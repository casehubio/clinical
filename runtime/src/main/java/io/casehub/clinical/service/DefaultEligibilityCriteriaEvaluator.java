package io.casehub.clinical.service;

import io.casehub.clinical.api.model.CriterionResult;
import io.casehub.clinical.api.spi.EligibilityCriteriaEvaluator;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

@DefaultBean
@ApplicationScoped
public class DefaultEligibilityCriteriaEvaluator implements EligibilityCriteriaEvaluator {

    @Override
    public List<CriterionResult> evaluate(UUID enrollmentId, String tenantId, List<String> protocolCriteria) {
        return IntStream.range(0, protocolCriteria.size())
                .mapToObj(i -> new CriterionResult("criterion-" + i, true, false))
                .toList();
    }
}
