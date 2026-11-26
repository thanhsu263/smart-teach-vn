package com.smartteach.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code token} is the value from the email link; the password rules match sign-up. */
public record ResetPasswordRequest(
        @NotBlank @Size(max = 100) String token,
        @NotBlank @Size(min = 8, max = 72) String newPassword) {
}
