package io.github.tuddy.dto;

// Spring Controller -> Client
// 봇의 답변과 함께 세션 ID를 전달하여 프론트가 상태를 유지하게 함
public record ChatProxyResponse(Long sessionId, String answer) {}