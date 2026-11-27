package smartteach;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Prepares the data a simulation needs (teacher, quiz, classes, published assignments) with plain
 * HTTP calls before the load starts, so only the measured requests show up in the report.
 */
final class Api {

    static final String BASE_URL = System.getProperty("baseUrl", "http://localhost:8090");
    /** Must be one of the backend's CORS_ALLOWED_ORIGINS, or the WebSocket handshake is refused. */
    static final String ORIGIN = System.getProperty("origin", "http://localhost:5173");
    static final int QUESTIONS = 10;

    private static final ObjectMapper JSON = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();
    private final String auth;

    /** Signs up a throwaway teacher; the password is random and never stored. */
    Api() {
        String body = """
                {"email": "loadtest-%s@example.com", "password": "%s", "fullName": "Load test"}"""
                .formatted(UUID.randomUUID(), UUID.randomUUID());
        auth = "Bearer " + send("POST", "/api/auth/register", body, null).get("accessToken").asText();
    }

    /** 10 single-choice questions; option 0 is right. */
    long createQuiz() {
        String questions = IntStream.range(0, QUESTIONS).mapToObj(i -> """
                {"kind": "single", "prompt": "Câu %d", "timeLimitSeconds": 30,
                 "options": [{"label": "Đúng", "correct": true}, {"label": "Sai", "correct": false}]}"""
                .formatted(i + 1)).collect(Collectors.joining(","));
        return post("/api/quizzes", "{\"title\": \"Load test\", \"questions\": [" + questions + "]}")
                .get("id").asLong();
    }

    record NewClass(long id, List<String> accessCodes) {
    }

    /** A class of {@code students} (at most 100). */
    NewClass createClass(String name, int students) {
        long classId = post("/api/classes", "{\"name\": \"" + name + "\"}").get("id").asLong();
        String names = IntStream.range(0, students).mapToObj(i -> "\"Học sinh " + i + "\"")
                .collect(Collectors.joining(","));
        List<String> codes = new ArrayList<>();
        post("/api/classes/" + classId + "/students", "{\"names\": [" + names + "]}")
                .forEach(student -> codes.add(student.get("accessCode").asText()));
        return new NewClass(classId, codes);
    }

    /** Creates and publishes an assignment; returns its public code. */
    String publishAssignment(long classId, long quizId) {
        long id = post("/api/assignments", """
                {"classId": %d, "quizId": %d, "title": "Load test", "revealPolicy": "immediate"}"""
                .formatted(classId, quizId)).get("id").asLong();
        return post("/api/assignments/" + id + "/publish", "").get("publicCode").asText();
    }

    /** Opens a live room and returns its PIN. */
    String openLiveRoom(long quizId) {
        return post("/api/live", "{\"quizId\": " + quizId + "}").get("pin").asText();
    }

    String auth() {
        return auth;
    }

    /** A student starts the assignment and saves every answer but the last; returns {attemptId, token}. */
    String[] startAndAnswerAllButLast(String publicCode, String accessCode) {
        JsonNode started = send("POST", "/api/public/assignments/" + publicCode + "/start",
                "{\"accessCode\": \"" + accessCode + "\"}", null);
        String attemptId = started.get("attempt").get("attemptId").asText();
        String token = started.get("token").asText();
        for (int position = 0; position < QUESTIONS - 1; position++) {
            HttpRequest request = HttpRequest.newBuilder(URI.create(
                            BASE_URL + "/api/public/attempts/" + attemptId + "/answers/" + position))
                    .header("Content-Type", "application/json")
                    .header("X-Attempt-Token", token)
                    .PUT(HttpRequest.BodyPublishers.ofString("{\"answer\": " + position % 2 + "}"))
                    .build();
            check(request);
        }
        return new String[] {attemptId, token};
    }

    private JsonNode post(String path, String body) {
        return send("POST", path, body, auth);
    }

    private JsonNode send(String method, String path, String body, String authorization) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(BASE_URL + path))
                .header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body));
        if (authorization != null) {
            request.header("Authorization", authorization);
        }
        try {
            return JSON.readTree(check(request.build()));
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private String check(HttpRequest request) {
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new IllegalStateException(request.method() + " " + request.uri() + " -> "
                        + response.statusCode() + " " + response.body());
            }
            return response.body();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
