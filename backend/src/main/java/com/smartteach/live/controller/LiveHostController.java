package com.smartteach.live.controller;

import com.smartteach.common.security.CurrentUser;
import com.smartteach.live.dto.CreateLiveRequest;
import com.smartteach.live.dto.LiveState;
import com.smartteach.live.service.LiveService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The teacher runs the game with REST calls; every change is also pushed over STOMP. */
@RestController
@RequestMapping("/api/live")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Live quiz (host)")
public class LiveHostController {

    private final LiveService liveService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Open a room with a 6-digit PIN for one of my quizzes (ends my previous room)")
    public LiveState create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateLiveRequest request) {
        return liveService.create(CurrentUser.id(jwt), request.quizId());
    }

    @GetMapping("/{pin}")
    @Operation(summary = "Current state of my room")
    public LiveState get(@AuthenticationPrincipal Jwt jwt, @PathVariable String pin) {
        return liveService.hostState(CurrentUser.id(jwt), pin);
    }

    @PostMapping("/{pin}/next")
    @Operation(summary = "Start the game, show the answer now, go to the next question, or finish after the last one")
    public LiveState next(@AuthenticationPrincipal Jwt jwt, @PathVariable String pin) {
        return liveService.next(CurrentUser.id(jwt), pin);
    }

    @PostMapping("/{pin}/end")
    @Operation(summary = "End the game now")
    public LiveState end(@AuthenticationPrincipal Jwt jwt, @PathVariable String pin) {
        return liveService.end(CurrentUser.id(jwt), pin);
    }

    @DeleteMapping("/{pin}/players/{playerId}")
    @Operation(summary = "Remove a player; the nickname stays blocked in this room")
    public LiveState removePlayer(@AuthenticationPrincipal Jwt jwt, @PathVariable String pin,
                                  @PathVariable int playerId) {
        return liveService.removePlayer(CurrentUser.id(jwt), pin, playerId);
    }
}
