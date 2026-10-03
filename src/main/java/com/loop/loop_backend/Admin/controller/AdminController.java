package com.loop.loop_backend.Admin.controller;

import com.loop.loop_backend.Admin.domain.AdminAccessLog;
import com.loop.loop_backend.Admin.repository.AdminAccessLogRepository;
import com.loop.loop_backend.Admin.service.AdminAccessLogService;
import com.loop.loop_backend.Artist.domain.Artist;
import com.loop.loop_backend.Artist.repository.ArtistRepository;
import com.loop.loop_backend.Chat.domain.Message;
import com.loop.loop_backend.Chat.repository.ChatRoomRepository;
import com.loop.loop_backend.Chat.repository.MessageRepository;
import com.loop.loop_backend.CompanionPost.domain.CompanionPost;
import com.loop.loop_backend.CompanionPost.repository.CompanionPostRepository;
import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.Concert.domain.ConcertCategory;
import com.loop.loop_backend.ConcertImport.domain.ConcertImport;
import com.loop.loop_backend.ConcertImport.domain.ImportStatus;
import com.loop.loop_backend.ConcertImport.kopis.KopisSyncService;
import com.loop.loop_backend.ConcertImport.repository.ConcertImportRepository;
import com.loop.loop_backend.Concert.repository.ConcertRepository;
import com.loop.loop_backend.ConcertImport.service.ConcertImportService;
import com.loop.loop_backend.Inquiry.domain.Inquiry;
import com.loop.loop_backend.Inquiry.domain.InquiryStatus;
import com.loop.loop_backend.Inquiry.domain.InquiryType;
import com.loop.loop_backend.Inquiry.repository.InquiryRepository;
import com.loop.loop_backend.Mail.domain.MailLog;
import com.loop.loop_backend.Mail.domain.MailType;
import com.loop.loop_backend.Mail.repository.MailLogRepository;
import com.loop.loop_backend.Mail.service.MailService;
import com.loop.loop_backend.Report.domain.AppealStatus;
import com.loop.loop_backend.Report.domain.Report;
import com.loop.loop_backend.Report.domain.ReportAction;
import com.loop.loop_backend.Report.domain.ReportStatus;
import com.loop.loop_backend.Report.domain.SanctionRecord;
import com.loop.loop_backend.Report.repository.ReportRepository;
import com.loop.loop_backend.Report.repository.SanctionRecordRepository;
import com.loop.loop_backend.Storage.service.S3StorageService;
import com.loop.loop_backend.User.domain.Gender;
import com.loop.loop_backend.User.domain.Role;
import com.loop.loop_backend.User.domain.Status;
import com.loop.loop_backend.User.domain.User;
import com.loop.loop_backend.User.repository.UserRepository;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.CommonResponse;
import com.loop.loop_backend.common.exception.ErrorCode;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 관리자 콘솔 API. 모든 엔드포인트는 SecurityConfig 에서 hasRole(ADMIN) 로 잠겨 있음.
 * 개인정보(회원/신고/채팅) 접근은 AdminAccessLog 로 감사 기록을 남긴다.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Tag(name = "Admin", description = "관리자 콘솔 API (ADMIN 전용)")
public class AdminController {

    private final UserRepository userRepository;
    private final ReportRepository reportRepository;
    // 사용자가 "내 제재 이력"(GET /api/users/me/reports/sanctions)에서 조회할 수 있도록,
    // 정지 조치를 적용할 때마다(신고 처리든 아래 직접 정지든) 이곳에도 기록을 남긴다.
    private final SanctionRecordRepository sanctionRecordRepository;
    private final InquiryRepository inquiryRepository;
    private final ArtistRepository artistRepository;
    private final ConcertRepository concertRepository;
    private final CompanionPostRepository companionPostRepository;
    private final MessageRepository messageRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final MailLogRepository mailLogRepository;
    private final MailService mailService;
    private final AdminAccessLogRepository accessLogRepository;
    private final AdminAccessLogService accessLog;
    private final S3StorageService s3StorageService;
    private final KopisSyncService kopisSyncService;
    private final ConcertImportRepository concertImportRepository;
    private final ConcertImportService concertImportService;

    // ================= DASHBOARD =================

