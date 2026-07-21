package com.loop.loop_backend.Storage.service;

import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class S3StorageService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024; // 5MB

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${aws.region}")
    private String region;

    @Value("${aws.s3.public-bucket}")
    private String publicBucket;

    @Value("${aws.s3.private-bucket}")
    private String privateBucket;

    // 공개 버킷에 업로드 후 바로 접근 가능한 public URL을 반환 (프로필/콘서트 이미지)
    public String uploadPublic(String prefix, Long ownerId, MultipartFile file) {
        String key = upload(publicBucket, prefix, ownerId, file);
        return "https://%s.s3.%s.amazonaws.com/%s".formatted(publicBucket, region, key);
    }

    // 비공개 버킷에 업로드 후 원본 key를 반환 (신고 증빙 이미지 - 조회 시 별도 presigned URL 필요)
    public String uploadPrivate(String prefix, Long ownerId, MultipartFile file) {
        return upload(privateBucket, prefix, ownerId, file);
    }

    // 비공개 버킷 객체를 조회할 때 쓰는 임시 GET URL 발급
    public String generatePresignedGetUrl(String key, Duration expiration) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(privateBucket)
                .key(key)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(expiration)
                .getObjectRequest(getObjectRequest)
                .build();

        return s3Presigner.presignGetObject(presignRequest).url().toString();
    }

    private String upload(String bucket, String prefix, Long ownerId, MultipartFile file) {
        String extension = validateAndExtractExtension(file);
        String key = "%s/%d/%s.%s".formatted(prefix, ownerId, UUID.randomUUID(), extension);

        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType(file.getContentType())
                            .build(),
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        } catch (IOException | S3Exception e) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED);
        }

        return key;
    }

    private String validateAndExtractExtension(MultipartFile file) {
        if (file.isEmpty()) {
            throw new BusinessException(ErrorCode.EMPTY_FILE);
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
        }
        if (!ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
            throw new BusinessException(ErrorCode.INVALID_FILE_EXTENSION);
        }

        String filename = file.getOriginalFilename();
        String extension = (filename != null && filename.contains("."))
                ? filename.substring(filename.lastIndexOf('.') + 1).toLowerCase()
                : "";

        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException(ErrorCode.INVALID_FILE_EXTENSION);
        }

        return extension;
    }
}