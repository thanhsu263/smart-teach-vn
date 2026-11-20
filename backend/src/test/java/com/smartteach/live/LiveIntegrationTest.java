package com.smartteach.live;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.smartteach.IntegrationTest;
import java.lang.reflect.Type;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

class LiveIntegrationTest extends IntegrationTest {

    private static final String QUIZ = """
            {"title": "Địa lý 10", "questions": [
              {"kind": "single", "prompt": "Thủ đô của Việt Nam?",
               "options": [{"label": "Hà Nội", "correct": true}, {"label": "Huế", "correct": false}]},
              {"kind": "short_text", "prompt": "Đỉnh núi cao nhất?", "ignoreDiacritics": true,
               "acceptedAnswers": ["Phan Xi Păng"]}
            ]}""";

    @LocalServerPort
    int port;

    @Autowired
    SimpleBrokerMessageHandler broker;

    private final WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());

    @AfterEach
    void stopStompClient() {
        stompClient.stop();
    }

    @Test
    void teacherRunsAGameWithPlayers() throws Exception {
        String teacher = signUpTeacher();
        String pin = createRoom(teacher, createQuiz(teacher));

        // Players join the lobby without an account.
        String an = join(pin, "An");
        mvc.perform(post("/api/public/live/" + pin + "/players").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\": \" an \"}"))
                .andExpect(status().isConflict());
        String binh = join(pin, "Bình");
        mvc.perform(get("/api/public/live/" + pin + "/me").header("X-Player-Token", an))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("An"))
                .andExpect(jsonPath("$.score").value(0));
        mvc.perform(get("/api/public/live/" + pin + "/me")).andExpect(status().isNotFound());
        mvc.perform(get("/api/public/live/" + pin + "/me").header("X-Player-Token", "wrong"))
                .andExpect(status().isNotFound());

        // The teacher removes Bình; the nickname stays taken.
        int binhId = JsonPath.read(mvc.perform(get("/api/public/live/" + pin + "/me").header("X-Player-Token", binh))
                .andReturn().getResponse().getContentAsString(), "$.id");
        perform(teacher, delete("/api/live/" + pin + "/players/" + binhId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.players", hasSize(1)));
        mvc.perform(post("/api/public/live/" + pin + "/players").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\": \"Bình\"}"))
                .andExpect(status().isConflict());

        // Question 1 is open: the public state does not give the answer away.
        perform(teacher, post("/api/live/" + pin + "/next"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("question"))
                .andExpect(jsonPath("$.question.options", hasSize(2)));
        mvc.perform(get("/api/public/live/" + pin))
                .andExpect(jsonPath("$.question.prompt").value("Thủ đô của Việt Nam?"))
                .andExpect(jsonPath("$.question.reveal").value(nullValue()));
        mvc.perform(post("/api/public/live/" + pin + "/players").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\": \"Late\"}"))
                .andExpect(status().isConflict());
        answer(pin, an, "{\"position\": 0, \"answer\": 9}").andExpect(status().isBadRequest());
        answer(pin, an, "{\"position\": 1, \"answer\": 0}").andExpect(status().isConflict());
        answer(pin, an, "{\"answer\": 0}").andExpect(status().isBadRequest());

        // The only player answers → the answer is revealed at once.
        answer(pin, an, "{\"position\": 0, \"answer\": 0}").andExpect(status().isNoContent());
        answer(pin, an, "{\"position\": 0, \"answer\": 0}").andExpect(status().isConflict());
        mvc.perform(get("/api/public/live/" + pin))
                .andExpect(jsonPath("$.status").value("reveal"))
                .andExpect(jsonPath("$.question.reveal.correctOptions[0]").value(0))
                .andExpect(jsonPath("$.players[0].score").value(greaterThan(900)))
                .andExpect(jsonPath("$.players[0].lastPoints").value(greaterThan(900)));

        // Question 2, then the teacher ends the game early.
        perform(teacher, post("/api/live/" + pin + "/next")).andExpect(jsonPath("$.question.position").value(1));
        perform(teacher, post("/api/live/" + pin + "/end"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ended"))
                .andExpect(jsonPath("$.players[0].rank").value(1));
        perform(teacher, post("/api/live/" + pin + "/next")).andExpect(status().isConflict());
    }

    @Test
    void roomsBelongToTheirTeacher() throws Exception {
        String teacher = signUpTeacher();
        String other = signUpTeacher();
        long quizId = createQuiz(teacher);

        perform(other, post("/api/live"), "{\"quizId\": %d}".formatted(quizId)).andExpect(status().isNotFound());
        perform(teacher, post("/api/live"), "{}").andExpect(status().isBadRequest());
        mvc.perform(post("/api/live").contentType(MediaType.APPLICATION_JSON).content("{\"quizId\": 1}"))
                .andExpect(status().isUnauthorized());

        String pin = createRoom(teacher, quizId);
        perform(other, get("/api/live/" + pin)).andExpect(status().isNotFound());
        perform(other, post("/api/live/" + pin + "/next")).andExpect(status().isNotFound());
        perform(other, post("/api/live/" + pin + "/end")).andExpect(status().isNotFound());
        perform(teacher, get("/api/live/" + pin)).andExpect(jsonPath("$.status").value("lobby"));
        perform(teacher, post("/api/live/" + pin + "/next")).andExpect(status().isConflict()); // no player yet
        mvc.perform(get("/api/public/live/000000x")).andExpect(status().isNotFound());

        // A teacher hosts one game at a time.
        String second = createRoom(teacher, quizId);
        assertThat(second).isNotEqualTo(pin);
        mvc.perform(get("/api/public/live/" + pin)).andExpect(jsonPath("$.status").value("ended"));
    }

    @Test
    void statesArePushedOverStompAndClientsCannotSend() throws Exception {
        String teacher = signUpTeacher();
        String pin = createRoom(teacher, createQuiz(teacher));
        stompClient.setMessageConverter(new JacksonJsonMessageConverter());
        String url = "ws://localhost:" + port + "/ws";

        StompSession session = stompClient.connectAsync(url, new StompSessionHandlerAdapter() { })
                .get(5, TimeUnit.SECONDS);
        BlockingQueue<Map<?, ?>> states = new LinkedBlockingQueue<>();
        session.subscribe("/topic/live/" + pin, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                states.add((Map<?, ?>) payload);
            }
        });
        awaitSubscriber("/topic/live/" + pin);

        join(pin, "An");
        Map<?, ?> state = states.poll(5, TimeUnit.SECONDS);
        assertThat(state).isNotNull();
        assertThat(state.get("status")).isEqualTo("lobby");
        assertThat(state.get("serverTime")).asString().matches("\\d{4}-\\d\\d-\\d\\dT.*Z");
        assertThat(state.get("players").toString()).contains("nickname=An");

        // Subscribing to anything else, or sending, closes the connection with an ERROR frame.
        assertThat(errorAfter(url, s -> s.subscribe("/topic/live/" + pin + "/x", new StompSessionHandlerAdapter() { })))
                .contains("Only /topic/live/{pin}");
        assertThat(errorAfter(url, s -> s.send("/topic/live/" + pin, Map.of("status", "ended"))))
                .contains("Sending is not allowed");
    }

    private String errorAfter(String url, Consumer<StompSession> action) throws Exception {
        CompletableFuture<String> error = new CompletableFuture<>();
        StompSession session = stompClient.connectAsync(url, new StompSessionHandlerAdapter() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return byte[].class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                error.complete(headers.getFirst("message"));
            }
        }).get(5, TimeUnit.SECONDS);
        action.accept(session);
        return error.get(5, TimeUnit.SECONDS);
    }

    /** Subscriptions are registered asynchronously; wait until the broker knows about ours. */
    private void awaitSubscriber(String destination) throws InterruptedException {
        SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        headers.setDestination(destination);
        var probe = MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (broker.getSubscriptionRegistry().findSubscriptions(probe).isEmpty()) {
            assertThat(System.nanoTime()).as("subscription registered").isLessThan(deadline);
            Thread.sleep(20);
        }
    }

    private long createQuiz(String teacher) throws Exception {
        String json = perform(teacher, post("/api/quizzes"), QUIZ)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    private String createRoom(String teacher, long quizId) throws Exception {
        String json = perform(teacher, post("/api/live"), "{\"quizId\": %d}".formatted(quizId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("lobby"))
                .andExpect(jsonPath("$.questionCount").value(2))
                .andReturn().getResponse().getContentAsString();
        String pin = JsonPath.read(json, "$.pin");
        assertThat(pin).matches("\\d{6}");
        return pin;
    }

    private String join(String pin, String nickname) throws Exception {
        String json = mvc.perform(post("/api/public/live/" + pin + "/players").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\": \"%s\"}".formatted(nickname)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nickname").value(nickname))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.token");
    }

    private ResultActions answer(String pin, String token, String body)
            throws Exception {
        return mvc.perform(post("/api/public/live/" + pin + "/answers").header("X-Player-Token", token)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
