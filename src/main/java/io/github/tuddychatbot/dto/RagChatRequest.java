package io.github.tuddychatbot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;


// Spring -> FastAPI
public record RagChatRequest(@JsonProperty("user_id") String userId,
                             String query) {}
