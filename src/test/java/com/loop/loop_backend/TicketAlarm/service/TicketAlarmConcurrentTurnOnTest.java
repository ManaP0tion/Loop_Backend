package com.loop.loop_backend.TicketAlarm.service;

import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.Concert.domain.ConcertCategory;
import com.loop.loop_backend.Concert.repository.ConcertRepository;
import com.loop.loop_backend.Concert.repository.ticket.ConcertGeneralSaleRepository;
import com.loop.loop_backend.Concert.repository.ticket.ConcertPresaleRepository;
import com.loop.loop_backend.TicketAlarm.domain.TicketAlarm;
import com.loop.loop_backend.TicketAlarm.domain.TicketAlarmType;
import com.loop.loop_backend.TicketAlarm.dto.TicketAlarmResponse;
import com.loop.loop_backend.TicketAlarm.repository.TicketAlarmRepository;
import com.loop.loop_backend.User.domain.AuthProvider;
import com.loop.loop_backend.User.domain.Status;
import com.loop.loop_backend.User.domain.User;
import com.loop.loop_backend.User.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

// 요구사항: 켜기는 토글이라 같은 요청이 동시에 두 번 와도(더블 클릭) 에러 없이 켜진 상태를 돌려준다.
// 두 요청이 모두 '아직 꺼짐'을 보고 저장하면 늦은 쪽이 유니크 제약에 걸리는 상황을 그대로 만든다.
@ExtendWith(MockitoExtension.class)
class TicketAlarmConcurrentTurnOnTest {

    private static final Long USER_ID = 1L;
    private static final Long CONCERT_ID = 10L;

    @Mock TicketAlarmRepository ticketAlarmRepository;
    @Mock ConcertRepository concertRepository;
    @Mock ConcertPresaleRepository presaleRepository;
    @Mock ConcertGeneralSaleRepository generalSaleRepository;
    @Mock UserRepository userRepository;
    @InjectMocks TicketAlarmService service;

    @Test
    void 동시에_들어온_켜기_요청이_먼저_저장된_뒤라도_켜진_상태를_돌려준다() {
        Concert concert = Concert.builder().title("YUURI LIVE").category(ConcertCategory.J_POP_ARTIST).build();
        concert.changePublished(true, LocalDateTime.now());
        User user = User.builder().authProvider(AuthProvider.KAKAO).providerId("me").status(Status.ACTIVE).build();
        when(concertRepository.findById(CONCERT_ID)).thenReturn(Optional.of(concert));
        when(presaleRepository.existsByConcert_IdAndOpensAtIsNotNull(CONCERT_ID)).thenReturn(true);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        // 확인 시점엔 아직 꺼짐 → 저장하려는 사이 다른 요청이 먼저 켰다
        when(ticketAlarmRepository.existsByUser_IdAndConcert_IdAndType(USER_ID, CONCERT_ID, TicketAlarmType.PRESALE))
                .thenReturn(false);
        when(ticketAlarmRepository.saveAndFlush(any(TicketAlarm.class)))
                .thenThrow(new DataIntegrityViolationException("uq_ticket_alarm_user_concert_type"));
        when(ticketAlarmRepository.findTypesByUserIdAndConcertId(USER_ID, CONCERT_ID))
                .thenReturn(List.of(TicketAlarmType.PRESALE));

        TicketAlarmResponse status = service.turnOn(USER_ID, CONCERT_ID, TicketAlarmType.PRESALE);

        assertThat(status).isEqualTo(new TicketAlarmResponse(true, false));
    }
}