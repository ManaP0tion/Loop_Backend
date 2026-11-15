package com.loop.loop_backend.AiJob.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loop.loop_backend.AiJob.domain.AiJob;
import com.loop.loop_backend.AiJob.domain.AiJobStatus;
import com.loop.loop_backend.AiJob.domain.AiJobType;
import com.loop.loop_backend.AiJob.dto.AdminAiJobCreateRequest;
import com.loop.loop_backend.AiJob.dto.AdminAiJobResponse;
import com.loop.loop_backend.AiJob.dto.AiJobClaimResponse;
import com.loop.loop_backend.AiJob.dto.AiJobResultRequest;
import com.loop.loop_backend.AiJob.repository.AiJobRepository;
import com.loop.loop_backend.Artist.domain.Artist;
import com.loop.loop_backend.Artist.domain.Nationality;
import com.loop.loop_backend.Artist.repository.ArtistRepository;
import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.Concert.domain.ConcertCategory;
import com.loop.loop_backend.Concert.domain.TicketVendorInfo;
import com.loop.loop_backend.Concert.repository.ConcertRepository;
import com.loop.loop_backend.Performance.domain.Performance;
import com.loop.loop_backend.Performance.repository.PerformanceRepository;
import com.loop.loop_backend.Song.Repository.SongRepository;
import com.loop.loop_backend.Song.domain.Song;
import com.loop.loop_backend.Venue.domain.Venue;
import com.loop.loop_backend.Venue.repository.VenueRepository;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * AI 작업 연동 약속 기준.
 * - claim(3절): 요청한 종류 중 가장 오래된 PENDING 1건을 RUNNING으로 바꿔 준다(workerId, lease 10분, attempt+1). 없으면 빈 값.
 *   한 작업은 두 워커에 주지 않는다. Backend가 모르는 종류는 무시한다.
 * - 결과 보고(3절): 없는 작업 404, 받지 않으면 409. DONE이면 결과를, FAILED면 실패 정보를 남긴다.
 * - 시간 초과(5절): 첫 시도면 PENDING으로 되돌리고, 두 번째면 LEASE_EXPIRED로 끝낸다.
 * - 어드민(6절): 종류·공연만 받아 payload를 DB에서 만든다. 같은 대상의 진행 중인 작업이 있으면 그 ID.
 *   셋리스트는 보류(400), 라인업은 시작일 있는 페스티벌만(400).
 * - payload(2절): 한글 별칭이 앞, 공연장은 연결된 공연장 이름 우선, 예매처 이름, 대표 아티스트, yyyy-MM-dd.
 */
@DataJpaTest
class AiJobServiceTest {

    private static final String A = "pm-macbook";
    private static final String B = "ec2-worker";
    private static final List<String> ALL_TYPES = List.of("CONCERT_ENRICH", "SETLIST_ACTUAL", "FESTIVAL_LINEUP");
    private static final Long ADMIN = 1L;

    @Autowired private AiJobRepository aiJobRepository;
    @Autowired private ConcertRepository concertRepository;
    @Autowired private ArtistRepository artistRepository;
    @Autowired private VenueRepository venueRepository;
    @Autowired private PerformanceRepository performanceRepository;
    @Autowired private SongRepository songRepository;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private EntityManager em;

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private AiJobService service;

    @BeforeEach
    void setUp() {
        service = new AiJobService(aiJobRepository, concertRepository, performanceRepository, songRepository,
                new AiJobPayloadBuilder(),
                mapper, new TransactionTemplate(transactionManager));
    }

    private static void assertErrorCode(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(expected);
    }

    private Concert stage() {
        return concertRepository.save(Concert.builder()
                .title("YUURI LIVE 2026 IN SEOUL").category(ConcertCategory.STAGE)
                .startDate(LocalDate.of(2026, 12, 5)).endDate(LocalDate.of(2026, 12, 6)).build());
    }

    private Concert festival(LocalDate startDate) {
        return concertRepository.save(Concert.builder()
                .title("WONDERLIVET 2026").category(ConcertCategory.FESTIVAL)
                .startDate(startDate).endDate(startDate == null ? null : startDate.plusDays(2)).build());
    }

    private Long createJob(AiJobType type, Concert concert) {
        return service.create(ADMIN, new AdminAiJobCreateRequest(type, concert.getId(), null)).jobId();
    }

    private AiJob reload(Long jobId) {
        em.flush();
        em.clear();
        return aiJobRepository.findById(jobId).orElseThrow();
    }

    /** 시간이 흘러 lease가 지난 상황 */
    private void leasePassed(Long jobId) {
        em.flush();
        em.createQuery("update AiJob j set j.leaseUntil = :past where j.id = :id")
                .setParameter("past", LocalDateTime.now().minusMinutes(1))
                .setParameter("id", jobId)
                .executeUpdate();
        em.clear();
    }

    private AiJobResultRequest done(String workerId, int attempt, String resultJson) throws Exception {
        return new AiJobResultRequest(workerId, attempt, AiJobResultRequest.Outcome.DONE, mapper.readTree(resultJson), null, null);
    }

    private AiJobResultRequest failed(String workerId, int attempt, String code, String message, boolean retryable) {
        return new AiJobResultRequest(workerId, attempt, AiJobResultRequest.Outcome.FAILED, null,
                new AiJobResultRequest.Error(code, message, retryable), null);
    }

    @Nested
    class 작업_배정 {

        @Test
        void 대기_작업이_없으면_빈_값() {
            assertThat(service.claim(A, ALL_TYPES)).isEmpty();
        }