    /** 관리자 진입 시 한눈에 보는 요약. 처리 대기 큐(신고·이의제기) + 서비스 KPI. 모두 카운트 쿼리라 캐시 불필요. */
    @GetMapping("/dashboard")
    public ResponseEntity<CommonResponse<DashboardStats>> dashboard() {
        LocalDateTime weekAgo = LocalDateTime.now().minusDays(7);
        DashboardStats stats = new DashboardStats(
                reportRepository.countByStatusIn(List.of(ReportStatus.RECEIVED, ReportStatus.PROCESSING)),
                reportRepository.countByAppealStatus(AppealStatus.RAISED),
                userRepository.count(),
                userRepository.countByCreatedAtAfter(weekAgo),
                companionPostRepository.count(),
                companionPostRepository.countByCreatedAtAfter(weekAgo),
                inquiryRepository.countPending(),
                inquiryRepository.countByCreatedAtAfter(weekAgo),
                chatRoomRepository.count(),
                chatRoomRepository.countActiveRooms());
        return ResponseEntity.ok(CommonResponse.success(stats));
    }

    // ================= USERS =================

    @GetMapping("/users")
    public ResponseEntity<CommonResponse<PageResp<UserRow>>> listUsers(
            @RequestParam(required = false) Status status,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<User> p = userRepository.searchForAdmin(status, role, q, PageRequest.of(page, size));
        return ResponseEntity.ok(CommonResponse.success(PageResp.from(p.map(UserRow::of))));
    }

    @GetMapping("/users/{id}")
    @Transactional
    public ResponseEntity<CommonResponse<UserRow>> getUser(
            @AuthenticationPrincipal Long adminId, HttpServletRequest req, @PathVariable Long id) {
        User u = userRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        accessLog.log(adminId, req, "VIEW_USER", "USER", id, "관리자 회원 개인정보 조회");
        return ResponseEntity.ok(CommonResponse.success(UserRow.of(u)));
    }

    /** 약관 제6조 3항: 고객센터 요청 시 성별/생년월일 수정. */
    @PatchMapping("/users/{id}")
    @Transactional
    public ResponseEntity<CommonResponse<UserRow>> updateUser(
            @AuthenticationPrincipal Long adminId, HttpServletRequest req,
            @PathVariable Long id, @RequestBody UserPatch body) {
        User u = userRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        u.updateBirthAndGender(body.birthDate(), body.gender());
        accessLog.log(adminId, req, "UPDATE_USER", "USER", id,
                "birthDate/gender 수정 요청: " + body);
        return ResponseEntity.ok(CommonResponse.success(UserRow.of(u)));
    }

    @PostMapping("/users/{id}/suspend")
    @Transactional
    public ResponseEntity<CommonResponse<UserRow>> suspend(
            @AuthenticationPrincipal Long adminId, HttpServletRequest req,
            @PathVariable Long id, @RequestBody SuspendReq body) {
        User u = userRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Integer days = body.days();
        LocalDateTime until = (days == null) ? null : LocalDateTime.now().plusDays(days);
        // 신고 없이 관리자가 직접 정지시키는 경로라 report는 null로 남긴다 (applySuspension 참고)
        applySuspension(u, until, body.reason(), null);
        accessLog.log(adminId, req, "SUSPEND_USER", "USER", id,
                "정지 " + (days == null ? "영구" : days + "일") + " · 사유: " + body.reason());
        return ResponseEntity.ok(CommonResponse.success(UserRow.of(u)));
    }

    // 정지 처리(User 상태 변경)와 "내 제재 이력" 기록(SanctionRecord)을 한 번에 처리하는 헬퍼.
    // report가 null이면 신고를 거치지 않고 관리자가 직접 정지시킨 경우, non-null이면 그 신고 처리로 인한 정지.
    private void applySuspension(User target, LocalDateTime until, String adminNote, Report report) {
        target.suspend(until);
        sanctionRecordRepository.save(SanctionRecord.builder()
                .targetUser(target)
                .suspendedUntil(until)
                .adminNote(adminNote)
                .report(report)
                .build());
    }

    @PostMapping("/users/{id}/lift")
    @Transactional
    public ResponseEntity<CommonResponse<UserRow>> lift(
            @AuthenticationPrincipal Long adminId, HttpServletRequest req, @PathVariable Long id) {
        User u = userRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        u.liftSuspension();
        accessLog.log(adminId, req, "LIFT_USER", "USER", id, "정지 해제");
        return ResponseEntity.ok(CommonResponse.success(UserRow.of(u)));
    }

    @PostMapping("/users/{id}/terminate")
    @Transactional
    public ResponseEntity<CommonResponse<UserRow>> terminate(
            @AuthenticationPrincipal Long adminId, HttpServletRequest req, @PathVariable Long id) {
        User u = userRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        u.terminate();
        accessLog.log(adminId, req, "TERMINATE_USER", "USER", id, "계정 해지");
        return ResponseEntity.ok(CommonResponse.success(UserRow.of(u)));
    }

    // ================= REPORTS =================

