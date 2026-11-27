package com.smartteach.user.controller;

import com.smartteach.common.security.CurrentUser;
import com.smartteach.user.dto.SettingsRequest;
import com.smartteach.user.dto.UserResponse;
import com.smartteach.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class MeController {

    private final UserService userService;

    @GetMapping
    @Operation(summary = "Current signed-in user")
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return userService.getById(CurrentUser.id(jwt));
    }

    @PutMapping("/settings")
    @Operation(summary = "Change account settings",
            description = "emailNotifications=false stops assignment reminder and summary emails. "
                    + "Password reset emails are always sent.")
    public UserResponse updateSettings(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody SettingsRequest request) {
        return userService.updateSettings(CurrentUser.id(jwt), request);
    }
}