        @Test
        void 가장_오래된_대기_작업을_담당_워커_10분_lease_첫_시도로_넘긴다() {
            Long first = createJob(AiJobType.CONCERT_ENRICH, stage());
            createJob(AiJobType.CONCERT_ENRICH, stage());

            AiJobClaimResponse claimed = service.claim(A, ALL_TYPES).orElseThrow();

            assertThat(claimed.jobId()).isEqualTo(first);
            assertThat(claimed.type()).isEqualTo(AiJobType.CONCERT_ENRICH);
            assertThat(claimed.attempt()).isEqualTo(1);
            assertThat(claimed.payload().get("title").asText()).isEqualTo("YUURI LIVE 2026 IN SEOUL");

            AiJob job = reload(first);
            assertThat(job.getStatus()).isEqualTo(AiJobStatus.RUNNING);
            assertThat(job.getWorkerId()).isEqualTo(A);
            assertThat(job.getLeaseUntil()).isCloseTo(LocalDateTime.now().plusMinutes(10), within(1, ChronoUnit.MINUTES));
        }

        @Test
        void 한_작업은_두_워커에_주지_않는다() {
            Long only = createJob(AiJobType.CONCERT_ENRICH, stage());

            assertThat(service.claim(A, ALL_TYPES)).map(AiJobClaimResponse::jobId).contains(only);
            assertThat(service.claim(B, ALL_TYPES)).isEmpty();
        }

        @Test
        void 요청한_종류의_작업만_준다() {
            createJob(AiJobType.CONCERT_ENRICH, stage());
            Long lineup = createJob(AiJobType.FESTIVAL_LINEUP, festival(LocalDate.of(2026, 11, 20)));

            assertThat(service.claim(A, List.of("FESTIVAL_LINEUP"))).map(AiJobClaimResponse::jobId).contains(lineup);
            assertThat(service.claim(A, List.of("FESTIVAL_LINEUP"))).isEmpty();
        }

        @Test
        void Backend가_모르는_종류는_무시하고_아는_종류에서_준다() {
            Long job = createJob(AiJobType.CONCERT_ENRICH, stage());

            assertThat(service.claim(A, List.of("NEW_TYPE_FROM_FUTURE"))).isEmpty();
            assertThat(service.claim(A, List.of("NEW_TYPE_FROM_FUTURE", "CONCERT_ENRICH")))
                    .map(AiJobClaimResponse::jobId).contains(job);
        }

        @Test
        void 시간_초과로_되돌아온_작업을_다시_가져가면_두_번째_시도다() {
            Long job = createJob(AiJobType.CONCERT_ENRICH, stage());
            service.claim(A, ALL_TYPES);
            leasePassed(job);
            service.expireLeases();

            AiJobClaimResponse again = service.claim(B, ALL_TYPES).orElseThrow();

            assertThat(again.jobId()).isEqualTo(job);
            assertThat(again.attempt()).isEqualTo(2);
            assertThat(reload(job).getWorkerId()).isEqualTo(B);
        }
    }

    @Nested
    class 결과_보고 {

        @Test
        void 없는_작업이면_404() throws Exception {
            AiJobResultRequest request = done(A, 1, "{}");
            assertErrorCode(() -> service.report(999L, request), ErrorCode.AI_JOB_NOT_FOUND);
        }

        @Test
        void 담당_워커의_결과는_DONE으로_저장되고_어드민_조회에_JSON_그대로_나온다() throws Exception {
            Long job = createJob(AiJobType.CONCERT_ENRICH, stage());
            service.claim(A, ALL_TYPES);

            service.report(job, done(A, 1, "{\"status\":\"NOT_FOUND\",\"presales\":[]}"));

            AdminAiJobResponse response = service.get(job);
            assertThat(response.status()).isEqualTo(AiJobStatus.DONE);
            assertThat(response.result().get("status").asText()).isEqualTo("NOT_FOUND");
            assertThat(response.result().get("presales").isArray()).isTrue();
            assertThat(response.error()).isNull();
            assertThat(response.finishedAt()).isNotNull();
        }

        @Test
        void 워커가_보고한_실패는_코드_문구_다시_시도_여부와_함께_FAILED로_남는다() {
            Long job = createJob(AiJobType.CONCERT_ENRICH, stage());
            service.claim(A, ALL_TYPES);

            service.report(job, failed(A, 1, "LLM_TIMEOUT", "AI 분석 시간이 초과됐습니다.", true));

            AdminAiJobResponse response = service.get(job);
            assertThat(response.status()).isEqualTo(AiJobStatus.FAILED);
            assertThat(response.result()).isNull();
            assertThat(response.error().code()).isEqualTo("LLM_TIMEOUT");
            assertThat(response.error().message()).isEqualTo("AI 분석 시간이 초과됐습니다.");
            assertThat(response.error().retryable()).isTrue();
        }

        @Test
        void 긴_실패_문구도_보고는_받는다() {
            Long job = createJob(AiJobType.CONCERT_ENRICH, stage());
            service.claim(A, ALL_TYPES);

            service.report(job, failed(A, 1, "INTERNAL_ERROR", "가".repeat(800), false));

            assertThat(service.get(job).status()).isEqualTo(AiJobStatus.FAILED);
        }

        @Test
        void 담당이_아닌_워커의_보고는_409() throws Exception {
            Long job = createJob(AiJobType.CONCERT_ENRICH, stage());
            service.claim(A, ALL_TYPES);
            AiJobResultRequest request = done(B, 1, "{}");

            assertErrorCode(() -> service.report(job, request), ErrorCode.AI_JOB_NOT_ACCEPTED);
            assertThat(reload(job).getStatus()).isEqualTo(AiJobStatus.RUNNING);
        }

        @Test
        void 이미_DONE인_작업에_다시_보고하면_409() throws Exception {
            Long job = createJob(AiJobType.CONCERT_ENRICH, stage());
            service.claim(A, ALL_TYPES);
            service.report(job, done(A, 1, "{\"status\":\"FOUND\"}"));
            AiJobResultRequest again = done(A, 1, "{\"status\":\"PARTIAL\"}");

            assertErrorCode(() -> service.report(job, again), ErrorCode.AI_JOB_NOT_ACCEPTED);
            assertThat(service.get(job).result().get("status").asText()).isEqualTo("FOUND");
        }

