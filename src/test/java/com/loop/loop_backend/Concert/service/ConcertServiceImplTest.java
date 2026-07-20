package com.loop.loop_backend.Concert.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loop.loop_backend.Artist.repository.ArtistRepository;
import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.Concert.dto.ConcertRequestDto;
import com.loop.loop_backend.Concert.dto.ConcertResponseDto;
import com.loop.loop_backend.Concert.repository.ConcertRepository;
import com.loop.loop_backend.CompanionPost.repository.CompanionPostRepository;
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

    @Test
    void 콘서트_단건_조회시_등록된_동행프로필_수가_함께_반환된다() {
        Concert existing = Concert.builder().title("기존 제목").build();
        ReflectionTestUtils.setField(existing, "id", 20L);
        when(concertRepository.findById(20L)).thenReturn(Optional.of(existing));
        when(companionPostRepository.countByConcert_Id(20L)).thenReturn(3L);

        ConcertResponseDto result = concertService.getConcertById(20L);

        assertThat(result.getCompanionCount()).isEqualTo(3L);
    }

    @Test
    void 콘서트_전체_목록조회시_각_콘서트별_동행프로필_수가_함께_반환된다() {
        Concert concert1 = Concert.builder().title("콘서트1").build();
        ReflectionTestUtils.setField(concert1, "id", 1L);
        Concert concert2 = Concert.builder().title("콘서트2").build();
        ReflectionTestUtils.setField(concert2, "id", 2L);

        when(concertRepository.findUpcomingOrUndated(any())).thenReturn(List.of(concert1, concert2));
        when(companionPostRepository.countByConcert_Id(1L)).thenReturn(5L);
        when(companionPostRepository.countByConcert_Id(2L)).thenReturn(0L);

        List<ConcertResponseDto> result = concertService.getAllConcerts();

        assertThat(result).extracting(ConcertResponseDto::getCompanionCount).containsExactly(5L, 0L);
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

