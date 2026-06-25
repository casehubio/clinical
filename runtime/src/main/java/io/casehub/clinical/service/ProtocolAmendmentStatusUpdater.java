package io.casehub.clinical.service;

import io.casehub.clinical.api.model.AmendmentCaseStatus;
import io.casehub.clinical.api.model.ProtocolAmendmentStatus;
import io.casehub.clinical.api.spi.AmendmentRecommendation;
import io.casehub.clinical.entity.ProtocolAmendment;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.UUID;
import org.jboss.logging.Logger;

@ApplicationScoped
public class ProtocolAmendmentStatusUpdater {

    private static final Logger LOG = Logger.getLogger(ProtocolAmendmentStatusUpdater.class);

    @Inject ProtocolAmendmentLedgerWriter ledgerWriter;

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void applyRecommendation(UUID amendmentId, String recommendation) {
        ProtocolAmendment amendment = ProtocolAmendment.findById(amendmentId);
        if (amendment == null) return;
        if (amendment.supervisorRecommendation != null) return;

        switch (recommendation) {
            case "PROCEED" -> {
                amendment.supervisorRecommendation = AmendmentRecommendation.PROCEED;
                amendment.status = ProtocolAmendmentStatus.APPROVED;
                amendment.amendmentCaseStatus = AmendmentCaseStatus.COMPLETED;
                ledgerWriter.writeResolutionEntry(amendment);
            }
            case "HALT" -> {
                amendment.supervisorRecommendation = AmendmentRecommendation.HALT;
                amendment.status = ProtocolAmendmentStatus.HALTED;
                amendment.amendmentCaseStatus = AmendmentCaseStatus.COMPLETED;
                ledgerWriter.writeResolutionEntry(amendment);
            }
            case "REFER_TO_DSMB" -> {
                amendment.supervisorRecommendation = AmendmentRecommendation.REFER_TO_DSMB;
                amendment.status = ProtocolAmendmentStatus.SUPERVISED;
                amendment.amendmentCaseStatus = AmendmentCaseStatus.COMPLETED;
                ledgerWriter.writeResolutionEntry(amendment);
            }
            default -> {
                LOG.errorf("unknown recommendation '%s' for amendmentId=%s — marking FAILED",
                        recommendation, amendmentId);
                amendment.amendmentCaseStatus = AmendmentCaseStatus.FAILED;
            }
        }
    }
}
