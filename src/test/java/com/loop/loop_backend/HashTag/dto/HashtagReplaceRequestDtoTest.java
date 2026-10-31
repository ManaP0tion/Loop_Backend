package com.loop.loop_backend.HashTag.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// 해시태그 목록 교체 요청 요구사항:
// - 목록은 필수다([]는 허용 - 모두 지우기).
// - 각 태그는 기존 추가 API와 같은 규칙: 5자 이하, 한글·영문·숫자만, 앞뒤 공백은 지운다.
class HashtagReplaceRequestDtoTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    private final ObjectMapper mapper = new ObjectMapper();

    private HashtagReplaceRequestDto parse(String json) throws Exception {
        return mapper.readValue(json, HashtagReplaceRequestDto.class);
    }

    private boolean valid(String json) throws Exception {
        return validator.validate(parse(json)).isEmpty();
    }

    @Test
    @DisplayName("빈 목록은 되고 목록이 없으면 안 된다")
    void emptyListIsValid_MissingListIsNot() throws Exception {
        assertThat(valid("{\"tags\": []}")).isTrue();
        assertThat(valid("{}")).isFalse();
    }

    @Test
    @DisplayName("태그는 5자까지 되고 6자는 안 된다")
    void tagUpTo5Chars() throws Exception {
        assertThat(valid("{\"tags\": [\"수다쟁이다\"]}")).isTrue();
        assertThat(valid("{\"tags\": [\"수다쟁이였다\"]}")).isFalse();
    }

    @Test
    @DisplayName("특수문자·빈 값·null 태그는 안 된다")
    void invalidTags_AreRejected() throws Exception {
        assertThat(valid("{\"tags\": [\"낯가림!\"]}")).isFalse();
        assertThat(valid("{\"tags\": [\"  \"]}")).isFalse();
        assertThat(valid("{\"tags\": [null]}")).isFalse();
    }

    @Test
    @DisplayName("태그 앞뒤 공백은 지우고 받는다")
    void tagsAreStripped() throws Exception {
        HashtagReplaceRequestDto dto = parse("{\"tags\": [\" 낯가림 \"]}");

        assertThat(dto.getTags()).containsExactly("낯가림");
        assertThat(validator.validate(dto)).isEmpty();
    }
}