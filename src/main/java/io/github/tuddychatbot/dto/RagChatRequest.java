package io.github.tuddychatbot.dto;

//Spring -> FastAPI
public record RagChatRequest(String namespace, String query) {}
