package io.casehub.clinical.api;

import io.casehub.clinical.api.model.ProtocolAmendmentStatus;
import io.casehub.clinical.api.spi.AmendmentRecommendation;

import java.util.UUID;

/**
 * Fired when a protocol amendment reaches terminal resolution: APPROVED, HALTED, or SUPERVISED.
 * <p>
 * Consumers (e.g., CBR writers) can react to record precedent for future amendment decisions.
 *
 * @param amendmentId       amendment entity identifier
 * @param trialId           trial this amendment belongs to
 * @param terminalStatus    final status: APPROVED, HALTED, or SUPERVISED
 * @param recommendation    supervisor recommendation: PROCEED, HALT, or REFER_TO_DSMB
 * @param tenantId          tenant identifier
 */
public record ProtocolAmendmentResolvedEvent(
    UUID amendmentId,
    UUID trialId,
    ProtocolAmendmentStatus terminalStatus,
    AmendmentRecommendation recommendation,
    String tenantId) {
}
