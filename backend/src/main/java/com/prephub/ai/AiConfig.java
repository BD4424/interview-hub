package com.prephub.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.support.RetryTemplate;

/**
 * Builds the AI client explicitly (no Spring AI auto-configuration), so the application starts and all non-AI
 * features work when no API key is configured. The key is read from the environment on the backend only.
 */
@Configuration
public class AiConfig {
    private static final Logger log = LoggerFactory.getLogger(AiConfig.class);

    @Bean
    public AiTextClient aiTextClient(@Value("${prephub.ai.enabled:false}") boolean enabled,
                                     @Value("${prephub.ai.provider:openai}") String provider,
                                     @Value("${prephub.ai.openai.api-key:}") String apiKey,
                                     @Value("${prephub.ai.openai.base-url:https://api.openai.com}") String baseUrl,
                                     @Value("${prephub.ai.openai.model:gpt-4o-mini}") String model,
                                     @Value("${prephub.ai.openai.temperature:0.4}") double temperature) {
        if (!enabled) return new DisabledAiTextClient("AI is disabled. Set AI_ENABLED=true and OPENAI_API_KEY on the backend.");
        if (!"openai".equalsIgnoreCase(provider)) return new DisabledAiTextClient("Unsupported AI provider '" + provider + "'.");
        if (apiKey == null || apiKey.isBlank()) return new DisabledAiTextClient("OPENAI_API_KEY is not set on the backend.");

        OpenAiApi api = OpenAiApi.builder().baseUrl(baseUrl).apiKey(apiKey).build();
        // Interactive requests: retry transient errors once instead of Spring AI's long default back-off.
        RetryTemplate retry = RetryTemplate.builder().maxAttempts(2).fixedBackoff(1000).retryOn(TransientAiException.class).build();
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(OpenAiChatOptions.builder().model(model).temperature(temperature).build())
                .retryTemplate(retry)
                .build();
        log.info("AI enabled: provider=openai model={}", model);
        return new SpringAiTextClient(ChatClient.create(chatModel), "openai", model);
    }
}
