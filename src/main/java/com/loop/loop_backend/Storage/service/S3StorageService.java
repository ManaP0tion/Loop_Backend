package com.loop.loop_backend.Storage.service;

import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class S3StorageService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final Set<String> HEIC_CONTENT_TYPES = Set.of("image/heic", "image/heif");
    private static final Set<String> HEIC_EXTENSIONS = Set.of("heic", "heif");
    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024; // 5MB
    private static final Duration FFMPEG_TIMEOUT = Duration.ofSeconds(30);

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
        if (file.isEmpty()) {
            throw new BusinessException(ErrorCode.EMPTY_FILE);
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
        }

        byte[] bytes;
        String contentType;
        String extension;
        try {
            if (isHeic(file)) {
                // 대부분의 브라우저/OS가 heic/heif를 렌더링 못 해서, 저장 전에 jpg로 미리 변환해둔다.
                bytes = convertHeicToJpeg(file.getBytes());
                contentType = "image/jpeg";
                extension = "jpg";
            } else {
                bytes = file.getBytes();
                contentType = file.getContentType();
                extension = extractExtension(file.getOriginalFilename());
            }
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED);
        }

        validateFormat(contentType, extension);

        String key = "%s/%d/%s.%s".formatted(prefix, ownerId, UUID.randomUUID(), extension);
        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType(contentType)
                            .build(),
                    RequestBody.fromBytes(bytes));
        } catch (S3Exception e) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED);
        }

        return key;
    }

    private boolean isHeic(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType != null && HEIC_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            return true;
        }
        return HEIC_EXTENSIONS.contains(extractExtension(file.getOriginalFilename()));
    }

    private String extractExtension(String filename) {
        return (filename != null && filename.contains("."))
                ? filename.substring(filename.lastIndexOf('.') + 1).toLowerCase()
                : "";
    }

    private void validateFormat(String contentType, String extension) {
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new BusinessException(ErrorCode.INVALID_FILE_EXTENSION);
        }
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException(ErrorCode.INVALID_FILE_EXTENSION);
        }
    }

    // ffmpeg는 HEIC 컨테이너 안의 내장 썸네일을 원본으로 착각해서 뽑거나 EXIF 회전 정보를 무시하는 경우가 있어,
    // libheif 공식 변환 도구(heif-convert)를 사용한다 — primary 이미지를 정확히 고르고 회전도 반영해준다.
    private byte[] convertHeicToJpeg(byte[] heicBytes) throws IOException {
        Path inputPath = Files.createTempFile("upload-", ".heic");
        Path outputPath = Files.createTempFile("upload-", ".jpg");
        try {
            Files.write(inputPath, heicBytes);

            Process process = new ProcessBuilder(
                    "heif-convert", inputPath.toString(), outputPath.toString())
                    .redirectErrorStream(true)
                    .start();

            boolean finished = process.waitFor(FFMPEG_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.error("heic 변환 타임아웃");
                throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED);
            }
            if (process.exitValue() != 0) {
                log.error("heic 변환 실패 (heif-convert exit={})", process.exitValue());
                throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED);
            }

            return Files.readAllBytes(outputPath);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED);
        } finally {
            Files.deleteIfExists(inputPath);
            Files.deleteIfExists(outputPath);
        }
    }
}