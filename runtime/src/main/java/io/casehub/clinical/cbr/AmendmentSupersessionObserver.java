package io.casehub.clinical.cbr;

import io.casehub.clinical.api.ProtocolAmendmentResolvedEvent;
import io.casehub.clinical.entity.ProtocolAmendment;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class AmendmentSupersessionObserver {

    private static final Logger LOG = Logger.getLogger(AmendmentSupersessionObserver.class);

    private final CbrCaseMemoryStore store;

    @Inject
    public AmendmentSupersessionObserver(CbrCaseMemoryStore store) {
        this.store = store;
    }

    @Transactional
    public void onAmendmentResolved(@ObservesAsync ProtocolAmendmentResolvedEvent event) {
        try {
            List<ProtocolAmendment> amendments = ProtocolAmendment.findByTrialId(event.trialId());
            onAmendmentResolved(event, amendments);
        } catch (Exception e) {
            LOG.errorf(e, "Amendment supersession failed for amendment %s", event.amendmentId());
        }
    }

    void onAmendmentResolved(ProtocolAmendmentResolvedEvent event,
                             List<ProtocolAmendment> amendments) {
        if (amendments.size() < 2) return;

        List<ProtocolAmendment> sorted = amendments.stream()
            .sorted(Comparator.comparing(a -> a.proposedAt))
            .toList();

        int currentIndex = -1;
        for (int i = 0; i < sorted.size(); i++) {
            if (sorted.get(i).id.equals(event.amendmentId())) {
                currentIndex = i;
                break;
            }
        }

        if (currentIndex <= 0) return;

        ProtocolAmendment prior = sorted.get(currentIndex - 1);
        UUID currentId = event.amendmentId();

        store.supersede(
            prior.id.toString(),
            event.tenantId(),
            currentId.toString(),
            "Superseded by newer amendment " + currentId);

        LOG.infof("Superseded amendment CBR case %s with %s for trial %s",
            prior.id, currentId, event.trialId());
    }
}
