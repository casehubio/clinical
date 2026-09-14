package io.casehub.clinical.agent;

import java.util.UUID;

public record ClinicalAgentRequest<T>(
        String systemPrompt,
        String userPrompt,
        Class<T> responseClass,
        T fallbackValue,
        String configKey,
        String correlationId,
        UUID cascadeAeId) {}