        @Test
        void 시간_초과로_되돌아갔지만_아무도_안_가져갔으면_직전_담당의_결과를_받는다() throws Exception {
            Long job = createJob(AiJobType.CONCERT_ENRICH, stage());
            service.claim(A, ALL_TYPES);
            leasePassed(job);
            service.expireLeases();

            service.report(job, done(A, 1, "{\"status\":\"FOUND\"}"));

            assertThat(service.get(job).status()).isEqualTo(AiJobStatus.DONE);
            assertThat(service.claim(B, ALL_TYPES)).isEmpty();
        }

        @Test
        void 다시_가져간_뒤_옛_시도의_보고는_409() throws Exception {
            Long job = createJob(AiJobType.CONCERT_ENRICH, stage());
            service.claim(A, ALL_TYPES);
            leasePassed(job);
            service.expireLeases();
            service.claim(A, ALL_TYPES); // 재시작한 같은 워커가 다시 가져감 - attempt 2
            AiJobResultRequest stale = done(A, 1, "{}");

            assertErrorCode(() -> service.report(job, stale), ErrorCode.AI_JOB_NOT_ACCEPTED);
        }

        @Test
        void 시간_초과로_끝난_뒤_도착한_결과는_받아_DONE으로_덮어쓴다() throws Exception {
            Long job = createJob(AiJobType.CONCERT_ENRICH, stage());
            service.claim(A, ALL_TYPES);
            leasePassed(job);
            service.expireLeases();
            service.claim(A, ALL_TYPES);
            leasePassed(job);
            service.expireLeases();
            assertThat(service.get(job).error().code()).isEqualTo("LEASE_EXPIRED");

            service.report(job, done(A, 2, "{\"status\":\"FOUND\"}"));

            AdminAiJobResponse response = service.get(job);
            assertThat(response.status()).isEqualTo(AiJobStatus.DONE);
            assertThat(response.error()).isNull();
        }
    }

    @Nested
    class 시간_초과_정리 {

        @Test
        void 첫_시도가_시간_초과면_다시_대기로_돌린다() {
            Long job = createJob(AiJobType.CONCERT_ENRICH, stage());
            service.claim(A, ALL_TYPES);
            leasePassed(job);

            service.expireLeases();

            AiJob reloaded = reload(job);
            assertThat(reloaded.getStatus()).isEqualTo(AiJobStatus.PENDING);
            assertThat(reloaded.getWorkerId()).isEqualTo(A);
            assertThat(reloaded.getAttempt()).isEqualTo(1);
        }

        @Test
        void 두_번째_시도도_시간_초과면_다시_시도할_수_있는_LEASE_EXPIRED로_끝낸다() {
            Long job = createJob(AiJobType.CONCERT_ENRICH, stage());
            service.claim(A, ALL_TYPES);
            leasePassed(job);
            service.expireLeases();
            service.claim(B, ALL_TYPES);
            leasePassed(job);

            service.expireLeases();

            AdminAiJobResponse response = service.get(job);
            assertThat(response.status()).isEqualTo(AiJobStatus.FAILED);
            assertThat(response.error().code()).isEqualTo("LEASE_EXPIRED");
            assertThat(response.error().retryable()).isTrue();
        }

        @Test
        void lease_안의_작업은_그대로_둔다() {
            Long job = createJob(AiJobType.CONCERT_ENRICH, stage());
            service.claim(A, ALL_TYPES);

            service.expireLeases();

            assertThat(reload(job).getStatus()).isEqualTo(AiJobStatus.RUNNING);
        }
    }

    @Nested
    class 어드민_작업_생성 {

        @Test
        void 없는_공연이면_404() {
            assertErrorCode(() -> service.create(ADMIN, new AdminAiJobCreateRequest(AiJobType.CONCERT_ENRICH, 999L, null)),
                    ErrorCode.CONCERT_NOT_FOUND);
        }

        @Test
        void 라인업은_단독_공연에_요청할_수_없다() {
            Concert concert = stage();
            assertErrorCode(() -> createJob(AiJobType.FESTIVAL_LINEUP, concert), ErrorCode.AI_JOB_FESTIVAL_ONLY);
        }

        @Test
        void 라인업은_시작일_없는_페스티벌에_요청할_수_없다() {
            Concert concert = festival(null);
            assertErrorCode(() -> createJob(AiJobType.FESTIVAL_LINEUP, concert), ErrorCode.AI_JOB_START_DATE_REQUIRED);
        }

        @Test
        void 공연_정보는_단독_공연과_페스티벌_모두_요청할_수_있다() {
            assertThat(createJob(AiJobType.CONCERT_ENRICH, stage())).isNotNull();
            assertThat(createJob(AiJobType.CONCERT_ENRICH, festival(null))).isNotNull();
        }

        @Test
        void 새_작업은_대기_상태로_만들어진다() {
            Long job = createJob(AiJobType.CONCERT_ENRICH, stage());

            AdminAiJobResponse response = service.get(job);
            assertThat(response.status()).isEqualTo(AiJobStatus.PENDING);
            assertThat(response.attempt()).isZero();
            assertThat(response.createdAt()).isNotNull();
        }

        @Test
        void 같은_공연_종류의_작업이_대기나_처리_중이면_그_작업ID를_돌려준다() {
            Concert concert = stage();
            Long first = createJob(AiJobType.CONCERT_ENRICH, concert);

            assertThat(createJob(AiJobType.CONCERT_ENRICH, concert)).isEqualTo(first);

            service.claim(A, ALL_TYPES);
            assertThat(createJob(AiJobType.CONCERT_ENRICH, concert)).isEqualTo(first);
        }

