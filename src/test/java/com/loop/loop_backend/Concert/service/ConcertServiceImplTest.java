package com.loop.loop_backend.Concert.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loop.loop_backend.Artist.repository.ArtistRepository;
import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.Concert.domain.ConcertCategory;
import com.loop.loop_backend.Concert.dto.ConcertPeriod;
import com.loop.loop_backend.Concert.dto.ConcertRequestDto;
import com.loop.loop_backend.Concert.dto.ConcertResponseDto;
import com.loop.loop_backend.Concert.dto.ConcertSection;
import com.loop.loop_backend.Concert.dto.ConcertSummaryDto;
import com.loop.loop_backend.Concert.repository.ConcertRepository;
import com.loop.loop_backend.CompanionPost.repository.CompanionPostRepository;
import com.loop.loop_backend.CompanionPost.service.CompanionService;
import com.loop.loop_backend.Storage.service.S3StorageService;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)

class ConcertServiceImplTest {

    @Mock ConcertRepository concertRepository;
    @Mock ArtistRepository artistRepository;
    @Mock CompanionPostRepository companionPostRepository;
    @Mock CompanionService companionService;
    @Mock S3StorageService s3StorageService;
    @InjectMocks ConcertServiceImpl concertService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ConcertRequestDto requestDto(String title) throws Exception {
        String json = """
                {
                    "title": "%s",
                    "category": "DOMESTIC_ARTIST"
                }
                """.formatted(title);
        return objectMapper.readValue(json, ConcertRequestDto.class);
    }

    private MultipartFile fakeImage() {
        return new MockMultipartFile("image", "poster.png", "image/png", "fake-image-bytes".getBytes());
    }

    // save() 호출 시 실제 IDENTITY 채번을 흉내내서 concert에 id를 부여
    private void stubSaveAssignsId(long id) {
        when(concertRepository.save(any(Concert.class))).thenAnswer(invocation -> {
            Concert concert = invocation.getArgument(0);
            ReflectionTestUtils.setField(concert, "id", id);
            return concert;
        });
    }

    @Test
    void 등록_시_이미지가_있으면_S3에_업로드된_URL이_저장된다() throws Exception {
        stubSaveAssignsId(1L);
        MultipartFile image = fakeImage();
        when(s3StorageService.uploadPublic("concerts", 1L, image)).thenReturn("https://cdn/concerts/1/poster.png");

        ConcertResponseDto result = concertService.createConcert(requestDto("아이유 콘서트"), image);

        assertThat(result.getPosterUrl()).isEqualTo("https://cdn/concerts/1/poster.png");
        verify(s3StorageService).uploadPublic("concerts", 1L, image);
    }

    @Test
    void 등록_시_이미지가_없으면_S3를_호출하지_않고_posterUrl은_null이다() throws Exception {
        stubSaveAssignsId(2L);

        ConcertResponseDto result = concertService.createConcert(requestDto("무이미지 콘서트"), null);

        assertThat(result.getPosterUrl()).isNull();
        verifyNoInteractions(s3StorageService);
    }

    @Test
    void 수정_시_새_이미지가_없으면_기존_posterUrl이_유지된다() throws Exception {
        Concert existing = Concert.builder().title("기존 제목").posterUrl("https://cdn/old.png").build();
        ReflectionTestUtils.setField(existing, "id", 10L);
        when(concertRepository.findById(10L)).thenReturn(Optional.of(existing));

        ConcertResponseDto result = concertService.updateConcert(10L, requestDto("제목만 수정"), null);

        assertThat(result.getPosterUrl()).isEqualTo("https://cdn/old.png");
        verifyNoInteractions(s3StorageService);
    }

    @Test
    void 수정_시_새_이미지가_있으면_기존_이미지를_교체한다() throws Exception {
        Concert existing = Concert.builder().title("기존 제목").posterUrl("https://cdn/old.png").build();
        ReflectionTestUtils.setField(existing, "id", 10L);
        when(concertRepository.findById(10L)).thenReturn(Optional.of(existing));

        MultipartFile newImage = fakeImage();
        when(s3StorageService.uploadPublic("concerts", 10L, newImage)).thenReturn("https://cdn/new.png");

        ConcertResponseDto result = concertService.updateConcert(10L, requestDto("이미지 교체"), newImage);

        assertThat(result.getPosterUrl()).isEqualTo("https://cdn/new.png");
    }

