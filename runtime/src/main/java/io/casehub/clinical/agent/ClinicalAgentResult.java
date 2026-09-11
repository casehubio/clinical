package io.casehub.clinical.agent;

public record ClinicalAgentResult<T>(
        T response,
        String rawText,
        InvocationMetrics metrics,
        boolean fallbackUsed,
        String failureReason) {

    public static <T> ClinicalAgentResult<T> success(T response, String rawText, InvocationMetrics metrics) {
        return new ClinicalAgentResult<>(response, rawText, metrics, false, null);
    }

    public static <T> ClinicalAgentResult<T> fallback(T fallbackValue, String reason, InvocationMetrics metrics) {
        return new ClinicalAgentResult<>(fallbackValue, null, metrics, true, reason);
    }
}
