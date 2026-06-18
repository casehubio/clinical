package io.casehub.clinical.service;

import io.casehub.clinical.api.model.AmendmentCaseStatus;
import io.casehub.clinical.api.model.ProtocolAmendmentStatus;
import io.casehub.clinical.api.spi.AmendmentRecommendation;
import io.casehub.clinical.entity.ProtocolAmendment;
import io.casehub.engine.common.spi.CaseInstanceRepository;
import io.casehub.engine.common.spi.event.CaseLifecycleEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.UUID;

/**
 * Observes CaseLifecycleEvent for protocol amendment cases.
 *
 * <p>Discriminates by presence of {@code amendmentId} in case context — set by
 * {@link ProtocolAmendmentCaseService} in the initial context map.
 *
 * <p>Idempotency guard: {@code supervisorRecommendation != null} — null until the first
 * run, non-null after any branch (PROCEED, HALT, REFER_TO_DSMB). REFER_TO_DSMB keeps
 * {@code status = SUPERVISED} so a status-based guard would re-enter on re-delivery.
 *
 * <p>PP-20260530-49856c opt-out: no REQUIRES_NEW split and no fireAsync after the ledger
 * write. Status update and ledger write are in the same XA transaction — both commit or
 * neither does. The double-write scenario cannot occur.
 */
@ApplicationScoped
public class ProtocolAmendmentListener {

    private static final Logger LOG = Logger.getLogger(ProtocolAmendmentListener.class);
    private static final Duration LOOKUP_TIMEOUT = Duration.ofSeconds(5);

    @Inject CaseInstanceRepository caseInstanceRepository;
    @Inject ProtocolAmendmentLedgerWriter ledgerWriter;

    @Transactional
    public void onCaseLifecycle(@ObservesAsync CaseLifecycleEvent event) {
        if (!"GoalReached".equals(event.eventType()) && !"CaseCompleted".equals(event.eventType())) return;

        var instance = caseInstanceRepository
            .findByUuid(event.caseId(), event.tenancyId())
            .await().atMost(LOOKUP_TIMEOUT);
        if (instance == null) return;

        Object amendmentIdObj = instance.getCaseContext().getPath("amendmentId");
        if (amendmentIdObj == null) return; // not a protocol amendment case

        UUID amendmentId;
        try {
            amendmentId = UUID.fromString(amendmentIdObj.toString());
        } catch (IllegalArgumentException e) {
            LOG.warnf("ProtocolAmendmentListener: invalid amendmentId: %s", amendmentIdObj);
            return;
        }

        ProtocolAmendment amendment = ProtocolAmendment.findById(amendmentId);
        if (amendment == null) return;

        // Idempotency guard: supervisorRecommendation is null until listener first runs.
        // REFER_TO_DSMB keeps status=SUPERVISED so a status-based guard would re-enter.
        if (amendment.supervisorRecommendation != null) return;

        Object recObj = instance.getCaseContext().getPath("advisorRecommendation");
        if (recObj == null) {
            LOG.errorf("ProtocolAmendmentListener: advisorRecommendation absent from case context for amendmentId=%s " +
                "— amendment stays at current status; audit gap", amendmentId);
            return;
        }
        String rec = recObj.toString();

        amendment.supervisorRecommendation = AmendmentRecommendation.valueOf(rec);
        amendment.status = switch (rec) {
            case "PROCEED"       -> ProtocolAmendmentStatus.APPROVED;
            case "HALT"          -> ProtocolAmendmentStatus.HALTED;
            case "REFER_TO_DSMB" -> ProtocolAmendmentStatus.SUPERVISED;
            default -> throw new IllegalStateException("Unknown recommendation: " + rec);
        };
        amendment.amendmentCaseStatus = AmendmentCaseStatus.COMPLETED;
        ledgerWriter.writeResolutionEntry(amendment);
    }
}
