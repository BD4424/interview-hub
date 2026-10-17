package com.prephub.semantic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;

import java.util.List;

public class SpringAiEmbeddingClient implements EmbeddingClient {
    private static final Logger log = LoggerFactory.getLogger(SpringAiEmbeddingClient.class);
    private final EmbeddingModel model;
    private final String modelName;

    public SpringAiEmbeddingClient(EmbeddingModel model, String modelName) { this.model = model; this.modelName = modelName; }

    @Override public boolean available() { return true; }
    @Override public String model() { return modelName; }
    @Override public String status() { return "Enabled (" + modelName + ")"; }

    @Override
    public List<float[]> embed(List<String> texts) {
        try {
            List<float[]> out = model.embed(texts);
            if (out.size() != texts.size()) throw new IllegalStateException("embedding count mismatch");
            return out;
        } catch (RuntimeException e) {
            log.warn("Embedding call failed: {}", e.getClass().getSimpleName());
            throw new IllegalStateException("Embedding provider call failed (" + e.getClass().getSimpleName() + ")");
        }
    }
}
