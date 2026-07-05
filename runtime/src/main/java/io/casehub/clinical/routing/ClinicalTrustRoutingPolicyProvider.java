package io.casehub.clinical.routing;

import io.casehub.api.spi.routing.TrustRoutingPolicy;
import io.casehub.api.spi.routing.TrustRoutingPolicyProvider;
import io.casehub.clinical.api.ClinicalCapabilities;
import io.casehub.clinical.api.ClinicalTrustDimensions;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;

/**
 * Clinical-domain trust routing policies. Displaces {@code DefaultTrustRoutingPolicyProvider
 * @DefaultBean} from casehub-engine-ledger — the @DefaultBean mechanism yields automatically
 * to any non-default @ApplicationScoped implementation of the same type.
 *
 * <p>Maturity model phases (per PLATFORM.md trust-maturity-model protocol):
 * <ul>
 *   <li>Phase 0 ({@code isBootstrap(decisionCount) == true}): availability routing</li>
 *   <li>Phase 2 ({@code passesThresholdCheck(score) == true}): trust-weighted selection</li>
 *   <li>Phase 3 ({@code isBorderline(score) == true}): EscalateToOversight assignment</li>
 * </ul>
 */
@ApplicationScoped
public class ClinicalTrustRoutingPolicyProvider implements TrustRoutingPolicyProvider {

    @Override
    public String id() {
        return "clinical";
    }

    @Override
    public TrustRoutingPolicy forCapability(String capability) {
        return switch (capability) {
            case ClinicalCapabilities.SAFETY_MONITORING ->
                // Strict: safety-critical, tight borderline margin → Phase 3 near threshold
                new TrustRoutingPolicy(0.75, 20, 0.05, 0.7,
                        Map.of(ClinicalTrustDimensions.SAFETY_ACCURACY, 0.70), false, null);

            case ClinicalCapabilities.ELIGIBILITY_SCREENING ->
                // Moderate: reversible decision, wider margin acceptable
                new TrustRoutingPolicy(0.70, 15, 0.10, 0.6,
                        Map.of(ClinicalTrustDimensions.ELIGIBILITY_PRECISION, 0.65), false, null);

            case ClinicalCapabilities.PROTOCOL_REVIEW ->
                // Conservative: high minimum observations before trust kicks in
                new TrustRoutingPolicy(0.65, 25, 0.08, 0.6,
                        Map.of(ClinicalTrustDimensions.PROTOCOL_ADHERENCE, 0.60), false, null);

            default ->
                TrustRoutingPolicy.DEFAULT;  // availability routing for all other capabilities
        };
    }
}