    // companionCount가 ConcertSummaryDto로 옮겨가며 빠졌다 - 콘서트 단건 조회는 이제
    // 동행 도메인과 무관하게 콘서트 정보만 반환하고, 비로그인(userId == null)이어도 동작해야 한다.
    @Test
    void 콘서트_단건_조회시_id로_찾은_콘서트_정보가_반환된다() {
        Concert existing = Concert.builder().title("아이유 콘서트").build();
        ReflectionTestUtils.setField(existing, "id", 20L);
        when(concertRepository.findById(20L)).thenReturn(Optional.of(existing));

        ConcertSummaryDto result = concertService.getConcertById(20L, 100L);

        assertThat(result.getConcertId()).isEqualTo(20L);
        assertThat(result.getTitle()).isEqualTo("아이유 콘서트");
    }

    @Test
    void 콘서트_단건_조회는_비로그인_조회자여도_동행_도메인을_거치지_않고_동작한다() {
        Concert existing = Concert.builder().title("아이유 콘서트").build();
        ReflectionTestUtils.setField(existing, "id", 20L);
        when(concertRepository.findById(20L)).thenReturn(Optional.of(existing));

        ConcertSummaryDto result = concertService.getConcertById(20L, null);

        assertThat(result.getConcertId()).isEqualTo(20L);
        verifyNoInteractions(companionService);
    }

    @Test
    void 존재하지_않는_콘서트_단건_조회시_예외가_발생한다() {
        when(concertRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> concertService.getConcertById(999L, 100L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CONCERT_NOT_FOUND);
    }

    // ===== searchConcertsByTitle (검색 스코프) =====

    private Concert concertNamed(long id, String title) {
        Concert concert = Concert.builder().title(title).build();
        ReflectionTestUtils.setField(concert, "id", id);
        return concert;
    }

    @Test
    void 검색시_section을_지정하지_않으면_전체_카테고리로_조회한다() {
        when(concertRepository.searchUpcomingOrUndatedByTitleAndCategories(
                eq("공연"), eq(List.of(ConcertCategory.values())), any()))
                .thenReturn(List.of(concertNamed(1L, "전체 카테고리 결과")));
        when(concertRepository.searchPastByTitleAndCategories(any(), any(), any()))
                .thenReturn(List.of());

        List<ConcertSummaryDto> result = concertService.searchConcertsByTitle("공연", null, ConcertPeriod.UPCOMING, 100L);

        assertThat(result).extracting(ConcertSummaryDto::getTitle).containsExactly("전체 카테고리 결과");
    }

    @Test
    void 검색시_section을_지정하면_해당_섹션의_카테고리로만_조회한다() {
        when(concertRepository.searchUpcomingOrUndatedByTitleAndCategories(
                eq("공연"), eq(ConcertSection.FESTIVAL.getCategories()), any()))
                .thenReturn(List.of(concertNamed(1L, "페스티벌 결과")));

        List<ConcertSummaryDto> result = concertService.searchConcertsByTitle(
                "공연", ConcertSection.FESTIVAL, ConcertPeriod.UPCOMING, 100L);

        assertThat(result).extracting(ConcertSummaryDto::getTitle).containsExactly("페스티벌 결과");
    }

    @Test
    void 검색시_period가_PAST면_지난_공연만_조회한다() {
        when(concertRepository.searchPastByTitleAndCategories(eq("공연"), any(), any()))
                .thenReturn(List.of(concertNamed(1L, "지난 공연 결과")));

        List<ConcertSummaryDto> result = concertService.searchConcertsByTitle(
                "공연", ConcertSection.DOMESTIC_TOUR, ConcertPeriod.PAST, 100L);

        assertThat(result).extracting(ConcertSummaryDto::getTitle).containsExactly("지난 공연 결과");
        verify(concertRepository, never()).searchUpcomingOrUndatedByTitleAndCategories(any(), any(), any());
    }

    @Test
    void 검색시_period를_지정하지_않으면_전체검색으로_예정_목록_뒤에_지난_목록을_이어붙인다() {
        when(concertRepository.searchUpcomingOrUndatedByTitleAndCategories(eq("공연"), any(), any()))
                .thenReturn(List.of(concertNamed(1L, "예정1"), concertNamed(2L, "예정2")));
        when(concertRepository.searchPastByTitleAndCategories(eq("공연"), any(), any()))
                .thenReturn(List.of(concertNamed(3L, "지난1")));

        List<ConcertSummaryDto> result = concertService.searchConcertsByTitle("공연", null, null, 100L);

        assertThat(result).extracting(ConcertSummaryDto::getTitle)
                .containsExactly("예정1", "예정2", "지난1");
    }

    @Test
    void 존재하지_않는_콘서트_수정_시_예외가_발생하고_S3는_호출되지_않는다() throws Exception {
        when(concertRepository.findById(999L)).thenReturn(Optional.empty());
        MultipartFile image = fakeImage();

        assertThatThrownBy(() -> concertService.updateConcert(999L, requestDto("없는 콘서트"), image))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CONCERT_NOT_FOUND);

        verify(s3StorageService, never()).uploadPublic(any(), any(), any());
    }
}

