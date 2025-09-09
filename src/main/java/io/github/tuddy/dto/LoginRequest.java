package io.github.tuddy.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
  @NotBlank String loginIdOrEmail,
  @NotBlank String password
) {}
