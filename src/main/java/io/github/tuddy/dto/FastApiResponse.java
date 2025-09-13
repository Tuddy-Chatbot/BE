package io.github.tuddy.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// LLM의 api 응답에 'response' 외 다른 필드가 있어도 무시
@JsonIgnoreProperties(ignoreUnknown = true)
public record FastApiResponse(String response) {}