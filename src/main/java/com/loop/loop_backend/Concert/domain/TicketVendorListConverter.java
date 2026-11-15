package com.loop.loop_backend.Concert.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.List;

// 예매처 목록(List<TicketVendorInfo>) <-> JSON 문자열 변환기.
// 예매처로 공연을 검색/필터링할 일이 없어서 별도 테이블로 나누지 않고 컬럼 하나에 통째로 저장한다.
@Converter
public class TicketVendorListConverter implements AttributeConverter<List<TicketVendorInfo>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<TicketVendorInfo>> TYPE = new TypeReference<>() {};

    @Override
    public String convertToDatabaseColumn(List<TicketVendorInfo> vendors) {
        // null이면 컬럼도 null - 승인 시 예매처 조회에 실패했거나 예매처가 없는 경우
        if (vendors == null) return null;
        try {
            return MAPPER.writeValueAsString(vendors);
        } catch (JsonProcessingException e) {
            // 저장 실패를 조용히 삼키면 예매처가 빈 채로 저장되므로 예외로 알린다
            throw new IllegalStateException("예매처 목록 직렬화 실패", e);
        }
    }

    @Override
    public List<TicketVendorInfo> convertToEntityAttribute(String json) {
        // 이 컬럼이 생기기 전에 만들어진 기존 콘서트 행은 null/빈 값
        if (json == null || json.isBlank()) return null;
        try {
            return MAPPER.readValue(json, TYPE);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("예매처 목록 역직렬화 실패", e);
        }
    }
}