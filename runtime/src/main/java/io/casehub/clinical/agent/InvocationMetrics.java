package io.casehub.clinical.agent;

public record InvocationMetrics(
        String model,
        int inputTokens,
        int outputTokens,
        int thinkingTokens,
        int cacheReadTokens,
        int cacheWriteTokens,
        Double totalCostUsd,
        long durationMs,
        long apiDurationMs,
        String sessionId,
        int numTurns,
        boolean isError) {

    public String toJson() {
        return "{\"model\":\"%s\",\"inputTokens\":%d,\"outputTokens\":%d,\"thinkingTokens\":%d,\"totalCostUsd\":%s,\"durationMs\":%d}"
                .formatted(model, inputTokens, outputTokens, thinkingTokens,
                        totalCostUsd != null ? totalCostUsd.toString() : "null", durationMs);
    }
}
