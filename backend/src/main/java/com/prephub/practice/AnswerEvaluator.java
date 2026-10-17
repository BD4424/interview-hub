package com.prephub.practice;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prephub.ai.AiExceptions;
import com.prephub.ai.AiTextClient;
import com.prephub.ai.FollowUpValidator;
import com.prephub.practice.EvaluationValidator.Evaluation;
import com.prephub.practice.EvaluationValidator.RawEvaluation;
import org.springframework.stereotype.Component;

import java.util.List;

/** Calls the model, parses JSON (one retry on malformed output) and validates the evaluation. */
@Component
public class AnswerEvaluator {
    private final AiTextClient ai;
    private final ObjectMapper json;

    public AnswerEvaluator(AiTextClient ai, ObjectMapper json) {
        this.ai = ai;
        this.json = json.copy().enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    public boolean available() { return ai.available(); }

    public Evaluation evaluate(String question, String topic, String difficulty, String reference, List<String> expectedPoints, String answer) {
        if (!ai.available()) throw new AiExceptions.AiUnavailableException(ai.status());
        String system = EvaluationPrompts.system();
        String user = EvaluationPrompts.user(question, topic, difficulty, reference, expectedPoints, answer);
        String model = ai.provider() + "/" + ai.model();
        try {
            return parse(ai.complete(system, user), model);
        } catch (IllegalArgumentException first) {
            try {
                return parse(ai.complete(system, user + "\nYour previous reply was invalid (" + first.getMessage() + "). Reply with the JSON object only."), model);
            } catch (IllegalArgumentException second) {
                throw new AiExceptions.AiOutputException("The AI evaluation was not valid", List.of(second.getMessage()));
            }
        }
    }

    Evaluation parse(String reply, String model) {
        RawEvaluation raw;
        try { raw = json.readValue(FollowUpValidator.extractJsonObject(reply), RawEvaluation.class); }
        catch (IllegalArgumentException e) { throw e; }
        catch (Exception e) { throw new IllegalArgumentException("JSON does not match the evaluation schema (" + e.getClass().getSimpleName() + ")"); }
        return EvaluationValidator.validate(raw, model);
    }
}
