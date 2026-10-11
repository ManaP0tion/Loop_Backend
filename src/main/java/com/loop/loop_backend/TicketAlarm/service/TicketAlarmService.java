package com.loop.loop_backend.TicketAlarm.service;

import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.Concert.repository.ConcertRepository;
import com.loop.loop_backend.Concert.repository.ticket.ConcertGeneralSaleRepository;
import com.loop.loop_backend.Concert.repository.ticket.ConcertPresaleRepository;
import com.loop.loop_backend.TicketAlarm.domain.TicketAlarm;
import com.loop.loop_backend.TicketAlarm.domain.TicketAlarmType;
import com.loop.loop_backend.TicketAlarm.dto.TicketAlarmResponse;
import com.loop.loop_backend.TicketAlarm.repository.TicketAlarmRepository;
import com.loop.loop_backend.User.domain.User;
import com.loop.loop_backend.User.repository.UserRepository;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Set;

/**
 * 공연 예매 알림 토글. 설정 저장·조회까지만 하고, 실제 발송은 별도 작업이다.
 * 토글 UX라 이미 켜진 것을 켜거나 꺼진 것을 꺼도 에러 없이 현재 상태를 돌려준다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TicketAlarmService {

    private final TicketAlarmRepository ticketAlarmRepository;
    private final ConcertRepository concertRepository;
    private final ConcertPresaleRepository presaleRepository;
    private final ConcertGeneralSaleRepository generalSaleRepository;
    private final UserRepository userRepository;

    /**
     * 켜기. 비공개 공연은 상세에 들어올 수 없으니 403.
     * 그 유형에 예매 일시가 정해진 일정이 없으면 400 - 일시 없는 블록은 사용자 화면에 보이지 않아 일정으로 치지 않는다.
     *
     * 더블 클릭으로 같은 켜기 요청이 동시에 오면 둘 다 '없음'을 보고 저장해 두 번째가 유니크 제약에 걸린다.
     * 이미 켜진 것이니 실패로 보지 않고 현재 상태를 돌려준다. 이를 위해 메서드 전체를 한 트랜잭션으로 묶지 않는다(SUPPORTS) -
     * 한 트랜잭션 안에서 제약 위반이 나면 그 트랜잭션은 롤백 전용이 되어 예외를 잡아도 응답을 만들 수 없다.
     * 저장(saveAndFlush)은 자기 트랜잭션에서 끝나므로 위반이 나도 그 저장만 실패한다.
     */
    @Transactional(propagation = Propagation.SUPPORTS)
    public TicketAlarmResponse turnOn(Long userId, Long concertId, TicketAlarmType type) {
        Concert concert = findConcert(concertId);
        if (!concert.isPublished()) {
            throw new BusinessException(ErrorCode.CONCERT_NOT_OPEN);
        }
        if (!hasSchedule(concertId, type)) {
            throw new BusinessException(ErrorCode.TICKET_SCHEDULE_NOT_FOUND);
        }
        if (!ticketAlarmRepository.existsByUser_IdAndConcert_IdAndType(userId, concertId, type)) {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
            try {
                ticketAlarmRepository.saveAndFlush(TicketAlarm.builder().user(user).concert(concert).type(type).build());
            } catch (DataIntegrityViolationException alreadyOn) {
                // 동시에 들어온 다른 요청이 먼저 켰다 - 결과는 같으니 그대로 진행
            }
        }
        return status(userId, concertId);
    }

    /** 끄기. 일정이 지워졌거나 비공개로 바뀐 뒤에도 끌 수 있어야 해서 공연 존재만 확인한다. */
    @Transactional
    public TicketAlarmResponse turnOff(Long userId, Long concertId, TicketAlarmType type) {
        findConcert(concertId);
        ticketAlarmRepository.deleteByUser_IdAndConcert_IdAndType(userId, concertId, type);
        return status(userId, concertId);
    }

    /**
     * 공연 상세·토글 응답용: 켜져 있는 알림 종류. 비로그인이면 빈 집합.
     * 켜 둔 뒤 관리자가 그 유형의 예매 일정을 지웠으면 꺼짐으로 보인다(일정이 없으면 토글도 비활성이라).
     * 설정 행은 남겨 두므로 일정이 다시 잡히면 사용자가 다시 켜지 않아도 켜짐으로 돌아온다.
     * 발송 대상도 같은 규칙(켜짐 + 일정 있음)으로 고르면 된다.
     */
    public Set<TicketAlarmType> typesOf(Long userId, Long concertId) {
        if (userId == null) return Set.of();
        Set<TicketAlarmType> types = EnumSet.noneOf(TicketAlarmType.class);
        for (TicketAlarmType type : ticketAlarmRepository.findTypesByUserIdAndConcertId(userId, concertId)) {
            if (hasSchedule(concertId, type)) types.add(type);
        }
        return types;
    }

    private TicketAlarmResponse status(Long userId, Long concertId) {
        return TicketAlarmResponse.of(typesOf(userId, concertId));
    }

    private boolean hasSchedule(Long concertId, TicketAlarmType type) {
        return switch (type) {
            case PRESALE -> presaleRepository.existsByConcert_IdAndOpensAtIsNotNull(concertId);
            case GENERAL_SALE -> generalSaleRepository.existsByConcert_IdAndOpensAtIsNotNull(concertId);
        };
    }

    private Concert findConcert(Long concertId) {
        return concertRepository.findById(concertId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONCERT_NOT_FOUND));
    }
}