        @Test
        void 같은_공연이라도_종류가_다르면_새로_만든다() {
            Concert concert = festival(LocalDate.of(2026, 11, 20));
            Long enrich = createJob(AiJobType.CONCERT_ENRICH, concert);

            assertThat(createJob(AiJobType.FESTIVAL_LINEUP, concert)).isNotEqualTo(enrich);
        }

        @Test
        void 실패로_끝난_뒤_다시_요청하면_새_작업을_만든다() {
            Concert concert = stage();
            Long first = createJob(AiJobType.CONCERT_ENRICH, concert);
            service.claim(A, ALL_TYPES);
            service.report(first, failed(A, 1, "SEARCH_FAILED", "검색에 실패했습니다.", true));

            assertThat(createJob(AiJobType.CONCERT_ENRICH, concert)).isNotEqualTo(first);
        }

        @Test
        void 없는_작업을_조회하면_404() {
            assertErrorCode(() -> service.get(999L), ErrorCode.AI_JOB_NOT_FOUND);
        }
    }

    @Nested
    class 마지막_작업_조회 {

        @Test
        void 한_번도_찾은_적_없으면_빈_값() {
            Concert concert = stage();

            assertThat(service.findLatest(concert.getId(), AiJobType.CONCERT_ENRICH, null)).isEmpty();
        }

        @Test
        void 끝난_작업의_결과를_다시_찾지_않고_볼_수_있다() throws Exception {
            Concert concert = stage();
            Long job = createJob(AiJobType.CONCERT_ENRICH, concert);
            service.claim(A, ALL_TYPES);
            service.report(job, done(A, 1, "{\"status\":\"FOUND\",\"presales\":[]}"));
            em.flush();
            em.clear();

            AdminAiJobResponse latest = service.findLatest(concert.getId(), AiJobType.CONCERT_ENRICH, null).orElseThrow();

            assertThat(latest.jobId()).isEqualTo(job);
            assertThat(latest.status()).isEqualTo(AiJobStatus.DONE);
            assertThat(latest.result().get("status").asText()).isEqualTo("FOUND");
            assertThat(latest.finishedAt()).isNotNull();
        }

        @Test
        void 상태와_관계없이_가장_나중에_만든_작업을_준다() throws Exception {
            Concert concert = stage();
            Long first = createJob(AiJobType.CONCERT_ENRICH, concert);
            service.claim(A, ALL_TYPES);
            service.report(first, done(A, 1, "{\"status\":\"FOUND\"}"));
            Long second = createJob(AiJobType.CONCERT_ENRICH, concert);
            service.claim(A, ALL_TYPES);
            service.report(second, failed(A, 1, "SEARCH_FAILED", "검색에 실패했습니다.", true));

            AdminAiJobResponse latest = service.findLatest(concert.getId(), AiJobType.CONCERT_ENRICH, null).orElseThrow();

            assertThat(latest.jobId()).isEqualTo(second);
            assertThat(latest.status()).isEqualTo(AiJobStatus.FAILED);
            assertThat(latest.result()).isNull();
            assertThat(latest.error().code()).isEqualTo("SEARCH_FAILED");
            assertThat(latest.error().retryable()).isTrue();
        }

        @Test
        void 진행_중인_작업이면_그_작업을_주어_이어서_조회하게_한다() {
            Concert concert = stage();
            Long job = createJob(AiJobType.CONCERT_ENRICH, concert);

            AdminAiJobResponse latest = service.findLatest(concert.getId(), AiJobType.CONCERT_ENRICH, null).orElseThrow();

            assertThat(latest.jobId()).isEqualTo(job);
            assertThat(latest.status()).isEqualTo(AiJobStatus.PENDING);
        }

        @Test
        void 다른_공연이나_다른_종류의_작업은_섞이지_않는다() {
            Concert festival = festival(LocalDate.of(2026, 11, 20));
            Concert other = stage();
            Long enrich = createJob(AiJobType.CONCERT_ENRICH, festival);
            createJob(AiJobType.FESTIVAL_LINEUP, festival);
            createJob(AiJobType.CONCERT_ENRICH, other);

            assertThat(service.findLatest(festival.getId(), AiJobType.CONCERT_ENRICH, null).orElseThrow().jobId())
                    .isEqualTo(enrich);
            assertThat(service.findLatest(other.getId(), AiJobType.FESTIVAL_LINEUP, null)).isEmpty();
        }
    }

    /** 후속 작업 1절: 관리자 취소. PENDING·RUNNING만 취소하고, 취소한 작업은 다시 처리되거나 결과를 받지 않는다. */
    @Nested
    class 작업_취소 {

        @Test
        void 대기_중인_작업을_취소하면_워커가_가져가지_않는다() {
            Long job = createJob(AiJobType.CONCERT_ENRICH, stage());

            AdminAiJobResponse canceled = service.cancel(ADMIN, job);

            assertThat(canceled.status()).isEqualTo(AiJobStatus.CANCELED);
            assertThat(canceled.finishedAt()).isNotNull();
            assertThat(reload(job).getCanceledBy()).isEqualTo(ADMIN);
            assertThat(service.claim(A, ALL_TYPES)).isEmpty();
        }

        @Test
        void 처리_중인_작업을_취소하면_그_뒤_도착한_결과는_409이고_취소가_유지된다() throws Exception {
            Long job = createJob(AiJobType.CONCERT_ENRICH, stage());
            service.claim(A, ALL_TYPES);

            service.cancel(ADMIN, job);
            AiJobResultRequest late = done(A, 1, "{\"status\":\"FOUND\"}");

            assertErrorCode(() -> service.report(job, late), ErrorCode.AI_JOB_NOT_ACCEPTED);
            AiJob reloaded = reload(job);
            assertThat(reloaded.getStatus()).isEqualTo(AiJobStatus.CANCELED);
            assertThat(reloaded.getResult()).isNull();
        }

