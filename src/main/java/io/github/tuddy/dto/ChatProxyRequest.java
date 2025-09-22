package io.github.tuddy.dto;

import jakarta.validation.constraints.NotBlank;

// Client -> Spring Controller
// 세션 ID와 함께, 선택적으로 fileId를 받아 새로운 채팅을 파일과 연결
public record ChatProxyRequest(
    Long sessionId,
    @NotBlank String query,
    Long fileId
) {}