package com.smartteach.live.service;

import com.smartteach.common.exception.ApiException;
import com.smartteach.common.security.Hashing;
import com.smartteach.live.dto.JoinResponse;
import com.smartteach.live.dto.LiveState;
import com.smartteach.live.dto.PlayerView;
import com.smartteach.quiz.dto.QuizResponse;
import com.smartteach.quiz.service.QuizService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import jakarta.annotation.PreDestroy;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

/**
 * Live games are short (one lesson) and need fast updates, so they live in memory instead of the
 * database. This works with one backend instance; several instances would need shared state
 * (e.g. Redis) and a STOMP broker relay. After every change the room state is pushed to
 * {@code /topic/live/{pin}}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LiveService implements MeterBinder {

    public static final String TOPIC_PREFIX = "/topic/live/";
    static final int MAX_ROOMS = 200;
    private static final int PIN_ATTEMPTS = 20;

    private final QuizService quizService;
    private final SimpMessagingTemplate messaging;
    private final Clock clock;

    private final Map<String, LiveRoom> rooms = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    /** Closes questions when their time is up. One thread is enough: the work is tiny. */
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();

    /** Spring Boot calls this for every MeterBinder bean; the gauge is read on every scrape. */
    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("smartteach.live.rooms.open", this, LiveService::openRoomCount)
                .description("Live quiz rooms that have not ended")
                .register(registry);
    }

    long openRoomCount() {
        return rooms.values().stream().filter(room -> room.status() != LiveStatus.ENDED).count();
    }

    /** Opens a room for one of the teacher's quizzes. A teacher hosts one game at a time: an older one is ended. */
    public LiveState create(Long ownerId, Long quizId) {
        QuizResponse quiz = quizService.get(ownerId, quizId);
        if (quiz.questions().isEmpty()) {
            throw ApiException.conflict("The quiz has no questions");
        }
        rooms.values().stream()
                .filter(room -> room.ownerId().equals(ownerId) && room.status() != LiveStatus.ENDED)
                .forEach(room -> {
                    room.end(clock.instant());
                    broadcast(room);
                });
        if (rooms.size() >= MAX_ROOMS) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Too many live games right now, try again later");
        }
        for (int i = 0; i < PIN_ATTEMPTS; i++) {
            String pin = "%06d".formatted(random.nextInt(1_000_000));
            LiveRoom room = new LiveRoom(pin, ownerId, quiz.title(), quiz.questions(), clock.instant());
            if (rooms.putIfAbsent(pin, room) == null) {
                return room.state(clock.instant());
            }
        }
        throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Could not find a free PIN, try again");
    }

    public LiveState hostState(Long ownerId, String pin) {
        return ownedRoom(ownerId, pin).state(clock.instant());
    }

    public LiveState next(Long ownerId, String pin) {
        LiveRoom room = ownedRoom(ownerId, pin);
        room.next(clock.instant());
        LiveState state = broadcast(room);
        if (state.status() == LiveStatus.QUESTION) {
            scheduleTimeUp(room, state.question());
        }
        return state;
    }

    public LiveState end(Long ownerId, String pin) {
        LiveRoom room = ownedRoom(ownerId, pin);
        room.end(clock.instant());
        return broadcast(room);
    }

    public LiveState removePlayer(Long ownerId, String pin, int playerId) {
        LiveRoom room = ownedRoom(ownerId, pin);
        room.remove(playerId, clock.instant());
        return broadcast(room);
    }

    public LiveState publicState(String pin) {
        return room(pin).state(clock.instant());
    }

    public JoinResponse join(String pin, String nickname) {
        LiveRoom room = room(pin);
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        LiveRoom.JoinedPlayer player = room.join(nickname, Hashing.sha256Hex(token), clock.instant());
        broadcast(room);
        return new JoinResponse(player.id(), player.nickname(), token);
    }

    public PlayerView player(String pin, String token) {
        return room(pin).playerView(hash(token));
    }

    public void answer(String pin, String token, int position, JsonNode answer) {
        LiveRoom room = room(pin);
        room.answer(hash(token), position, answer, clock.instant());
        broadcast(room);
    }

    @Scheduled(fixedDelay = 60_000)
    void removeExpiredRooms() {
        rooms.values().removeIf(room -> room.isExpired(clock.instant()));
    }

    @PreDestroy
    void stopTimer() {
        timer.shutdownNow();
    }

    private void scheduleTimeUp(LiveRoom room, LiveState.Question question) {
        long delayMs = Math.max(0, Duration.between(clock.instant(), question.endsAt()).toMillis());
        timer.schedule(() -> {
            try {
                if (room.timeUp(question.position(), clock.instant())) {
                    broadcast(room);
                }
            } catch (RuntimeException e) {
                log.error("Closing question {} of live game {} failed", question.position(), room.pin(), e);
            }
        }, delayMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Sends the state after the room's lock is released. Two broadcasts can overtake each other,
     * which is why the state carries a version that clients compare.
     */
    private LiveState broadcast(LiveRoom room) {
        LiveState state = room.state(clock.instant());
        messaging.convertAndSend(TOPIC_PREFIX + room.pin(), state);
        return state;
    }

    private LiveRoom room(String pin) {
        LiveRoom room = pin == null ? null : rooms.get(pin);
        if (room == null) {
            throw ApiException.notFound("Game not found");
        }
        return room;
    }

    /** Another teacher's room is reported as missing, like every other resource. */
    private LiveRoom ownedRoom(Long ownerId, String pin) {
        LiveRoom room = room(pin);
        if (!room.ownerId().equals(ownerId)) {
            throw ApiException.notFound("Game not found");
        }
        return room;
    }

    private static String hash(String token) {
        return token == null || token.isBlank() ? null : Hashing.sha256Hex(token);
    }
}
