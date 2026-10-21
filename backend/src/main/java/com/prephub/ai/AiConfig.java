package com.prephub.ai;

import com.prephub.semantic.EmbeddingClient;
import com.prephub.semantic.SpringAiEmbeddingClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.support.RetryTemplate;

/**
 * Builds the AI clients explicitly (no Spring AI auto-configuration), so the application starts and all non-AI
 * features work when no API key is configured. The key is read from the environment on the backend only and is
 * never logged or returned by any endpoint.
 */
@Configuration
public class AiConfig {
    private static final Logger log = LoggerFactory.getLogger(AiConfig.class);

    @Value("${prephub.ai.enabled:false}") boolean enabled;
    @Value("${prephub.ai.provider:openai}") String provider;
    @Value("${prephub.ai.openai.api-key:}") String apiKey;
    @Value("${prephub.ai.openai.base-url:https://api.openai.com}") String baseUrl;

    private String disabledReason() {
        if (!enabled) return "AI is disabled. Set AI_ENABLED=true and OPENAI_API_KEY on the backend.";
        if (!"openai".equalsIgnoreCase(provider)) return "Unsupported AI provider '" + provider + "'.";
        if (apiKey == null || apiKey.isBlank()) return "OPENAI_API_KEY is not set on the backend.";
        return null;
    }

    private OpenAiApi api() { return OpenAiApi.builder().baseUrl(baseUrl).apiKey(apiKey).build(); }

    /** Interactive requests: retry transient errors once instead of Spring AI's long default back-off. */
    private static RetryTemplate retry() {
        return RetryTemplate.builder().maxAttempts(2).fixedBackoff(1000).retryOn(TransientAiException.class).build();
    }

    @Bean
    public AiTextClient aiTextClient(@Value("${prephub.ai.openai.model:gpt-4o-mini}") String model,
                                     @Value("${prephub.ai.openai.temperature:0.4}") double temperature) {
        String reason = disabledReason();
        if (reason != null) return new DisabledAiTextClient(reason);
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(api())
                .defaultOptions(OpenAiChatOptions.builder().model(model).temperature(temperature).build())
                .retryTemplate(retry())
                .build();
        log.info("AI chat enabled: provider=openai model={}", model);
        return new SpringAiTextClient(ChatClient.create(chatModel), "openai", model);
    }

//    @Bean
//    public EmbeddingClient embeddingClient(@Value("${prephub.ai.openai.embedding-model:text-embedding-3-small}") String model,
//                                           @Value("${prephub.dedup.semantic-enabled:true}") boolean semanticEnabled) {
//        String reason = disabledReason();
//        if (reason != null) return new EmbeddingClient.Disabled(reason);
//        if (!semanticEnabled) return new EmbeddingClient.Disabled("Semantic duplicate detection is disabled (DEDUP_SEMANTIC_ENABLED=false).");
//        OpenAiEmbeddingModel embeddingModel = new OpenAiEmbeddingModel(api(), MetadataMode.NONE,
//                OpenAiEmbeddingOptions.builder().model(model).build(), retry());
//        log.info("AI embeddings enabled: model={}", model);
//        return new SpringAiEmbeddingClient(embeddingModel, model);
//    }


    @Bean
    public EmbeddingClient embeddingClient(@Value("${prephub.ai.openai.embedding-model:text-embedding-3-small}") String model,
                                           @Value("${prephub.dedup.semantic-enabled:true}") boolean semanticEnabled) {
        String reason = disabledReason();
        if (reason != null) return new EmbeddingClient.Disabled(reason);
        if (!semanticEnabled) return new EmbeddingClient.Disabled("Semantic duplicate detection is disabled (DEDUP_SEMANTIC_ENABLED=false).");

        // Temporary debug logging
        log.info("Loading embedding client with API key prefix: {}", apiKey == null ? "null" : apiKey.substring(0, Math.min(7, apiKey.length())) + "...");

        OpenAiEmbeddingModel embeddingModel = new OpenAiEmbeddingModel(api(), MetadataMode.NONE,
                OpenAiEmbeddingOptions.builder().model(model).build(), retry());
        log.info("AI embeddings enabled: model={}", model);
        return new SpringAiEmbeddingClient(embeddingModel, model);
    }
}
