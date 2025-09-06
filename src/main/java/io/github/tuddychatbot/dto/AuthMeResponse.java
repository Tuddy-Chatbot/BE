package io.github.tuddychatbot.dto;

public record AuthMeResponse(Long id,
							 String displayName,
							 String loginId,
							 String email,
							 String provider,
							 String role) {}
