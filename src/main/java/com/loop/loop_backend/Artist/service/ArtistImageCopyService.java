package com.loop.loop_backend.Artist.service;

import com.loop.loop_backend.Artist.domain.Artist;
import com.loop.loop_backend.Artist.repository.ArtistRepository;
import com.loop.loop_backend.Storage.service.S3StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 아티스트 imageUrl 중 외부 주소(CSV·수기 입력)를 우리 S3 공개 버킷으로 복사하고 imageUrl을 S3 주소로 바꾼다.
 * 외부 사이트가 이미지를 지우거나 핫링크를 막으면 앱에서 깨지기 때문.
 * 관리자가 버튼으로 실행하고, 몇 분 걸릴 수 있어 KOPIS 싱크처럼 백그라운드로 돈다. 여러 번 실행해도 결과는 같다(이미 S3인 건 건너뜀).
 *
 * 서버가 관리자가 넣은 임의 URL로 요청을 보내므로 SSRF를 막는다:
 * http/https만, 내부망·루프백·링크로컬(EC2 메타데이터 169.254.169.254 포함) 주소 차단, 리다이렉트는 매 hop 다시 검사, 5MB·10초 제한.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ArtistImageCopyService {

    private static final int MAX_BYTES = 5 * 1024 * 1024;
    private static final int MAX_REDIRECTS = 3;
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final ArtistRepository artistRepository;
    private final S3StorageService s3StorageService;

    // 리다이렉트는 직접 따라가며 목적지도 검사한다(자동으로 따라가면 외부 → 내부망 리다이렉트를 못 막음)
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile Result lastResult;

    public record Failure(Long artistId, String name, String imageUrl, String reason) {}

    /** finishedAt == null 이면 아직 실행 중 */
    public record Result(LocalDateTime startedAt, LocalDateTime finishedAt, int copied, int skipped, List<Failure> failures) {}

    public boolean isRunning() {
        return running.get();
    }

    public Result getLastResult() {
        return lastResult;
    }

    // 다른 빈(컨트롤러)이 프록시를 통해 불러야 @Async가 적용된다.
    @Async("imageCopyExecutor")
    public void copyAllAsync() {
        if (!running.compareAndSet(false, true)) return;
        LocalDateTime startedAt = LocalDateTime.now();
        int copied = 0, skipped = 0;
        List<Failure> failures = new ArrayList<>();
        lastResult = new Result(startedAt, null, 0, 0, List.of());
        try {
            for (Artist a : artistRepository.findAll()) {
                String url = a.getImageUrl();
                if (url == null || url.isBlank() || s3StorageService.isPublicUrl(url)) continue;
                try {
                    Image img = download(url);
                    String s3Url = s3StorageService.uploadPublicBytes("artists", a.getId(), img.bytes(), img.contentType(), img.extension());
                    if (artistRepository.replaceImageUrl(a.getId(), url, s3Url) == 1) copied++;
                    else skipped++; // 복사하는 사이 관리자가 이미지를 바꿈
                } catch (Exception e) {
                    failures.add(new Failure(a.getId(), a.getName(), url,
                            e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
                }
            }
            log.info("Artist image copy completed: copied={}, skipped={}, failed={}", copied, skipped, failures.size());
        } finally {
            lastResult = new Result(startedAt, LocalDateTime.now(), copied, skipped, failures);
            running.set(false);
        }
    }

    record Image(byte[] bytes, String contentType, String extension) {}

    private Image download(String url) throws IOException, InterruptedException {
        URI uri = URI.create(url);
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            checkPublicHost(uri);
            HttpRequest req = HttpRequest.newBuilder(uri).timeout(TIMEOUT)
                    .header("User-Agent", "Mozilla/5.0 (compatible; LoopImageCopy/1.0)")
                    .GET().build();
            HttpResponse<InputStream> res = httpClient.send(req, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = res.body()) {
                int status = res.statusCode();
                if (status >= 300 && status < 400) {
                    String location = res.headers().firstValue("Location")
                            .orElseThrow(() -> new IOException("리다이렉트 주소 없음"));
                    uri = uri.resolve(location);
                    continue;
                }
                if (status != 200) throw new IOException("HTTP " + status);
                byte[] bytes = body.readNBytes(MAX_BYTES + 1);
                if (bytes.length > MAX_BYTES) throw new IOException("5MB 초과");
                return detectImage(bytes);
            }
        }
        throw new IOException("리다이렉트가 너무 많음");
    }

    // ponytail: 검사 후 HttpClient가 DNS를 다시 조회하므로 DNS rebinding까지는 못 막는다. 관리자 전용 기능이라 감수.
    // 막아야 하면 검사한 IP로 직접 접속하는 클라이언트로 바꾼다.
    static void checkPublicHost(URI uri) throws IOException {
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme) || uri.getHost() == null) {
            throw new IOException("http/https 주소가 아님");
        }
        InetAddress[] addrs;
        try {
            addrs = InetAddress.getAllByName(uri.getHost());
        } catch (UnknownHostException e) {
            throw new IOException("존재하지 않는 도메인");
        }
        for (InetAddress addr : addrs) {
            if (isInternal(addr)) throw new IOException("내부망 주소는 허용하지 않음");
        }
    }

    static boolean isInternal(InetAddress addr) {
        if (addr.isAnyLocalAddress() || addr.isLoopbackAddress() || addr.isLinkLocalAddress()
                || addr.isSiteLocalAddress() || addr.isMulticastAddress()) return true;
        byte[] b = addr.getAddress();
        if (addr instanceof Inet6Address) return (b[0] & 0xFE) == 0xFC; // fc00::/7 (IPv6 사설)
        return (b[0] & 0xFF) == 100 && (b[1] & 0xC0) == 64;             // 100.64.0.0/10 (CGNAT)
    }

    // 응답 헤더 Content-Type은 믿지 않고 파일 앞부분(매직 넘버)으로 판별. jpg/png/webp만(업로드 허용 형식과 동일, SVG 차단)
    static Image detectImage(byte[] b) throws IOException {
        if (b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return new Image(b, "image/jpeg", "jpg");
        }
        if (b.length > 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
            return new Image(b, "image/png", "png");
        }
        if (b.length > 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return new Image(b, "image/webp", "webp");
        }
        throw new IOException("jpg/png/webp 이미지가 아님");
    }
}
