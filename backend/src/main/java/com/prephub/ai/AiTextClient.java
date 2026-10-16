package com.prephub.ai;

/**
 * Provider-neutral text completion. The only production implementation is backed by Spring AI's ChatClient;
 * swapping LLM providers means building a different ChatModel in {@link AiConfig}. Tests replace this bean.
 */
public interface AiTextClient {
    /** @throws AiExceptions.AiUnavailableException when AI is not configured */
    String complete(String system, String user);
    boolean available();
    String provider();
    String model();
    /** Human-readable status for the settings page (never contains secrets). */
    String status();
}
