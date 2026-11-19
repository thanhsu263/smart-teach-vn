package com.smartteach.common.security;

import org.springframework.security.oauth2.jwt.Jwt;

/** The access token's subject is the user id. */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static Long id(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