        @Test
        void 취소된_작업은_시간_초과_정리_대상이_아니다() {
            Long job = createJob(AiJobType.CONCERT_ENRICH, stage());
            service.claim(A, ALL_TYPES);
            service.cancel(ADMIN, job);
            leasePassed(job);

            service.expireLeases();

            assertThat(reload(job).getStatus()).isEqualTo(AiJobStatus.CANCELED);
        }

        @Test
        void 끝난_작업은_취소할_수_없다() throws Exception {
            Long done = createJob(AiJobType.CONCERT_ENRICH, stage());
            service.claim(A, ALL_TYPES);
            service.report(done, done(A, 1, "{\"status\":\"FOUND\"}"));

            Long failed = createJob(AiJobType.CONCERT_ENRICH, stage());
            service.claim(A, ALL_TYPES);
            service.report(failed, failed(A, 1, "SEARCH_FAILED", "검색에 실패했습니다.", true));

            Long canceled = createJob(AiJobType.CONCERT_ENRICH, stage());
            service.cancel(ADMIN, canceled);

            assertErrorCode(() -> service.cancel(ADMIN, done), ErrorCode.AI_JOB_ALREADY_FINISHED);
            assertErrorCode(() -> service.cancel(ADMIN, failed), ErrorCode.AI_JOB_ALREADY_FINISHED);
            assertErrorCode(() -> service.cancel(ADMIN, canceled), ErrorCode.AI_JOB_ALREADY_FINISHED);
            assertThat(reload(done).getStatus()).isEqualTo(AiJobStatus.DONE);
        }

        @Test
        void 없는_작업을_취소하면_404() {
            assertErrorCode(() -> service.cancel(ADMIN, 999L), ErrorCode.AI_JOB_NOT_FOUND);
        }

        @Test
        void 취소한_뒤_같은_공연_종류로_새_작업을_만들_수_있다() {
            Concert concert = stage();
            Long first = createJob(AiJobType.CONCERT_ENRICH, concert);
            service.cancel(ADMIN, first);

            assertThat(createJob(AiJobType.CONCERT_ENRICH, concert)).isNotEqualTo(first);
        }

        @Test
        void 마지막_작업_조회는_취소된_작업도_그대로_준다() {
            Concert concert = stage();
            Long job = createJob(AiJobType.CONCERT_ENRICH, concert);
            service.cancel(ADMIN, job);

            AdminAiJobResponse latest = service.findLatest(concert.getId(), AiJobType.CONCERT_ENRICH, null).orElseThrow();

            assertThat(latest.jobId()).isEqualTo(job);
            assertThat(latest.status()).isEqualTo(AiJobStatus.CANCELED);
        }
    }

    /** 후속 작업 3절: 결과와 함께 온 분석 정보(meta)를 남긴다. meta 때문에 결과 보고가 실패하면 안 된다. */
    @Nested
    class 분석_정보 {

        private static final String META = """
                {"model":"claude-haiku-5-5","llmCalls":[{"purpose":"extract","outputTokens":4951}],
                 "timings":{"totalMs":25300}}""";

        private AiJobResultRequest doneWithMeta(int attempt, JsonNode meta) throws Exception {
            return new AiJobResultRequest(A, attempt, AiJobResultRequest.Outcome.DONE,
                    mapper.readTree("{\"status\":\"FOUND\"}"), null, meta);
        }

        private Long claimedJob() {
            Long job = createJob(AiJobType.CONCERT_ENRICH, stage());
            service.claim(A, ALL_TYPES);
            return job;
        }

        @Test
        void 성공_결과와_함께_온_분석_정보를_남긴다() throws Exception {
            Long job = claimedJob();

            service.report(job, doneWithMeta(1, mapper.readTree(META)));

            AiJob reloaded = reload(job);
            assertThat(reloaded.getStatus()).isEqualTo(AiJobStatus.DONE);
            assertThat(mapper.readTree(reloaded.getMeta())).isEqualTo(mapper.readTree(META));
        }

        @Test
        void 실패_보고와_함께_온_분석_정보도_남긴다() throws Exception {
            Long job = claimedJob();

            service.report(job, new AiJobResultRequest(A, 1, AiJobResultRequest.Outcome.FAILED, null,
                    new AiJobResultRequest.Error("LLM_FAILED", "AI 분석에 실패했습니다.", true),
                    mapper.readTree("{\"errorDetail\":\"LLM 응답 오류: HTTP 401\"}")));

            AiJob reloaded = reload(job);
            assertThat(reloaded.getStatus()).isEqualTo(AiJobStatus.FAILED);
            assertThat(mapper.readTree(reloaded.getMeta()).get("errorDetail").asText()).isEqualTo("LLM 응답 오류: HTTP 401");
        }

        @Test
        void 분석_정보가_없어도_결과는_저장된다() throws Exception {
            Long job = claimedJob();

            service.report(job, doneWithMeta(1, null));

            AiJob reloaded = reload(job);
            assertThat(reloaded.getStatus()).isEqualTo(AiJobStatus.DONE);
            assertThat(reloaded.getMeta()).isNull();
        }

        @Test
        void 너무_큰_분석_정보는_버리고_버렸다는_표시만_남기며_결과는_저장된다() throws Exception {
            Long job = claimedJob();
            String big = "x".repeat(64 * 1024 + 1);

            service.report(job, doneWithMeta(1, mapper.createObjectNode().put("warnings", big)));

            AiJob reloaded = reload(job);
            assertThat(reloaded.getStatus()).isEqualTo(AiJobStatus.DONE);
            assertThat(reloaded.getResult()).isNotNull();
            JsonNode meta = mapper.readTree(reloaded.getMeta());
            assertThat(meta.get("dropped").asText()).isEqualTo("TOO_LARGE");
            assertThat(meta.get("bytes").asInt()).isGreaterThan(64 * 1024);
        }

