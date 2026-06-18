package io.casehub.clinical.service;

import io.casehub.clinical.api.ProtocolAmendmentProposedEvent;
import io.casehub.clinical.api.model.AmendmentCaseStatus;
import io.casehub.clinical.entity.ProtocolAmendment;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Observes ProtocolAmendmentProposedEvent and starts a protocol-amendment engine case.
 *
 * <p>Four-phase pattern per AeEscalationCaseService:
 * <ol>
 *   <li>Phase 1 — {@code @Transactional} idempotency guard + mark REQUESTED + build context</li>
 *   <li>Phase 2 — {@code startCase().join()} outside any TX boundary (avoids Agroal pool deadlock)</li>
 *   <li>Phase 3 — {@code @Transactional} persist returned caseId</li>
 *   <li>Phase 4 — {@code @Transactional} markFailed on any exception from Phase 2–3</li>
 * </ol>
 *
 * <p>Two {@code prepareAndMark} overloads:
 * <ul>
 *   <li>Private overload — loads entity from Panache; production path called from {@link #onProposed}</li>
 *   <li>Package-private overload — takes pre-loaded entity; unit test path (avoids Panache in unit tests)</li>
 * </ul>
 */
@ApplicationScoped
public class ProtocolAmendmentCaseService {

    private static final Logger LOG = Logger.getLogger(ProtocolAmendmentCaseService.class);

    @Inject ProtocolAmendmentCaseHub caseHub;

    public void onProposed(@ObservesAsync ProtocolAmendmentProposedEvent event) {
        try {
            Map<String, Object> ctx = prepareAndMark(event);
            if (ctx == null) return;
            // Phase 2 — startCase outside any TX boundary
            UUID caseId = caseHub.startCase(ctx).toCompletableFuture().join();
            // Phase 3 — persist caseId
            persistCaseId(event.amendmentId(), caseId);
        } catch (Exception e) {
            LOG.errorf(e, "ProtocolAmendmentCaseService: failed for amendmentId=%s", event.amendmentId());
            try {
                markFailed(event.amendmentId());
            } catch (Exception ex) {
                LOG.errorf(ex, "ProtocolAmendmentCaseService: markFailed also failed for amendmentId=%s", event.amendmentId());
            }
        }
    }

    /**
     * Package-private for unit testing — takes an already-loaded entity to avoid Panache.
     * In production only the private overload (which loads via Panache) is called.
     */
    @Transactional
    Map<String, Object> prepareAndMark(ProtocolAmendmentProposedEvent event, ProtocolAmendment amendment) {
        if (amendment.amendmentCaseStatus != AmendmentCaseStatus.NONE) {
            LOG.debugf("ProtocolAmendmentCaseService: already processed %s — skipping", event.amendmentId());
            return null;
        }
        amendment.amendmentCaseStatus = AmendmentCaseStatus.REQUESTED;
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("amendmentId", event.amendmentId().toString());
        ctx.put("trialId", event.trialId().toString());
        ctx.put("proposedChange", event.proposedChange());
        ctx.put("tenantId", event.tenantId());
        return ctx;
    }

    /** Called from onProposed — loads amendment inside @Transactional boundary. */
    @Transactional
    private Map<String, Object> prepareAndMark(ProtocolAmendmentProposedEvent event) {
        ProtocolAmendment amendment = ProtocolAmendment.findById(event.amendmentId());
        if (amendment == null) {
            LOG.warnf("ProtocolAmendmentCaseService: amendment not found %s", event.amendmentId());
            return null;
        }
        return prepareAndMark(event, amendment);
    }

    @Transactional
    void persistCaseId(UUID amendmentId, UUID caseId) {
        ProtocolAmendment a = ProtocolAmendment.findById(amendmentId);
        if (a != null) {
            a.engineCaseId = caseId;
        }
    }

    @Transactional
    void markFailed(UUID amendmentId) {
        ProtocolAmendment a = ProtocolAmendment.findById(amendmentId);
        if (a != null) {
            a.amendmentCaseStatus = AmendmentCaseStatus.FAILED;
        }
    }
}
