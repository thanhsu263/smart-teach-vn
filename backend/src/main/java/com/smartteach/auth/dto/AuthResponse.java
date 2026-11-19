package com.smartteach.auth.dto;

import com.smartteach.user.dto.UserResponse;

/** The refresh token is not in the body; it is sent as an HttpOnly cookie. */
public record AuthResponse(String accessToken, String tokenType, long expiresIn, UserResponse user) {

    public AuthResponse(String accessToken, long expiresIn, UserResponse user) {
        this(accessToken, "Bearer", expiresIn, user);
    }
}
