package io.github.tuddy.dto;

//Spring -> FastAPI
public record RagChatRequest(String namespace, String query) {}
