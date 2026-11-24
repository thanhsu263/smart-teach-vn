package com.smartteach.admin.controller;

import com.smartteach.admin.dto.KeepAliveRequest;
import com.smartteach.admin.dto.KeepAliveStatus;
import com.smartteach.admin.service.KeepAliveService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin", description = "ADMIN role only")
public class AdminController {

    private final KeepAliveService keepAliveService;

    @GetMapping("/keep-alive")
    @Operation(summary = "Keep-alive switch and the result of the last ping")
    public KeepAliveStatus keepAlive() {
        return keepAliveService.status();
    }

    @PutMapping("/keep-alive")
    @Operation(summary = "Turn the keep-alive pinger on or off")
    public KeepAliveStatus setKeepAlive(@Valid @RequestBody KeepAliveRequest request) {
        return keepAliveService.setEnabled(request.enabled());
    }
}
