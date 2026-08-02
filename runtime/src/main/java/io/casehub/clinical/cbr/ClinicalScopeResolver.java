package io.casehub.clinical.cbr;

import io.casehub.clinical.entity.ClinicalTrial;
import io.casehub.clinical.entity.PatientEnrollment;
import io.casehub.clinical.entity.ProtocolAmendment;
import io.casehub.clinical.entity.ProtocolDeviation;
import io.casehub.clinical.entity.TrialSite;
import io.casehub.platform.api.path.Path;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class ClinicalScopeResolver {

    private EntityResolver entityResolver = new PanacheEntityResolver();

    void setEntityResolver(EntityResolver resolver) {
        this.entityResolver = resolver;
    }

    public Optional<Path> forAdverseEvent(io.casehub.clinical.entity.AdverseEvent ae) {
        if (ae.enrollmentId == null) return Optional.empty();
        PatientEnrollment enrollment = entityResolver.findEnrollment(ae.enrollmentId);
        if (enrollment == null) return Optional.empty();
        TrialSite site = entityResolver.findSite(enrollment.siteId);
        if (site == null) return Optional.empty();
        return Optional.of(Path.of(site.trialId.toString(), enrollment.siteId.toString(), enrollment.patientId));
    }

    public Optional<Path> forDeviation(ProtocolDeviation dev) {
        if (dev.siteId == null) return Optional.empty();
        TrialSite site = entityResolver.findSite(dev.siteId);
        if (site == null) return Optional.empty();
        return Optional.of(Path.of(site.trialId.toString(), dev.siteId.toString()));
    }

    public Optional<Path> forAmendment(ProtocolAmendment amend) {
        if (amend.trialId == null) return Optional.empty();
        return Optional.of(Path.of(amend.trialId.toString()));
    }

    public Optional<Path> forSiteEnrollment(TrialSite site) {
        if (site.trialId == null) return Optional.empty();
        return Optional.of(Path.of(site.trialId.toString(), site.id.toString()));
    }

    public Optional<Path> forTrial(ClinicalTrial trial) {
        if (trial.id == null) return Optional.empty();
        return Optional.of(Path.of(trial.id.toString()));
    }

    interface EntityResolver {
        PatientEnrollment findEnrollment(UUID id);
        TrialSite findSite(UUID id);
        ClinicalTrial findTrial(UUID id);
    }

    private static class PanacheEntityResolver implements EntityResolver {
        @Override
        public PatientEnrollment findEnrollment(UUID id) { return PatientEnrollment.findById(id); }

        @Override
        public TrialSite findSite(UUID id) { return TrialSite.findById(id); }

        @Override
        public ClinicalTrial findTrial(UUID id) { return ClinicalTrial.findById(id); }
    }
}
