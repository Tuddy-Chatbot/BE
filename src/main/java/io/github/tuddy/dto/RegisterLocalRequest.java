package io.github.tuddy.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterLocalRequest(
  @NotBlank @Size(max=60) String displayName,
  @NotBlank @Size(max=50) String loginId,
  @Email @NotBlank String email,
  @NotBlank @Size(min=8, max=100) String password,
  @NotBlank String passwordConfirm
) {}