        @Test
        void 받지_않은_보고의_분석_정보는_남기지_않는다() throws Exception {
            Long job = claimedJob();
            AiJobResultRequest notAssigned = new AiJobResultRequest(B, 1, AiJobResultRequest.Outcome.DONE,
                    mapper.readTree("{}"), null, mapper.readTree(META));

            assertErrorCode(() -> service.report(job, notAssigned), ErrorCode.AI_JOB_NOT_ACCEPTED);
            assertThat(reload(job).getMeta()).isNull();
        }

        @Test
        void 어드민_조회에는_분석_정보를_내보내지_않는다() throws Exception {
            Long job = claimedJob();
            service.report(job, doneWithMeta(1, mapper.readTree(META)));

            JsonNode response = mapper.valueToTree(service.get(job));

            assertThat(response.has("meta")).isFalse();
            assertThat(response.toString()).doesNotContain("claude-haiku-5-5");
        }
    }

    /**
     * 후속 작업 2절: 셋리스트는 출연 정보(performance) 단위다. 단독 공연은 출연 정보 1개, 페스티벌은 헤드라이너마다.
     * payload는 그 출연 정보의 아티스트 한 명과 그 아티스트의 곡 목록(AI가 songId로 맞춘다)을 담는다.
     */
    @Nested
    class 셋리스트_작업 {

        private Artist yuuri() {
            return artistRepository.save(Artist.builder().name("Yuuri").nameKo("유우리").nameAlias("優里")
                    .nationality(Nationality.JP).build());
        }

        private Performance solo(Concert concert, Artist artist) {
            return performanceRepository.save(Performance.solo(concert, artist));
        }

        private Performance lineup(Concert festival, Artist artist, boolean headliner, int... days) {
            Performance performance = Performance.builder().concert(festival).artist(artist)
                    .day(days[0]).displayOrder(1).build();
            for (int i = 1; i < days.length; i++) {
                performance.addDay(days[i]);
            }
            performance.changeHeadliner(headliner);
            return performanceRepository.save(performance);
        }

        private Song song(Artist artist, String original, String romanized, String ko, int order) {
            return songRepository.save(Song.builder().artist(artist).titleOriginal(original)
                    .titleRomanized(romanized).titleKo(ko).sortOrder(order).build());
        }

        private Long createSetlist(Concert concert, Long performanceId) {
            return service.create(ADMIN, new AdminAiJobCreateRequest(AiJobType.SETLIST_ACTUAL, concert.getId(), performanceId))
                    .jobId();
        }

        private JsonNode payloadOf(Long jobId) throws Exception {
            return mapper.readTree(reload(jobId).getPayload());
        }

        @Test
        void 단독_공연_셋리스트는_아티스트와_곡_목록과_예상_곡_수와_공연_기간을_담는다() throws Exception {
            Artist artist = yuuri();
            Concert concert = concertRepository.save(Concert.builder()
                    .title("YUURI LIVE 2026 IN SEOUL").category(ConcertCategory.STAGE).artist(artist)
                    .startDate(LocalDate.of(2026, 12, 5)).endDate(LocalDate.of(2026, 12, 6))
                    .venue("인스파이어 아레나").build());
            Performance performance = solo(concert, artist);
            performance.changeExpectedSongCount(22);
            Song dry = song(artist, "ドライフラワー", "Dry Flower", "드라이플라워", 1);
            Song kakurenbo = song(artist, "かくれんぼ", "Kakurenbo", "숨바꼭질", 2);

            Long job = createSetlist(concert, performance.getId());

            AiJob saved = reload(job);
            assertThat(saved.getType()).isEqualTo(AiJobType.SETLIST_ACTUAL);
            assertThat(saved.getPerformanceId()).isEqualTo(performance.getId());
            JsonNode payload = payloadOf(job);
            assertThat(payload.get("concertId").asLong()).isEqualTo(concert.getId());
            assertThat(payload.get("category").asText()).isEqualTo("STAGE");
            assertThat(payload.get("title").asText()).isEqualTo("YUURI LIVE 2026 IN SEOUL");
            assertThat(payload.get("artist").get("name").asText()).isEqualTo("Yuuri");
            assertThat(payload.get("artist").get("nameKo").asText()).isEqualTo("유우리");
            assertThat(payload.get("artist").get("nameAlias").asText()).isEqualTo("優里");
            assertThat(payload.get("startDate").asText()).isEqualTo("2026-12-05");
            assertThat(payload.get("endDate").asText()).isEqualTo("2026-12-06");
            assertThat(payload.get("venueName").asText()).isEqualTo("인스파이어 아레나");
            assertThat(payload.get("expectedSongCount").asInt()).isEqualTo(22);
            assertThat(payload.get("songs")).hasSize(2);
            assertThat(payload.get("songs").get(0).get("id").asLong()).isEqualTo(dry.getId());
            assertThat(payload.get("songs").get(0).get("titleOriginal").asText()).isEqualTo("ドライフラワー");
            assertThat(payload.get("songs").get(0).get("titleRomanized").asText()).isEqualTo("Dry Flower");
            assertThat(payload.get("songs").get(0).get("titleKo").asText()).isEqualTo("드라이플라워");
            assertThat(payload.get("songs").get(1).get("id").asLong()).isEqualTo(kakurenbo.getId());
        }

        @Test
        void 삭제된_곡은_곡_목록에서_빠지고_곡이_없으면_빈_목록이다() throws Exception {
            Artist artist = yuuri();
            Concert concert = stage();
            Performance performance = solo(concert, artist);
            Song deleted = song(artist, "旧曲", null, null, 1);
            songRepository.delete(deleted);
            em.flush();
            em.clear();

            JsonNode payload = payloadOf(createSetlist(concert, performance.getId()));

            assertThat(payload.get("songs").isArray()).isTrue();
            assertThat(payload.get("songs")).isEmpty();
            assertThat(payload.get("expectedSongCount").isNull()).isTrue();
        }

