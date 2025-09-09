package io.github.tuddy.dto;

import jakarta.validation.constraints.NotBlank;

//Client -> Spring Controller
public record ChatProxyRequest(@NotBlank String query) {}
