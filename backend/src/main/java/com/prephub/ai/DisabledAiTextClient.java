package com.prephub.ai;

public class DisabledAiTextClient implements AiTextClient {
    private final String reason;
    public DisabledAiTextClient(String reason) { this.reason = reason; }
    @Override public String complete(String system, String user) { throw new AiExceptions.AiUnavailableException(reason); }
    @Override public boolean available() { return false; }
    @Override public String provider() { return null; }
    @Override public String model() { return null; }
    @Override public String status() { return reason; }
}