        @Test
        void 페스티벌_헤드라이너는_그_아티스트가_출연하는_DAY의_날짜로_찾는다() throws Exception {
            Artist artist = yuuri();
            Concert festival = festival(LocalDate.of(2026, 11, 20));
            Performance headliner = lineup(festival, artist, true, 2, 3);

            JsonNode payload = payloadOf(createSetlist(festival, headliner.getId()));

            assertThat(payload.get("category").asText()).isEqualTo("FESTIVAL");
            assertThat(payload.get("title").asText()).isEqualTo("WONDERLIVET 2026");
            assertThat(payload.get("artist").get("name").asText()).isEqualTo("Yuuri");
            assertThat(payload.get("startDate").asText()).isEqualTo("2026-11-21");
            assertThat(payload.get("endDate").asText()).isEqualTo("2026-11-22");
        }

        @Test
        void 출연_정보를_지정하지_않으면_400() {
            Concert concert = stage();
            solo(concert, yuuri());

            assertErrorCode(() -> createSetlist(concert, null), ErrorCode.AI_JOB_PERFORMANCE_REQUIRED);
        }

        @Test
        void 다른_공연의_출연_정보이거나_없는_출연_정보면_400() {
            Concert concert = stage();
            Concert other = stage();
            Performance othersPerformance = solo(other, yuuri());

            assertErrorCode(() -> createSetlist(concert, othersPerformance.getId()), ErrorCode.AI_JOB_PERFORMANCE_NOT_IN_CONCERT);
            assertErrorCode(() -> createSetlist(concert, 999L), ErrorCode.AI_JOB_PERFORMANCE_NOT_IN_CONCERT);
        }

        @Test
        void 페스티벌은_헤드라이너가_아니면_400() {
            Concert festival = festival(LocalDate.of(2026, 11, 20));
            Performance notHeadliner = lineup(festival, yuuri(), false, 1);

            assertErrorCode(() -> createSetlist(festival, notHeadliner.getId()), ErrorCode.AI_JOB_HEADLINER_ONLY);
        }

        @Test
        void 공연_시작일이_없으면_400() {
            Concert concert = concertRepository.save(Concert.builder()
                    .title("YUURI LIVE").category(ConcertCategory.STAGE).build());
            Performance performance = solo(concert, yuuri());

            assertErrorCode(() -> createSetlist(concert, performance.getId()), ErrorCode.AI_JOB_START_DATE_REQUIRED);
        }

        @Test
        void 같은_출연_정보는_진행_중인_작업을_돌려주고_다른_헤드라이너는_새로_만든다() {
            Concert festival = festival(LocalDate.of(2026, 11, 20));
            Performance first = lineup(festival, yuuri(), true, 1);
            Performance second = lineup(festival, artistRepository.save(Artist.builder().name("Vaundy")
                    .nationality(Nationality.JP).build()), true, 2);
            Long firstJob = createSetlist(festival, first.getId());

            assertThat(createSetlist(festival, first.getId())).isEqualTo(firstJob);
            assertThat(createSetlist(festival, second.getId())).isNotEqualTo(firstJob);
        }

        @Test
        void 셋리스트가_아닌_작업은_출연_정보를_보내도_무시하고_공연_단위로_본다() {
            Concert concert = stage();
            Performance performance = solo(concert, yuuri());
            Long enrich = createJob(AiJobType.CONCERT_ENRICH, concert);

            Long again = service.create(ADMIN,
                    new AdminAiJobCreateRequest(AiJobType.CONCERT_ENRICH, concert.getId(), performance.getId())).jobId();

            assertThat(again).isEqualTo(enrich);
            assertThat(reload(enrich).getPerformanceId()).isNull();
        }

        @Test
        void 마지막_작업_조회는_출연_정보별로_따로_준다() {
            Concert festival = festival(LocalDate.of(2026, 11, 20));
            Performance first = lineup(festival, yuuri(), true, 1);
            Performance second = lineup(festival, artistRepository.save(Artist.builder().name("Vaundy")
                    .nationality(Nationality.JP).build()), true, 2);
            Long firstJob = createSetlist(festival, first.getId());

            AdminAiJobResponse latest = service.findLatest(festival.getId(), AiJobType.SETLIST_ACTUAL, first.getId())
                    .orElseThrow();
            assertThat(latest.jobId()).isEqualTo(firstJob);
            assertThat(latest.performanceId()).isEqualTo(first.getId());
            assertThat(service.findLatest(festival.getId(), AiJobType.SETLIST_ACTUAL, second.getId())).isEmpty();
        }

        @Test
        void 셋리스트의_마지막_작업_조회는_출연_정보가_없으면_400() {
            Concert concert = stage();

            assertErrorCode(() -> service.findLatest(concert.getId(), AiJobType.SETLIST_ACTUAL, null),
                    ErrorCode.AI_JOB_PERFORMANCE_REQUIRED);
        }

        @Test
        void 다른_종류의_마지막_작업_조회는_출연_정보를_보내도_무시한다() {
            Concert concert = stage();
            Performance performance = solo(concert, yuuri());
            Long enrich = createJob(AiJobType.CONCERT_ENRICH, concert);

            assertThat(service.findLatest(concert.getId(), AiJobType.CONCERT_ENRICH, performance.getId())
                    .orElseThrow().jobId()).isEqualTo(enrich);
        }

