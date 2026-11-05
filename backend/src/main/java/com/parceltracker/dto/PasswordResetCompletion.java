package com.parceltracker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetCompletion(
        @NotBlank String token,
        @NotBlank @Size(min = 12, max = 72, message = "Password must be 12 to 72 characters") String password) {
}
