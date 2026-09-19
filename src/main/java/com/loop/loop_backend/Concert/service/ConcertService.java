package com.loop.loop_backend.Concert.service;

import com.loop.loop_backend.Concert.dto.ConcertPeriod;
import com.loop.loop_backend.Concert.dto.ConcertRequestDto;
import com.loop.loop_backend.Concert.dto.ConcertResponseDto;
import com.loop.loop_backend.Concert.dto.ConcertSection;
import com.loop.loop_backend.Concert.dto.ConcertSummaryDto;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ConcertService {

    // 등록/수정은 관리자 전용(항상 로그인 상태)이라 동행 인원수를 그대로 보여줘도 무방 - ConcertResponseDto 유지.
    ConcertResponseDto createConcert(ConcertRequestDto requestDto, MultipartFile image);
    ConcertResponseDto updateConcert(Long id, ConcertRequestDto requestDto, MultipartFile image);
    void deleteConcert(Long id);

    // 아래 조회 계열은 비로그인(userId == null)으로도 호출되므로 동행 도메인을 타지 않는 ConcertSummaryDto를 쓴다.
    ConcertSummaryDto getConcertById(Long id, Long userId);
    // section 미지정 시 전체 카테고리, period 미지정 시 예정 목록 뒤에 지난 목록을 이어붙여 반환한다.
    List<ConcertSummaryDto> searchConcertsByTitle(String title, ConcertSection section, ConcertPeriod period, Long userId);
    List<ConcertSummaryDto> getConcertsByArtist(Long artistId, Long userId);

    // 공연 탭: section(내한/페스티벌) + period(예정/지난)로 조회. 정렬은 서버가 period 기준으로 고정한다.
    List<ConcertSummaryDto> getConcertsBySection(ConcertSection section, ConcertPeriod period, Long userId);
}
