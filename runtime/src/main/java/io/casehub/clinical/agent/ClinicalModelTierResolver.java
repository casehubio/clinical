package io.casehub.clinical.agent;

import io.casehub.platform.api.model.ModelQuery;
import io.casehub.platform.api.model.ModelRegistry;
import io.casehub.platform.api.model.ModelTier;
import org.eclipse.microprofile.config.Config;

import java.util.Map;

final class ClinicalModelTierResolver {

    private static final String DEFAULT_MODEL = "sonnet";

    static final Map<String, ModelTier> DEFAULT_TIERS = Map.of(
            "safety-monitoring", ModelTier.FLAGSHIP,
            "susar-criteria", ModelTier.FLAGSHIP,
            "protocol-amendment", ModelTier.FLAGSHIP,
            "trial-supervision", ModelTier.FLAGSHIP,
            "eligibility-screening", ModelTier.STANDARD
    );

    private ClinicalModelTierResolver() {}

    static String resolveModel(String configKey, Config config, ModelRegistry modelRegistry) {
        var explicitModel = config.getOptionalValue(
                "casehub.clinical.agent." + configKey + ".model", String.class);
        if (explicitModel.isPresent()) return explicitModel.get();

        var tierStr = config.getOptionalValue(
                "casehub.clinical.agent." + configKey + ".tier", String.class);
        ModelTier tier = tierStr.map(ModelTier::valueOf)
                .orElse(DEFAULT_TIERS.getOrDefault(configKey, ModelTier.FLAGSHIP));

        var models = modelRegistry.query(ModelQuery.builder().tier(tier).build());
        if (!models.isEmpty()) return models.getFirst().apiModelId();

        return DEFAULT_MODEL;
    }
}
