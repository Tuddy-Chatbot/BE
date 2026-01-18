package io.github.tuddy.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
// Spring -> FastAPI
// Request Body에 맞게 필드 정의
public record FastApiChatRequest(
    @JsonProperty("user_id") String userId,
    @JsonProperty("session_id") String sessionId,
    String query,
    @JsonProperty("n_turns") int nTurns
) {}