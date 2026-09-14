package io.casehub.clinical.agent;

import io.casehub.platform.api.model.ModelTier;

public record ModelSelectionEvent(
        String configKey,
        ModelTier tier,
        String modelId) {}