    @GetMapping("/reports")
    @Transactional(readOnly = true)
    public ResponseEntity<CommonResponse<PageResp<ReportRow>>> listReports(
            @RequestParam(required = false) ReportStatus status,
            @RequestParam(required = false) AppealStatus appeal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<Report> p = reportRepository.searchForAdmin(status, appeal, PageRequest.of(page, size));
        return ResponseEntity.ok(CommonResponse.success(PageResp.from(p.map(ReportRow::of))));
    }

    @GetMapping("/reports/{id}")
    @Transactional
    public ResponseEntity<CommonResponse<ReportRow>> getReport(
            @AuthenticationPrincipal Long adminId, HttpServletRequest req, @PathVariable Long id) {
        Report r = reportRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.REPORT_NOT_FOUND));
        List<String> imageUrls = r.getImages().stream()
                .map(img -> s3StorageService.generatePresignedGetUrl(img.getImageUrl(), java.time.Duration.ofMinutes(15)))
                .toList();
        accessLog.log(adminId, req, "VIEW_REPORT", "REPORT", id, "신고 상세 조회");
        return ResponseEntity.ok(CommonResponse.success(ReportRow.of(r, imageUrls)));
    }

    @PatchMapping("/reports/{id}/status")
    @Transactional
    public ResponseEntity<CommonResponse<ReportRow>> updateReportStatus(
            @PathVariable Long id, @RequestBody Map<String, String> body) {
        Report r = reportRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.REPORT_NOT_FOUND));
        r.updateStatus(ReportStatus.valueOf(body.get("status")));
        return ResponseEntity.ok(CommonResponse.success(ReportRow.of(r)));
    }

    @PostMapping("/reports/{id}/action")
    @Transactional
    public ResponseEntity<CommonResponse<ReportRow>> applyAction(
            @AuthenticationPrincipal Long adminId, HttpServletRequest req,
            @PathVariable Long id, @RequestBody ActionReq body) {
        Report r = reportRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.REPORT_NOT_FOUND));
        r.applyAction(body.action(), body.note());

        // 피신고자에 실제 조치 반영 (정지된 경우 SanctionRecord에도 기록 - applySuspension 참고)
        User target = r.getTargetUser();
        switch (body.action()) {
            case SUSPEND_30D -> applySuspension(target, LocalDateTime.now().plusDays(30), body.note(), r);
            case PERMANENT -> applySuspension(target, null, body.note(), r);
            case NONE -> { /* no user side effect */ }
        }
        accessLog.log(adminId, req, "REPORT_ACTION", "REPORT", id,
                "조치 " + body.action() + " · 대상 userId=" + target.getId());
        return ResponseEntity.ok(CommonResponse.success(ReportRow.of(r)));
    }

    @PostMapping("/reports/{id}/close")
    @Transactional
    public ResponseEntity<CommonResponse<ReportRow>> closeReport(
            @PathVariable Long id, @RequestBody Map<String, String> body) {
        Report r = reportRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.REPORT_NOT_FOUND));
        r.closeWithoutAction(body.getOrDefault("note", ""));
        return ResponseEntity.ok(CommonResponse.success(ReportRow.of(r)));
    }

    /** 신고 건에 대한 채팅 열람 (약관 제11조 5·8항 · 처리방침 조치 시 회원 고지 근거). */
    @GetMapping("/reports/{id}/chat")
    @Transactional
    public ResponseEntity<CommonResponse<List<MessageRow>>> viewReportChat(
            @AuthenticationPrincipal Long adminId, HttpServletRequest req, @PathVariable Long id) {
        Report r = reportRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.REPORT_NOT_FOUND));
        Long roomId = r.getChatRoomId();
        if (roomId == null) {
            accessLog.log(adminId, req, "VIEW_REPORT_CHAT", "REPORT", id, "채팅 없음");
            return ResponseEntity.ok(CommonResponse.success(List.of()));
        }
        var msgs = messageRepository
                .findByChatRoom_IdOrderByCreatedAtDesc(roomId, PageRequest.of(0, 200))
                .stream().map(MessageRow::of).toList();
        accessLog.log(adminId, req, "VIEW_REPORT_CHAT", "CHAT_ROOM", roomId,
                "신고 #" + id + " 관련 채팅 열람 (건수: " + msgs.size() + ")");
        return ResponseEntity.ok(CommonResponse.success(msgs));
    }

    @PostMapping("/reports/{id}/appeal/raise")
    @Transactional
    public ResponseEntity<CommonResponse<ReportRow>> raiseAppeal(@PathVariable Long id) {
        Report r = reportRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.REPORT_NOT_FOUND));
        r.raiseAppeal();
        return ResponseEntity.ok(CommonResponse.success(ReportRow.of(r)));
    }

    @PostMapping("/reports/{id}/appeal/resolve")
    @Transactional
    public ResponseEntity<CommonResponse<ReportRow>> resolveAppeal(
            @PathVariable Long id, @RequestBody AppealReq body) {
        Report r = reportRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.REPORT_NOT_FOUND));
        AppealStatus resolution = body.resolution();
        if (resolution != AppealStatus.UPHELD && resolution != AppealStatus.OVERTURNED) {
            throw new BusinessException(ErrorCode.APPEAL_INVALID_STATE);
        }
        r.resolveAppeal(resolution, body.note());
        // 조치해제 시 대상 정지 해제
        if (resolution == AppealStatus.OVERTURNED) {
            r.getTargetUser().liftSuspension();
        }
        return ResponseEntity.ok(CommonResponse.success(ReportRow.of(r)));
    }

    // ================= INQUIRIES =================

    @GetMapping("/inquiries")
    @Transactional(readOnly = true)
    public ResponseEntity<CommonResponse<PageResp<InquiryRow>>> listInquiries(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        Page<Inquiry> p = inquiryRepository.findAll(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return ResponseEntity.ok(CommonResponse.success(PageResp.from(p.map(InquiryRow::of))));
    }

    @GetMapping("/inquiries/{id}")
    @Transactional
    public ResponseEntity<CommonResponse<InquiryRow>> getInquiry(
            @AuthenticationPrincipal Long adminId, HttpServletRequest req, @PathVariable Long id) {
        Inquiry i = inquiryRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.INQUIRY_NOT_FOUND));
        accessLog.log(adminId, req, "VIEW_INQUIRY", "INQUIRY", id, "고객센터 문의 조회");
        return ResponseEntity.ok(CommonResponse.success(InquiryRow.of(i)));
    }

    @PatchMapping("/inquiries/{id}/status")
    @Transactional
    public ResponseEntity<CommonResponse<InquiryRow>> updateInquiryStatus(
            @PathVariable Long id, @RequestBody InquiryStatusReq body) {
        Inquiry i = inquiryRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.INQUIRY_NOT_FOUND));
        i.updateStatus(body.status());
        return ResponseEntity.ok(CommonResponse.success(InquiryRow.of(i)));
    }

    // ================= ARTISTS (Concert 있는 삭제도 cascade — 도메인 FK가 처리) =================

    @GetMapping("/artists")
    public ResponseEntity<CommonResponse<List<ArtistRow>>> listArtists() {
        return ResponseEntity.ok(CommonResponse.success(
                artistRepository.findAll().stream().map(ArtistRow::of).toList()));
    }

    @PostMapping("/artists")
    @Transactional
    public ResponseEntity<CommonResponse<ArtistRow>> createArtist(@RequestBody ArtistReq body) {
        Artist a = artistRepository.save(Artist.builder()
                .name(body.name())
                .baseName(body.baseName())
                .nameKo(body.nameKo())
                .nameAlias(body.nameAlias())
                .imageUrl(body.imageUrl())
                .category(body.category())
                .autoFetchConcerts(false)
                .build());
        return ResponseEntity.ok(CommonResponse.success(ArtistRow.of(a)));
    }

    @PutMapping("/artists/{id}")
    @Transactional
    public ResponseEntity<CommonResponse<ArtistRow>> updateArtist(@PathVariable Long id, @RequestBody ArtistReq body) {
        Artist a = artistRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.ARTIST_NOT_FOUND));
        a.update(body.name(), body.baseName(), body.nameKo(), body.nameAlias(), body.imageUrl(), body.category());
        return ResponseEntity.ok(CommonResponse.success(ArtistRow.of(a)));
    }

    @DeleteMapping("/artists/{id}")
    @Transactional
    public ResponseEntity<CommonResponse<Void>> deleteArtist(@PathVariable Long id) {
        if (!artistRepository.existsById(id)) throw new BusinessException(ErrorCode.ARTIST_NOT_FOUND);
        // Concert.artist ON DELETE CASCADE → 관련 콘서트 함께 삭제.
        artistRepository.deleteById(id);
        return ResponseEntity.ok(CommonResponse.success(null));
    }

    // S3 공개 버킷 업로드 후 아티스트의 imageUrl 갱신. 프론트는 JSON 저장 뒤 별도로 호출.
    @PostMapping(value = "/artists/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public ResponseEntity<CommonResponse<Map<String, String>>> uploadArtistImage(
            @PathVariable Long id, @RequestPart("image") MultipartFile image) {
        Artist a = artistRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.ARTIST_NOT_FOUND));
        String url = s3StorageService.uploadPublic("artists", id, image);
        a.update(a.getName(), a.getBaseName(), a.getNameKo(), a.getNameAlias(), url, a.getCategory());
        return ResponseEntity.ok(CommonResponse.success(Map.of("imageUrl", url)));
    }

    // ================= CONCERTS =================

    @GetMapping("/concerts")
    @Transactional(readOnly = true)
    public ResponseEntity<CommonResponse<PageResp<ConcertRow>>> listConcerts(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String q) {
        String query = (q == null || q.isBlank()) ? null : q.trim();
        Page<Concert> p = concertRepository.searchForAdmin(query, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")));
        return ResponseEntity.ok(CommonResponse.success(PageResp.from(p.map(ConcertRow::of))));
    }

    @PostMapping("/concerts")
    @Transactional
    public ResponseEntity<CommonResponse<ConcertRow>> createConcert(@RequestBody ConcertReq body) {
        Artist artist = body.artistId() == null ? null
                : artistRepository.findById(body.artistId())
                        .orElseThrow(() -> new BusinessException(ErrorCode.ARTIST_NOT_FOUND));
        ConcertCategory category = body.category() != null ? body.category()
                : (artist != null ? artist.getCategory() : null);
        if (category == null) throw new BusinessException(ErrorCode.INVALID_INPUT);
        Concert c = Concert.builder()
                .artist(artist)
                .title(body.title())
                .posterUrl(body.posterUrl())
                .venue(body.venue())
                .startDate(body.startDate())
                .endDate(body.endDate())
                .price(body.price())
                .ticketUrl(body.ticketUrl())
                .showtime(body.showtime())
                .category(category)
                .build();
        return ResponseEntity.ok(CommonResponse.success(ConcertRow.of(concertRepository.save(c))));
    }

    @PutMapping("/concerts/{id}")
    @Transactional
    public ResponseEntity<CommonResponse<ConcertRow>> updateConcert(@PathVariable Long id, @RequestBody ConcertReq body) {
        Concert c = concertRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.CONCERT_NOT_FOUND));
        Artist artist = body.artistId() == null ? null
                : artistRepository.findById(body.artistId())
                        .orElseThrow(() -> new BusinessException(ErrorCode.ARTIST_NOT_FOUND));
        ConcertCategory category = body.category() != null ? body.category()
                : (artist != null ? artist.getCategory() : null);
        if (category == null) throw new BusinessException(ErrorCode.INVALID_INPUT);
        c.update(artist, body.title(), body.posterUrl(), body.venue(),
                body.startDate(), body.endDate(), category,
                body.price(), body.ticketUrl(), body.showtime());
        return ResponseEntity.ok(CommonResponse.success(ConcertRow.of(c)));
    }

    // S3 공개 버킷 업로드 후 콘서트의 posterUrl 갱신. 프론트는 JSON 저장 뒤 별도로 호출.
    @PostMapping(value = "/concerts/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public ResponseEntity<CommonResponse<Map<String, String>>> uploadConcertPoster(
            @PathVariable Long id, @RequestPart("image") MultipartFile image) {
        Concert c = concertRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.CONCERT_NOT_FOUND));
        String url = s3StorageService.uploadPublic("concerts", id, image);
        c.updatePosterUrl(url);
        return ResponseEntity.ok(CommonResponse.success(Map.of("posterUrl", url)));
    }

    @DeleteMapping("/concerts/{id}")
    @Transactional
    public ResponseEntity<CommonResponse<Void>> deleteConcert(@PathVariable Long id) {
        if (!concertRepository.existsById(id)) throw new BusinessException(ErrorCode.CONCERT_NOT_FOUND);
        // CompanionPost.concert ON DELETE CASCADE → 동행 프로필 함께 삭제. 채팅방은 ChatRoom.post SET_NULL 로 보존.
        // ChatRoom.concertId는 post와 별개로 저장된 스냅샷이라 DB 캐스케이드가 안 닿으므로, 여기서 직접 정리해서
        // 삭제된 공연 id를 계속 들고 있는 유령 참조가 안 남게 한다.
        chatRoomRepository.clearConcertId(id);
        concertRepository.deleteById(id);
        return ResponseEntity.ok(CommonResponse.success(null));
    }

    @PostMapping("/concerts/sync")
    public ResponseEntity<CommonResponse<String>> syncKopis(
            @AuthenticationPrincipal Long adminId, HttpServletRequest req) {
        // 싱크가 몇 분 걸려 프록시 타임아웃이 나므로, 백그라운드로 시작만 하고 바로 응답한다.
        // 이미 돌고 있으면 새로 시작하지 않고 409로 알린다.
        if (kopisSyncService.isRunning()) {
            throw new BusinessException(ErrorCode.KOPIS_SYNC_ALREADY_RUNNING);
        }
        kopisSyncService.syncAllAsync();
        accessLog.log(adminId, req, "SYNC_KOPIS", "CONCERT", null, "KOPIS 공연 정보 수동 동기화 시작");
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(CommonResponse.success(
                "KOPIS 동기화를 시작했습니다. 끝나면 검토 대기 목록에 적재되며, 진행 상황은 서버 로그에서 확인하세요."));
    }

    // ================= CONCERT IMPORTS (KOPIS 수집 → 검토 대기) =================

    /** 검토 큐. status 미지정 시 전체. 기본 PENDING만 보고 싶으면 ?status=PENDING. */
    @GetMapping("/concert-imports")
    @Transactional(readOnly = true)
    public ResponseEntity<CommonResponse<PageResp<ImportRow>>> listImports(
            @RequestParam(required = false) ImportStatus status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        Page<ConcertImport> p = (status == null)
                ? concertImportRepository.findAll(pageable)
                : concertImportRepository.findByStatus(status, pageable);
        return ResponseEntity.ok(CommonResponse.success(PageResp.from(p.map(ImportRow::of))));
    }

    /** 승인 → Concert(운영 데이터) 생성. body의 non-null 필드만 수집 원본값을 덮어쓴다. */
    @PostMapping("/concert-imports/{id}/approve")
    @Transactional
    public ResponseEntity<CommonResponse<ConcertRow>> approveImport(
            @AuthenticationPrincipal Long adminId, HttpServletRequest req,
            @PathVariable Long id, @RequestBody(required = false) ConcertImportService.ApproveCommand body) {
        ConcertImportService.ApproveCommand cmd = (body != null) ? body
                : new ConcertImportService.ApproveCommand(null, null, null, null, null, null, null);
        Concert c = concertImportService.approve(id, cmd);
        accessLog.log(adminId, req, "APPROVE_IMPORT", "CONCERT", c.getId(),
                "수집 공연 #" + id + " 승인 → 콘서트 #" + c.getId() + " 생성");
        return ResponseEntity.ok(CommonResponse.success(ConcertRow.of(c)));
    }

    @PostMapping("/concert-imports/{id}/reject")
    @Transactional
    public ResponseEntity<CommonResponse<Void>> rejectImport(
            @AuthenticationPrincipal Long adminId, HttpServletRequest req,
            @PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.getOrDefault("reason", "") : "";
        concertImportService.reject(id, reason);
        accessLog.log(adminId, req, "REJECT_IMPORT", "CONCERT", id, "수집 공연 거절 · 사유: " + reason);
        return ResponseEntity.ok(CommonResponse.success(null));
    }

    // ================= COMPANION POSTS =================

    @GetMapping("/companion-posts")
    @Transactional(readOnly = true)
    public ResponseEntity<CommonResponse<PageResp<CompanionRow>>> listPosts(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        Page<CompanionPost> p = companionPostRepository.findAll(
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return ResponseEntity.ok(CommonResponse.success(PageResp.from(p.map(CompanionRow::of))));
    }

    @DeleteMapping("/companion-posts/{id}")
    @Transactional
    public ResponseEntity<CommonResponse<Void>> deletePost(
            @AuthenticationPrincipal Long adminId, HttpServletRequest req, @PathVariable Long id) {
        if (!companionPostRepository.existsById(id))
            throw new BusinessException(ErrorCode.COMPANION_POST_NOT_FOUND);
        companionPostRepository.deleteById(id);
        accessLog.log(adminId, req, "DELETE_POST", "COMPANION_POST", id, "약관 위반 삭제 (약관 제13조 3항)");
        return ResponseEntity.ok(CommonResponse.success(null));
    }

    // ================= ACCESS LOGS =================

    @GetMapping("/access-logs")
    public ResponseEntity<CommonResponse<PageResp<AccessLogRow>>> listAccessLogs(
            @RequestParam(required = false) Long adminId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<AdminAccessLog> p = accessLogRepository.search(
                adminId, action,
                from != null ? from.atStartOfDay() : null,
                to != null ? to.plusDays(1).atStartOfDay() : null,
                pageable);
        return ResponseEntity.ok(CommonResponse.success(PageResp.from(p.map(AccessLogRow::of))));
    }

    // ================= MAIL LOGS =================

    @GetMapping("/mail-logs")
    public ResponseEntity<CommonResponse<PageResp<MailLogRow>>> listMailLogs(
            @RequestParam(required = false) MailType type,
            @RequestParam(required = false) Boolean success,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<MailLog> p = mailLogRepository.searchForAdmin(type, success, pageable);
        return ResponseEntity.ok(CommonResponse.success(PageResp.from(p.map(MailLogRow::of))));
    }

    /** 테스트용: 선택한 타입의 샘플 메일을 입력한 주소로 발송. 발송 결과는 mail-logs 에 그대로 남는다. */
    @PostMapping("/mail-logs/test")
    public ResponseEntity<CommonResponse<Void>> sendTestMail(
            @AuthenticationPrincipal Long adminId, HttpServletRequest req, @RequestBody TestMailReq body) {
        if (body == null || body.type() == null || body.email() == null || body.email().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        mailService.sendTest(body.type(), body.email().trim());
        accessLog.log(adminId, req, "SEND_TEST_MAIL", "MAIL", null, body.type() + " → " + body.email().trim());
        return ResponseEntity.ok(CommonResponse.success(null));
    }

    public record TestMailReq(MailType type, String email) {}

    // ================= ADMIN ACCOUNTS =================

    @GetMapping("/admins")
    public ResponseEntity<CommonResponse<List<UserRow>>> listAdmins() {
        return ResponseEntity.ok(CommonResponse.success(
                userRepository.findByRole(Role.ADMIN).stream().map(UserRow::of).toList()));
    }

    @PostMapping("/admins/{userId}/grant")
    @Transactional
    public ResponseEntity<CommonResponse<UserRow>> grantAdmin(
            @AuthenticationPrincipal Long adminId, HttpServletRequest req, @PathVariable Long userId) {
        User u = userRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        u.changeRole(Role.ADMIN);
        accessLog.log(adminId, req, "GRANT_ADMIN", "USER", userId, "관리자 권한 부여");
        return ResponseEntity.ok(CommonResponse.success(UserRow.of(u)));
    }

    @PostMapping("/admins/{userId}/revoke")
    @Transactional
    public ResponseEntity<CommonResponse<UserRow>> revokeAdmin(
            @AuthenticationPrincipal Long adminId, HttpServletRequest req, @PathVariable Long userId) {
        User u = userRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        u.changeRole(Role.USER);
        accessLog.log(adminId, req, "REVOKE_ADMIN", "USER", userId, "관리자 권한 해제");
        return ResponseEntity.ok(CommonResponse.success(UserRow.of(u)));
    }

    // ================= DTOs (records) =================

    /** ① 처리 대기 큐(pendingReports·pendingAppeals) + ② 서비스 KPI */
    public record DashboardStats(
            long pendingReports, long pendingAppeals,
            long totalUsers, long newUsersThisWeek,
            long totalPosts, long newPostsThisWeek,
            long totalInquiries, long newInquiriesThisWeek,
            long totalChatRooms, long activeChatRooms) {}

    public record PageResp<T>(List<T> items, int page, int size, long totalElements, int totalPages) {
        static <T> PageResp<T> from(Page<T> p) {
            return new PageResp<>(p.getContent(), p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages());
        }
    }

    public record UserRow(Long id, String nickname, String email, String userId, Gender gender, LocalDate birthDate,
                          Status status, Role role, LocalDateTime suspendedUntil, LocalDateTime createdAt) {
        static UserRow of(User u) {
            return new UserRow(u.getId(), u.getNickname(), u.getEmail(), u.getUserId(),
                    u.getGender(), u.getBirthDate(), u.getStatus(), u.getRole(),
                    u.getSuspendedUntil(), u.getCreatedAt());
        }
    }

    public record UserPatch(LocalDate birthDate, Gender gender) {}
    public record SuspendReq(Integer days, String reason) {} // days=null → 영구

    public record ReportRow(Long id, Long reporterId, String reporterNickname,
                            Long targetId, String targetNickname,
                            String reason, String detail, ReportStatus status,
                            ReportAction action, AppealStatus appealStatus,
                            Long chatRoomId, String adminNote,
                            List<String> imageUrls,
                            LocalDateTime createdAt, LocalDateTime processedAt) {
        static ReportRow of(Report r) {
            return of(r, List.of());
        }
        static ReportRow of(Report r, List<String> imageUrls) {
            return new ReportRow(r.getId(),
                    r.getReporter().getId(), r.getReporter().getNickname(),
                    r.getTargetUser().getId(), r.getTargetUser().getNickname(),
                    r.getReason(), r.getDetail(), r.getStatus(),
                    r.getAction(), r.getAppealStatus(),
                    r.getChatRoomId(), r.getAdminNote(),
                    imageUrls,
                    r.getCreatedAt(), r.getProcessedAt());
        }
    }

    public record ActionReq(ReportAction action, String note) {}
    public record AppealReq(AppealStatus resolution, String note) {}

    public record MessageRow(Long id, Long senderId, String senderNickname, String content, LocalDateTime createdAt) {
        static MessageRow of(Message m) {
            return new MessageRow(m.getId(), m.getSender().getId(), m.getSender().getNickname(),
                    m.getContent(), m.getCreatedAt());
        }
    }

    public record InquiryRow(Long id, Long userId, String userNickname, String userEmail,
                             InquiryType type, InquiryStatus status, String title, String content,
                             LocalDateTime createdAt) {
        static InquiryRow of(Inquiry i) {
            // userEmail: 계정 이메일이 아니라 문의 접수 시점에 저장된 값 — 탈퇴로 계정 이메일이 null이 돼도 답장 가능하도록.
            return new InquiryRow(i.getId(),
                    i.getUser().getId(), i.getUser().getNickname(), i.getEmail(),
                    i.getType(), i.getStatus(), i.getTitle(), i.getContent(), i.getCreatedAt());
        }
    }

    public record InquiryStatusReq(InquiryStatus status) {}

    public record ArtistRow(Long id, String name, String baseName, String nameKo, String nameAlias,
                            String imageUrl, ConcertCategory category) {
        static ArtistRow of(Artist a) {
            return new ArtistRow(a.getId(), a.getName(), a.getBaseName(), a.getNameKo(),
                    a.getNameAlias(), a.getImageUrl(), a.getCategory());
        }
    }
    public record ArtistReq(String name, String baseName, String nameKo, String nameAlias,
                            String imageUrl, ConcertCategory category) {}

    public record ConcertRow(Long id, Long artistId, String artistName, String title, String posterUrl,
                             String venue, LocalDate startDate, LocalDate endDate,
                             String price, String ticketUrl, String showtime, ConcertCategory category) {
        static ConcertRow of(Concert c) {
            return new ConcertRow(c.getId(),
                    c.getArtist() != null ? c.getArtist().getId() : null,
                    c.getArtist() != null ? c.getArtist().getName() : null,
                    c.getTitle(), c.getPosterUrl(), c.getVenue(),
                    c.getStartDate(), c.getEndDate(),
                    c.getPrice(), c.getTicketUrl(), c.getShowtime(), c.getCategory());
        }
    }
    public record ConcertReq(Long artistId, String title, String posterUrl, String venue,
                             LocalDate startDate, LocalDate endDate, ConcertCategory category,
                             String price, String ticketUrl, String showtime) {}

    public record ImportRow(Long id, String kopisId, String title, String posterUrl, String venue,
                            LocalDate startDate, LocalDate endDate,
                            Long matchedArtistId, String matchedArtistName,
                            ConcertCategory suggestedCategory, String matchReason,
                            ImportStatus status, Long publishedConcertId, String rejectReason,
                            LocalDateTime collectedAt, LocalDateTime reviewedAt) {
        static ImportRow of(ConcertImport i) {
            return new ImportRow(i.getId(), i.getKopisId(), i.getTitle(), i.getPosterUrl(), i.getVenue(),
                    i.getStartDate(), i.getEndDate(),
                    i.getMatchedArtist() != null ? i.getMatchedArtist().getId() : null,
                    i.getMatchedArtist() != null ? i.getMatchedArtist().getName() : null,
                    i.getSuggestedCategory(), i.getMatchReason(),
                    i.getStatus(), i.getPublishedConcertId(), i.getRejectReason(),
                    i.getCollectedAt(), i.getReviewedAt());
        }
    }

    public record CompanionRow(Long id, Long userId, String userNickname, Long concertId, String concertTitle,
                               String messageToCompanion, boolean visible, boolean sameGenderOnly, LocalDateTime createdAt) {
        static CompanionRow of(CompanionPost p) {
            return new CompanionRow(p.getId(),
                    p.getUser().getId(), p.getUser().getNickname(),
                    p.getConcert().getId(), p.getConcert().getTitle(),
                    p.getMessageToCompanion(), p.isVisible(), p.isSameGenderOnly(), p.getCreatedAt());
        }
    }

    public record AccessLogRow(Long id, Long adminId, String ip, String action, String targetType,
                               Long targetId, String description, LocalDateTime createdAt) {
        static AccessLogRow of(AdminAccessLog l) {
            return new AccessLogRow(l.getId(), l.getAdminId(), l.getIp(), l.getAction(),
                    l.getTargetType(), l.getTargetId(), l.getDescription(), l.getCreatedAt());
        }
    }

    public record MailLogRow(Long id, MailType type, String toEmail, String subject,
                             boolean success, String failReason, LocalDateTime sentAt) {
        static MailLogRow of(MailLog m) {
            return new MailLogRow(m.getId(), m.getType(), m.getToEmail(), m.getSubject(),
                    m.isSuccess(), m.getFailReason(), m.getSentAt());
        }
    }
}
