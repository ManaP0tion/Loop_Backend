package com.loop.loop_backend.Admin.controller;

import com.loop.loop_backend.Admin.service.AdminAccessLogService;
import com.loop.loop_backend.Artist.domain.Artist;
import com.loop.loop_backend.Artist.domain.Nationality;
import com.loop.loop_backend.Artist.dto.ArtistProductDto;
import com.loop.loop_backend.Artist.dto.ArtistProductRequest;
import com.loop.loop_backend.Artist.repository.ArtistRepository;
import com.loop.loop_backend.Storage.service.S3StorageService;
import com.loop.loop_backend.Report.domain.Report;
import com.loop.loop_backend.Report.domain.ReportAction;
import com.loop.loop_backend.Report.domain.SanctionRecord;
import com.loop.loop_backend.Report.repository.ReportRepository;
import com.loop.loop_backend.Report.repository.SanctionRecordRepository;
import com.loop.loop_backend.User.domain.AuthProvider;
import com.loop.loop_backend.User.domain.Status;
import com.loop.loop_backend.User.domain.User;
import com.loop.loop_backend.User.repository.UserRepository;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

// AdminController 중 SanctionRecord 기록 부분(suspend/applyAction, applySuspension 헬퍼)과 아티스트 공식 링크 입력을 검증한다.
// AdminController 자체는 기존에 테스트가 없던 클래스라, 전체 커버리지 대신 이번에 변경한 조치 로직에 한정했다.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminControllerTest {

    @Mock UserRepository userRepository;
    @Mock ReportRepository reportRepository;
    @Mock SanctionRecordRepository sanctionRecordRepository;
    @Mock AdminAccessLogService accessLog;
    @Mock ArtistRepository artistRepository;
    @Mock S3StorageService s3StorageService;
    @InjectMocks AdminController adminController;

    private static final Long ADMIN_ID = 1L;
    private static final Long USER_ID = 10L;
    private static final Long REPORT_ID = 100L;

    private User testUser(long id) {
        User user = User.builder()
                .authProvider(AuthProvider.KAKAO)
                .providerId("kakao-" + id)
                .status(Status.ACTIVE)
                .onboardingCompleted(true)
                .build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Report testReport(User target) {
        Report report = Report.builder().reporter(testUser(2L)).targetUser(target).reason("SPAM").build();
        ReflectionTestUtils.setField(report, "id", REPORT_ID);
        return report;
    }

    // ── suspend (신고 없이 관리자가 직접 정지) ──────────────────────────

    @Test
    void 직접_정지시_유저_상태가_바뀌고_report_없는_SanctionRecord가_저장된다() {
        User user = testUser(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        adminController.suspend(ADMIN_ID, null, USER_ID, new AdminController.SuspendReq(7, "약관 위반"));

        assertThat(user.getStatus()).isEqualTo(Status.SUSPENDED);

        ArgumentCaptor<SanctionRecord> captor = ArgumentCaptor.forClass(SanctionRecord.class);
        verify(sanctionRecordRepository).save(captor.capture());
        SanctionRecord saved = captor.getValue();
        assertThat(saved.getTargetUser()).isEqualTo(user);
        assertThat(saved.getAdminNote()).isEqualTo("약관 위반");
        assertThat(saved.getReport()).isNull();
        assertThat(saved.getSuspendedUntil()).isNotNull();
    }

    @Test
    void days가_null이면_영구정지로_처리되고_SanctionRecord의_suspendedUntil도_null이다() {
        User user = testUser(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        adminController.suspend(ADMIN_ID, null, USER_ID, new AdminController.SuspendReq(null, "영구 정지 사유"));

        ArgumentCaptor<SanctionRecord> captor = ArgumentCaptor.forClass(SanctionRecord.class);
        verify(sanctionRecordRepository).save(captor.capture());
        assertThat(captor.getValue().getSuspendedUntil()).isNull();
    }

    @Test
    void 존재하지_않는_유저를_정지시키면_USER_NOT_FOUND_예외를_던지고_SanctionRecord도_안_남는다() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminController.suspend(
                ADMIN_ID, null, USER_ID, new AdminController.SuspendReq(7, "사유")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);

        verifyNoInteractions(sanctionRecordRepository);
    }

    // ── applyAction (신고 처리 결과로 정지) ─────────────────────────────

    @Test
    void SUSPEND_30D_조치시_30일_정지되고_해당_신고를_참조하는_SanctionRecord가_저장된다() {
        User target = testUser(USER_ID);
        Report report = testReport(target);
        when(reportRepository.findById(REPORT_ID)).thenReturn(Optional.of(report));

        adminController.applyAction(ADMIN_ID, null, REPORT_ID,
                new AdminController.ActionReq(ReportAction.SUSPEND_30D, "반복 위반"));

        assertThat(target.getStatus()).isEqualTo(Status.SUSPENDED);

        ArgumentCaptor<SanctionRecord> captor = ArgumentCaptor.forClass(SanctionRecord.class);
        verify(sanctionRecordRepository).save(captor.capture());
        SanctionRecord saved = captor.getValue();
        assertThat(saved.getReport()).isEqualTo(report);
        assertThat(saved.getTargetUser()).isEqualTo(target);
        assertThat(saved.getAdminNote()).isEqualTo("반복 위반");
        assertThat(saved.getSuspendedUntil()).isNotNull();
    }

    @Test
    void PERMANENT_조치시_영구정지되고_SanctionRecord의_suspendedUntil이_null이다() {
        User target = testUser(USER_ID);
        Report report = testReport(target);
        when(reportRepository.findById(REPORT_ID)).thenReturn(Optional.of(report));

        adminController.applyAction(ADMIN_ID, null, REPORT_ID,
                new AdminController.ActionReq(ReportAction.PERMANENT, "영구정지 사유"));

        assertThat(target.getStatus()).isEqualTo(Status.SUSPENDED);

        ArgumentCaptor<SanctionRecord> captor = ArgumentCaptor.forClass(SanctionRecord.class);
        verify(sanctionRecordRepository).save(captor.capture());
        assertThat(captor.getValue().getSuspendedUntil()).isNull();
    }

    @Test
    void NONE_조치시_유저_상태가_안_바뀌고_SanctionRecord도_저장되지_않는다() {
        User target = testUser(USER_ID);
        Report report = testReport(target);
        when(reportRepository.findById(REPORT_ID)).thenReturn(Optional.of(report));

        adminController.applyAction(ADMIN_ID, null, REPORT_ID,
                new AdminController.ActionReq(ReportAction.NONE, "조치 없음"));

        assertThat(target.getStatus()).isEqualTo(Status.ACTIVE);
        verifyNoInteractions(sanctionRecordRepository);
    }

    // ── 아티스트 공식 링크 (인스타·X·홈페이지) ──────────────────────────
    // 요구사항: 관리자가 아티스트 수정에서 직접 입력한다. http/https 주소만 받는다(아니면 400).
    // 수정은 PUT이라 링크도 통째로 교체되고, 비워 보내면 지워진다. 이미지만 올릴 때는 링크가 그대로다.

    private static final Long ARTIST_ID = 5L;

    private Artist givenArtist() {
        Artist artist = Artist.builder().name("Yuuri").nationality(Nationality.JP).build();
        ReflectionTestUtils.setField(artist, "id", ARTIST_ID);
        when(artistRepository.findById(ARTIST_ID)).thenReturn(Optional.of(artist));
        return artist;
    }

    private static AdminController.ArtistReq artistReq(String instagram, String x, String homepage) {
        return new AdminController.ArtistReq("Yuuri", null, "유우리", null, null, Nationality.JP,
                instagram, x, homepage);
    }

    @Test
    void 아티스트_수정으로_공식_링크를_등록한다() {
        givenArtist();

        AdminController.ArtistRow row = adminController.updateArtist(ARTIST_ID, artistReq(
                "https://www.instagram.com/yuuri_official", "https://x.com/yuuri_official", "https://yuuri.jp"))
                .getBody().getData();

        assertThat(row.instagramUrl()).isEqualTo("https://www.instagram.com/yuuri_official");
        assertThat(row.xUrl()).isEqualTo("https://x.com/yuuri_official");
        assertThat(row.homepageUrl()).isEqualTo("https://yuuri.jp");
    }

    @Test
    void 공식_링크를_비워_보내면_지워진다() {
        Artist artist = givenArtist();
        artist.changeLinks("https://www.instagram.com/yuuri_official", "https://x.com/yuuri_official", "https://yuuri.jp");

        AdminController.ArtistRow row = adminController.updateArtist(ARTIST_ID, artistReq(null, "", "  "))
                .getBody().getData();

        assertThat(row.instagramUrl()).isNull();
        assertThat(row.xUrl()).isNull();
        assertThat(row.homepageUrl()).isNull();
    }

    @Test
    void 공식_링크는_http_https_주소만_받는다() {
        givenArtist();

        for (String invalid : List.of("javascript:alert(1)", "instagram.com/yuuri_official", "@yuuri_official")) {
            assertThatThrownBy(() -> adminController.updateArtist(ARTIST_ID, artistReq(invalid, null, null)))
                    .as(invalid)
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void 공식_링크가_500자를_넘으면_DB_오류가_아니라_입력_오류다() {
        givenArtist();
        String tooLong = "https://yuuri.jp/" + "a".repeat(500);

        assertThatThrownBy(() -> adminController.updateArtist(ARTIST_ID, artistReq(null, null, tooLong)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 아티스트_이미지만_올리면_공식_링크는_그대로다() {
        Artist artist = givenArtist();
        artist.changeLinks("https://www.instagram.com/yuuri_official", null, "https://yuuri.jp");
        MockMultipartFile image = new MockMultipartFile("image", "a.png", "image/png", new byte[]{1});
        when(s3StorageService.uploadPublic("artists", ARTIST_ID, image)).thenReturn("https://cdn/artists/5/a.png");

        adminController.uploadArtistImage(ARTIST_ID, image);

        assertThat(artist.getImageUrl()).isEqualTo("https://cdn/artists/5/a.png");
        assertThat(artist.getInstagramUrl()).isEqualTo("https://www.instagram.com/yuuri_official");
        assertThat(artist.getHomepageUrl()).isEqualTo("https://yuuri.jp");
    }

    // ---------- 관련 상품 ----------
    // 새 상품은 CDJapan HTML 전문으로 하나씩 추가(맨 뒤). 수정·삭제·순서 변경은 조회로 받은 목록을 고쳐 통째로 저장한다.

    private static final String CDJAPAN_HTML = "<div><div><a href=\"https://www.cdjapan.co.jp/aff/click.cgi/e86NDzbdSLQ/7784/A103176/product%2FPCXP-51237\" target=\"_blank\">"
            + "<img src=\"https://www.cdjapan.co.jp/image.html?id=PCXP-51237\" alt=\"Official HIGE DANdism Arena Tour 2024 - Rejoice - / Official HIGE DANdism\" border=0 width=\"100\" /></a></div>"
            + "<div><a href=\"https://www.cdjapan.co.jp/aff/click.cgi/e86NDzbdSLQ/7784/A103176/product%2FPCXP-51237\" target=\"_blank\">Official HIGE DANdism Arena Tour 2024 - Rejoice -</a></div>"
            + "<div>Official HIGE DANdism</div></div>";

    private static final ArtistProductDto REJOICE = new ArtistProductDto("Official HIGE DANdism Arena Tour 2024 - Rejoice -",
            "https://www.cdjapan.co.jp/aff/click.cgi/e86NDzbdSLQ/7784/A103176/product%2FPCXP-51237",
            "https://www.cdjapan.co.jp/image.html?id=PCXP-51237");
    private static final ArtistProductDto BLU_RAY = new ArtistProductDto("Live Blu-ray",
            "https://www.cdjapan.co.jp/aff/1", "https://www.cdjapan.co.jp/image.html?id=1");

    @Test
    void HTML로_추가한_상품은_맨_뒤에_붙고_전체_목록을_돌려준다() {
        givenArtist();
        adminController.replaceArtistProducts(ARTIST_ID, List.of(BLU_RAY));

        List<ArtistProductDto> saved = adminController.addArtistProduct(ARTIST_ID,
                new ArtistProductRequest(CDJAPAN_HTML)).getBody().getData();

        assertThat(saved).containsExactly(BLU_RAY, REJOICE);
        assertThat(adminController.getArtistProducts(ARTIST_ID).getBody().getData()).isEqualTo(saved);
    }

    @Test
    void 목록_저장으로_상품명을_고치고_순서를_바꾸고_뺀_상품은_지운다() {
        givenArtist();
        adminController.addArtistProduct(ARTIST_ID, new ArtistProductRequest(CDJAPAN_HTML));
        adminController.replaceArtistProducts(ARTIST_ID, List.of(REJOICE, BLU_RAY,
                new ArtistProductDto("지울 상품", "https://www.cdjapan.co.jp/aff/9", "https://www.cdjapan.co.jp/image.html?id=9")));

        ArtistProductDto renamed = new ArtistProductDto("Rejoice (통상반)", REJOICE.linkUrl(), REJOICE.imageUrl());
        List<ArtistProductDto> saved = adminController.replaceArtistProducts(ARTIST_ID, List.of(BLU_RAY, renamed))
                .getBody().getData();

        assertThat(saved).containsExactly(BLU_RAY, renamed);
    }

    @Test
    void 목록을_빈_목록으로_저장하면_모두_비운다() {
        givenArtist();
        adminController.addArtistProduct(ARTIST_ID, new ArtistProductRequest(CDJAPAN_HTML));

        adminController.replaceArtistProducts(ARTIST_ID, List.of());

        assertThat(adminController.getArtistProducts(ARTIST_ID).getBody().getData()).isEmpty();
    }

    @Test
    void 잘못된_HTML_추가나_잘못된_목록_저장은_400이고_기존_상품은_그대로다() {
        Artist artist = givenArtist();
        adminController.addArtistProduct(ARTIST_ID, new ArtistProductRequest(CDJAPAN_HTML));

        assertThatThrownBy(() -> adminController.addArtistProduct(ARTIST_ID, new ArtistProductRequest("ベテルギウス")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> adminController.replaceArtistProducts(ARTIST_ID, List.of(BLU_RAY,
                new ArtistProductDto("링크 없음", null, "https://www.cdjapan.co.jp/image.html?id=1"))))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(ArtistProductDto.listOf(artist)).containsExactly(REJOICE);
    }

    @Test
    void 없는_아티스트의_상품은_404() {
        when(artistRepository.findById(999L)).thenReturn(Optional.empty());

        for (Runnable call : List.<Runnable>of(
                () -> adminController.getArtistProducts(999L),
                () -> adminController.addArtistProduct(999L, new ArtistProductRequest(CDJAPAN_HTML)),
                () -> adminController.replaceArtistProducts(999L, List.of()))) {
            assertThatThrownBy(call::run)
                    .isInstanceOfSatisfying(BusinessException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ARTIST_NOT_FOUND));
        }
    }

    @Test
    void 공식_링크_CSV는_빈_칸을_유지하고_못_찾은_행과_잘못된_링크_행을_알려준다() throws Exception {
        Artist artist = givenArtist();
        artist.changeLinks(null, "https://x.com/old", "https://yuuri.jp");
        Artist ado = Artist.builder().name("Ado").nationality(Nationality.JP).build();
        when(artistRepository.findByName("Ado")).thenReturn(Optional.of(ado));
        String csv = "\uFEFFid,name,instagramUrl,xUrl,homepageUrl\n"
                + "5,Yuuri,https://www.instagram.com/yuuri_official,,\n"   // 1: 반영, 빈 칸 유지
                + ",Ado,,https://x.com/ado1024,\n"                            // 2: name 매칭
                + "999,,https://a.com,,\n"                                    // 3: 없는 id
                + "5,,https://ok.com,javascript:alert(1),\n"                  // 4: 잘못된 링크 → 행 전체 건너뜀
                + "5,,,,\n";                                                  // 5: 유지
        MockMultipartFile file = new MockMultipartFile("file", "links.csv", "text/csv",
                csv.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        AdminController.ArtistLinkCsvResult r = adminController.importArtistLinksCsv(file).getBody().getData();

        assertThat(r.updated()).isEqualTo(2);
        assertThat(r.unchanged()).isEqualTo(1);
        assertThat(r.failedRows()).containsExactly(3L, 4L);
        assertThat(artist.getInstagramUrl()).isEqualTo("https://www.instagram.com/yuuri_official");
        assertThat(artist.getXUrl()).isEqualTo("https://x.com/old");
        assertThat(artist.getHomepageUrl()).isEqualTo("https://yuuri.jp");
        assertThat(ado.getXUrl()).isEqualTo("https://x.com/ado1024");
    }

    @Test
    void 공식_링크_CSV_다운로드는_업로드와_같은_헤더로_내보낸다() throws Exception {
        Artist artist = Artist.builder().name("Yuuri, Jr").nationality(Nationality.JP).build();
        ReflectionTestUtils.setField(artist, "id", ARTIST_ID);
        artist.changeLinks("https://www.instagram.com/yuuri_official", null, "https://yuuri.jp");
        when(artistRepository.findAll(any(org.springframework.data.domain.Sort.class))).thenReturn(List.of(artist));

        String csv = new String(adminController.exportArtistLinksCsv().getBody(), java.nio.charset.StandardCharsets.UTF_8);

        assertThat(csv).isEqualTo("\uFEFFid,name,baseName,nameKo,nameAlias,imageUrl,nationality,instagramUrl,xUrl,homepageUrl\r\n"
                + "5,\"Yuuri, Jr\",,,,,JP,https://www.instagram.com/yuuri_official,,https://yuuri.jp\r\n");
    }

    @Test
    void 아티스트_CSV는_수정_폼_전체_항목을_반영하고_잘못된_값_행은_건너뛴다() throws Exception {
        Artist artist = givenArtist();
        artist.update("Yuuri", null, "유우리", null, "https://old.com/a.jpg", Nationality.JP);
        String csv = "id,name,baseName,nameKo,nameAlias,imageUrl,nationality,instagramUrl,xUrl,homepageUrl\n"
                + "5,Yuuri2,,,ゆうり,https://img.com/y.jpg,KR,,,\n"               // 1: id 매칭이라 이름도 변경
                + "5,,,,,,NOPE,,,\n"                                             // 2: 없는 국적
                + "5,,,,,ftp://img.com/y.jpg,,,,\n"                              // 3: http/https 아닌 이미지
                + "5,," + "a".repeat(101) + ",,,,,,,\n"                          // 4: baseName 길이 초과
                + "5,Yuuri2,,,,,,,,\n";                                          // 5: 같은 값이면 유지
        MockMultipartFile file = new MockMultipartFile("file", "artists.csv", "text/csv",
                csv.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        AdminController.ArtistLinkCsvResult r = adminController.importArtistLinksCsv(file).getBody().getData();

        assertThat(r.updated()).isEqualTo(1);
        assertThat(r.unchanged()).isEqualTo(1);
        assertThat(r.failedRows()).containsExactly(2L, 3L, 4L);
        assertThat(artist.getName()).isEqualTo("Yuuri2");
        assertThat(artist.getNameKo()).isEqualTo("유우리");
        assertThat(artist.getNameAlias()).isEqualTo("ゆうり");
        assertThat(artist.getImageUrl()).isEqualTo("https://img.com/y.jpg");
        assertThat(artist.getNationality()).isEqualTo(Nationality.KR);
        assertThat(artist.getBaseName()).isNull();
    }
}