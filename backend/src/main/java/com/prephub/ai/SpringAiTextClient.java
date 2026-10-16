package com.prephub.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;

public class SpringAiTextClient implements AiTextClient {
    private static final Logger log = LoggerFactory.getLogger(SpringAiTextClient.class);
    private final ChatClient chat;
    private final String provider, model;

    public SpringAiTextClient(ChatClient chat, String provider, String model) { this.chat = chat; this.provider = provider; this.model = model; }

    @Override
    public String complete(String system, String user) {
        try {
            return chat.prompt().system(system).user(user).call().content();
        } catch (RuntimeException e) {
            // Never log prompts (may contain personal notes) or the exception message (may echo request details).
            log.warn("AI call failed: {}", e.getClass().getSimpleName());
            throw new AiExceptions.AiProviderException("The AI provider call failed (" + e.getClass().getSimpleName() + "). Check the API key, model name and network.");
        }
    }

    @Override public boolean available() { return true; }
    @Override public String provider() { return provider; }
    @Override public String model() { return model; }
    @Override public String status() { return "Enabled (" + provider + ", model " + model + ")"; }
}
