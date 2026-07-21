package io.casehub.clinical.service;

import io.casehub.clinical.api.AeGradeChangedEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;

@ApplicationScoped
public class AeGradeChangeRegulatoryListener {

    @Inject RegulatorySubmissionCaseService regulatorySubmissionCaseService;

    public void onGradeChanged(@ObservesAsync AeGradeChangedEvent event) {
        if (!event.isUpgrade()) return;
        regulatorySubmissionCaseService.reevaluateForRegrade(event.aeId(), event.siteId(), event.tenantId());
    }
}
