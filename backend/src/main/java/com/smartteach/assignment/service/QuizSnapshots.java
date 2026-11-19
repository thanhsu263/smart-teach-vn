package com.smartteach.assignment.service;

import com.smartteach.assignment.dto.QuizSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** JSON conversion for the quiz snapshot and the saved answers (both stored as JSON text). */
@Component
@RequiredArgsConstructor
public class QuizSnapshots {

    private final JsonMapper jsonMapper;

    public String write(QuizSnapshot snapshot) {
        return jsonMapper.writeValueAsString(snapshot);
    }

    public QuizSnapshot read(String json) {
        return jsonMapper.readValue(json, QuizSnapshot.class);
    }

    public String writeAnswer(JsonNode answer) {
        return jsonMapper.writeValueAsString(answer);
    }

    public JsonNode readAnswer(String json) {
        return jsonMapper.readTree(json);
    }
}
