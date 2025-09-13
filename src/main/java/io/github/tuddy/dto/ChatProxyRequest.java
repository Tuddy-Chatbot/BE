package io.github.tuddy.dto;

import jakarta.validation.constraints.NotBlank;

// Client -> Spring Controller
// 세션 ID를 받아야 하므로 필드 추가 (새로운 채팅일 경우 null 또는 0)
public record ChatProxyRequest(Long sessionId, @NotBlank String query) {}