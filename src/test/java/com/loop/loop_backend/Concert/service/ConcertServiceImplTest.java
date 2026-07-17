package com.loop.loop_backend.Concert.service;

import com.loop.loop_backend.Artist.repository.ArtistRepository;
import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.Concert.domain.ConcertCategory;
import com.loop.loop_backend.Concert.dto.ConcertResponseDto;
import com.loop.loop_backend.Concert.repository.ConcertRepository;
import com.loop.loop_backend.CompanionPost.repository.CompanionPostRepository;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConcertServiceImplTest {

    @Mock ConcertRepository concertRepository;
    @Mock ArtistRepository artistRepository;
    @Mock CompanionPostRepository companionPostRepository;
    @InjectMocks ConcertServiceImpl concertService;

    private Concert concert(Long id) {
        Concert concert = Concert.builder()
                .title("Test Concert")
                .category(ConcertCategory.J_POP_ARTIST)
                .build();
        ReflectionTestUtils.setField(concert, "id", id);
        return concert;
    }

    @Test
    void 단건_조회시_동행_수가_포함된다() {
        Long concertId = 10L;
        when(concertRepository.findById(concertId)).thenReturn(Optional.of(concert(concertId)));
        when(companionPostRepository.countByConcertId(concertId)).thenReturn(3L);

        ConcertResponseDto response = concertService.getConcertById(concertId);

        assertThat(response.getId()).isEqualTo(concertId);
        assertThat(response.getCompanionCount()).isEqualTo(3L);
        verify(companionPostRepository).countByConcertId(concertId);
    }

    @Test
    void 등록된_동행이_없으면_0이_반환된다() {
        Long concertId = 11L;
        when(concertRepository.findById(concertId)).thenReturn(Optional.of(concert(concertId)));
        when(companionPostRepository.countByConcertId(concertId)).thenReturn(0L);

        ConcertResponseDto response = concertService.getConcertById(concertId);

        assertThat(response.getCompanionCount()).isEqualTo(0L);
    }

    @Test
    void 존재하지_않는_콘서트_조회시_예외가_발생한다() {
        Long concertId = 999L;
        when(concertRepository.findById(concertId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> concertService.getConcertById(concertId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CONCERT_NOT_FOUND);
    }
}
