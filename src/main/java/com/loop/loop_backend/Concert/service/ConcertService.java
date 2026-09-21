package com.loop.loop_backend.Concert.service;

import com.loop.loop_backend.Concert.dto.ConcertPastDetailDto;
import com.loop.loop_backend.Concert.dto.ConcertPeriod;
import com.loop.loop_backend.Concert.dto.ConcertPeriodDto;
import com.loop.loop_backend.Concert.dto.ConcertRequestDto;
import com.loop.loop_backend.Concert.dto.ConcertResponseDto;
import com.loop.loop_backend.Concert.dto.ConcertSection;
import com.loop.loop_backend.Concert.dto.ConcertSummaryDto;
import com.loop.loop_backend.Concert.dto.ConcertUpcomingDetailDto;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ConcertService {

    // 등록/수정은 관리자 전용(항상 로그인 상태)이라 동행 인원수를 그대로 보여줘도 무방 - ConcertResponseDto 유지.
    ConcertResponseDto createConcert(ConcertRequestDto requestDto, MultipartFile image);
    ConcertResponseDto updateConcert(Long id, ConcertRequestDto requestDto, MultipartFile image);
    void deleteConcert(Long id);

    // 아래 조회 계열은 비로그인(userId == null)으로도 호출되므로 동행 도메인을 타지 않는 ConcertSummaryDto를 쓴다.
    ConcertSummaryDto getConcertById(Long id, Long userId);
    // 딥링크 등 목록을 안 거쳐서 startDate/endDate를 못 들고 있는 진입 경로용 - id만으로 period 판단.
    // 콘서트 날짜와 현재 시각만으로 정해지는 객관적 사실이라 개인화 여지가 없어 userId를 받지 않는다.
    ConcertPeriodDto getPeriod(Long id);
    // 상세조회는 목록/기본조회와 분리 — id가 가리키는 공연의 실제 period(ExpiryCutoff 기준)와
    // 호출한 엔드포인트가 다르면 CONCERT_NOT_FOUND로 처리한다 (클라이언트가 잘못된 엔드포인트를 호출하는 걸 막음).
    ConcertUpcomingDetailDto getUpcomingDetail(Long id, Long userId);
    ConcertPastDetailDto getPastDetail(Long id, Long userId);
    // section 미지정 시 전체 카테고리, period 미지정 시 예정 목록 뒤에 지난 목록을 이어붙여 반환한다.
    List<ConcertSummaryDto> searchConcertsByTitle(String title, ConcertSection section, ConcertPeriod period, Long userId);
    List<ConcertSummaryDto> getConcertsByArtist(Long artistId, Long userId);

    // 공연 탭: section(내한/페스티벌) + period(예정/지난)로 조회. 정렬은 서버가 period 기준으로 고정한다.
    List<ConcertSummaryDto> getConcertsBySection(ConcertSection section, ConcertPeriod period, Long userId);
}