        @Test
        void 셋리스트_작업이_있어도_같은_공연의_다른_종류_작업과_섞이지_않는다() {
            Concert concert = stage();
            Performance performance = solo(concert, yuuri());
            Long setlist = createSetlist(concert, performance.getId());

            Long enrich = createJob(AiJobType.CONCERT_ENRICH, concert);

            assertThat(enrich).isNotEqualTo(setlist);
            assertThat(service.findLatest(concert.getId(), AiJobType.CONCERT_ENRICH, null).orElseThrow().jobId())
                    .isEqualTo(enrich);
        }
    }

    @Nested
    class payload_만들기 {

        private JsonNode payloadOf(Long jobId) throws Exception {
            return mapper.readTree(reload(jobId).getPayload());
        }

        @Test
        void 공연_정보_payload는_공연과_대표_아티스트와_예매처를_담는다() throws Exception {
            Artist yuuri = artistRepository.save(Artist.builder().name("Yuuri").nameKo("유우리").nameAlias("優里")
                    .nationality(Nationality.JP).build());
            Concert concert = concertRepository.save(Concert.builder()
                    .kopisId("PF297519").title("YUURI LIVE 2026 IN SEOUL").category(ConcertCategory.STAGE)
                    .artist(yuuri)
                    .startDate(LocalDate.of(2026, 12, 5)).endDate(LocalDate.of(2026, 12, 6))
                    .venue("인스파이어 아레나")
                    .ticketVendors(List.of(new TicketVendorInfo("NOL", "https://nol"), new TicketVendorInfo("NOL", "https://nol2")))
                    .build());

            JsonNode payload = payloadOf(createJob(AiJobType.CONCERT_ENRICH, concert));

            assertThat(payload.get("concertId").asLong()).isEqualTo(concert.getId());
            assertThat(payload.get("kopisId").asText()).isEqualTo("PF297519");
            assertThat(payload.get("title").asText()).isEqualTo("YUURI LIVE 2026 IN SEOUL");
            assertThat(payload.get("category").asText()).isEqualTo("STAGE");
            assertThat(payload.get("artists")).hasSize(1);
            assertThat(payload.get("artists").get(0).get("name").asText()).isEqualTo("Yuuri");
            assertThat(payload.get("artists").get(0).get("nameKo").asText()).isEqualTo("유우리");
            assertThat(payload.get("artists").get(0).get("nameAlias").asText()).isEqualTo("優里");
            assertThat(payload.get("startDate").asText()).isEqualTo("2026-12-05");
            assertThat(payload.get("endDate").asText()).isEqualTo("2026-12-06");
            assertThat(payload.get("venueName").asText()).isEqualTo("인스파이어 아레나");
            assertThat(payload.get("knownVendors")).hasSize(1);
            assertThat(payload.get("knownVendors").get(0).asText()).isEqualTo("NOL");
        }

        @Test
        void 아티스트와_예매처와_별칭이_없으면_빈_목록이다() throws Exception {
            JsonNode payload = payloadOf(createJob(AiJobType.CONCERT_ENRICH, festival(null)));

            assertThat(payload.get("artists").isArray()).isTrue();
            assertThat(payload.get("artists")).isEmpty();
            assertThat(payload.get("knownVendors")).isEmpty();
            assertThat(payload.get("titleAliases")).isEmpty();
        }

        @Test
        void 한글이_들어간_별칭이_앞에_오고_나머지는_원래_순서를_지킨다() throws Exception {
            Concert concert = concertRepository.save(Concert.builder()
                    .title("Official HIGE DANdism LIVE").category(ConcertCategory.STAGE)
                    .titleAliases(new ArrayList<>(List.of("OHD", "히게단", "Higedan", "오피셜히게단디즘")))
                    .build());

            JsonNode aliases = payloadOf(createJob(AiJobType.CONCERT_ENRICH, concert)).get("titleAliases");

            List<String> values = new ArrayList<>();
            aliases.forEach(node -> values.add(node.asText()));
            assertThat(values).containsExactly("히게단", "오피셜히게단디즘", "OHD", "Higedan");
        }

        @Test
        void 공연장은_공연장_관리에_연결된_이름을_먼저_쓴다() throws Exception {
            Venue kintex = venueRepository.save(Venue.builder().name("킨텍스 제2전시장").address("경기도 고양시").build());
            Concert concert = concertRepository.save(Concert.builder()
                    .title("WONDERLIVET 2026").category(ConcertCategory.FESTIVAL)
                    .startDate(LocalDate.of(2026, 11, 20)).endDate(LocalDate.of(2026, 11, 22))
                    .venue("KINTEX").linkedVenue(kintex)
                    .build());

            JsonNode payload = payloadOf(createJob(AiJobType.FESTIVAL_LINEUP, concert));

            assertThat(payload.get("venueName").asText()).isEqualTo("킨텍스 제2전시장");
        }

        @Test
        void 라인업_payload는_공연명_별칭_기간_공연장을_담는다() throws Exception {
            Concert concert = concertRepository.save(Concert.builder()
                    .title("WONDERLIVET 2026").category(ConcertCategory.FESTIVAL)
                    .titleAliases(new ArrayList<>(List.of("원더리벳 2026")))
                    .startDate(LocalDate.of(2026, 11, 20)).endDate(LocalDate.of(2026, 11, 22))
                    .venue("킨텍스")
                    .build());

            JsonNode payload = payloadOf(createJob(AiJobType.FESTIVAL_LINEUP, concert));

            assertThat(payload.get("concertId").asLong()).isEqualTo(concert.getId());
            assertThat(payload.get("title").asText()).isEqualTo("WONDERLIVET 2026");
            assertThat(payload.get("titleAliases").get(0).asText()).isEqualTo("원더리벳 2026");
            assertThat(payload.get("startDate").asText()).isEqualTo("2026-11-20");
            assertThat(payload.get("endDate").asText()).isEqualTo("2026-11-22");
            assertThat(payload.get("venueName").asText()).isEqualTo("킨텍스");
        }
    }
}