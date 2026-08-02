package io.casehub.clinical.api;

import java.util.List;
import java.util.UUID;

public record DsmbSafetySignalEvent(UUID trialId, String signalType,
                                     List<UUID> affectedSites, String summary,
                                     String tenantId) {}
