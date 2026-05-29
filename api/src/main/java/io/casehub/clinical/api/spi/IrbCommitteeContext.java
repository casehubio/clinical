package io.casehub.clinical.api.spi;

import io.casehub.clinical.api.model.DeviationSeverity;
import java.util.UUID;

/**
 * Context passed to {@link IrbCommitteeAssignmentPolicy#evaluate}.
 * {@code trialId} may be null if the site has no active trial case.
 */
public record IrbCommitteeContext(
        UUID deviationId,
        UUID siteId,
        UUID trialId,
        DeviationSeverity severity) {}
