package io.github.tuddychatbot.dto;

import jakarta.validation.constraints.NotBlank;


// Client -> Spring Controller
public record ChatProxyRequest(@NotBlank String userId,
                               @NotBlank String query) {}
