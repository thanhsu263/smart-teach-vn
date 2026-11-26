package com.smartteach.live.controller;

import com.smartteach.live.dto.JoinRequest;
import com.smartteach.live.dto.JoinResponse;
import com.smartteach.live.dto.LiveAnswerRequest;
import com.smartteach.live.dto.LiveState;
import com.smartteach.live.dto.PlayerView;
import com.smartteach.live.service.LiveService;
import com.smartteach.common.ratelimit.RateLimitRule;
import com.smartteach.common.ratelimit.RateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Public endpoints for players: no account, only a nickname and the token returned by join. */
@RestController
@RequestMapping("/api/public/live/{pin}")
@RequiredArgsConstructor
@Tag(name = "Live quiz (players, public)")
public class LivePlayerController {

    public static final String TOKEN_HEADER = "X-Player-Token";

    private final LiveService liveService;
    private final RateLimiter rateLimiter;

    @GetMapping
    @Operation(summary = "Room state (also pushed to /topic/live/{pin}); never contains the answer of an open question")
    public LiveState state(@PathVariable String pin) {
        return liveService.publicState(pin);
    }

    @PostMapping("/players")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Join the lobby with a nickname (1-20 characters, unique in the room)")
    public JoinResponse join(@PathVariable String pin, @Valid @RequestBody JoinRequest request,
                             HttpServletRequest http) {
        rateLimiter.check(RateLimitRule.LIVE_JOIN, http.getRemoteAddr());
        return liveService.join(pin, request.nickname());
    }

    @GetMapping("/me")
    @Operation(summary = "My nickname, score and whether I answered the current question")
    public PlayerView me(@PathVariable String pin,
                         @RequestHeader(name = TOKEN_HEADER, required = false) String token) {
        return liveService.player(pin, token);
    }

    @PostMapping("/answers")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Answer the open question once; points depend on correctness and speed")
    public void answer(@PathVariable String pin,
                       @RequestHeader(name = TOKEN_HEADER, required = false) String token,
                       @Valid @RequestBody LiveAnswerRequest request) {
        liveService.answer(pin, token, request.position(), request.answer());
    }
}
