package io.casehub.clinical.api.spi;

import java.util.List;

/** Assignment returned by {@link IrbCommitteeAssignmentPolicy#evaluate}. */
public record IrbCommitteeAssignment(String committeeId, List<String> candidateGroups) {}
