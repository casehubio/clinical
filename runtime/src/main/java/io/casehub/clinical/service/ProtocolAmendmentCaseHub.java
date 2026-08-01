package io.casehub.clinical.service;

import io.casehub.api.engine.YamlCaseHub;
import io.casehub.api.model.CaseDefinition;
import io.casehub.clinical.api.spi.AmendmentRecommendation;
import io.casehub.clinical.api.spi.ProtocolAmendmentAdvisor;
import io.casehub.clinical.api.spi.ProtocolAmendmentContext;
import io.casehub.worker.api.Worker;
import io.casehub.worker.api.WorkerResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class ProtocolAmendmentCaseHub extends YamlCaseHub {

    @Inject
    ProtocolAmendmentAdvisor advisor;

    public ProtocolAmendmentCaseHub() {super("clinical/protocol-amendment.yaml");}

    @Override
    protected void augment(CaseDefinition definition) {
        definition.getWorkers().add(Worker.builder()
                                          .name("protocol-amendment-advisor-worker")
                                          .capabilityName("protocol-amendment-advisor")
                                          .function((Map<String, Object> ctx) -> {
                                              String amendmentIdStr = (String) ctx.get("amendmentId");
                                              String trialIdStr     = (String) ctx.get("trialId");
                                              if (amendmentIdStr == null || trialIdStr == null) {
                                                  return WorkerResult.failed("protocol-amendment-advisor: missing context keys (amendmentId, trialId)");
                                              }
                                              UUID                trialId  = UUID.fromString(trialIdStr);
                                              Map<String, Object> snapshot = buildTrialSnapshot(trialId);
                                              ProtocolAmendmentContext pac = new ProtocolAmendmentContext(
                                                      UUID.fromString(amendmentIdStr), trialId,
                                                      (String) ctx.get("proposedChange"), snapshot);
                                              AmendmentRecommendation rec = advisor.advise(pac);
                                              return WorkerResult.of(Map.of("advisorRecommendation", rec.name()));
                                          })
                                          .build());
    }

    private Map<String, Object> buildTrialSnapshot(UUID trialId) {
        return io.quarkus.narayana.jta.QuarkusTransaction.requiringNew().call(() -> buildTrialSnapshotInTx(trialId));
    }

    private Map<String, Object> buildTrialSnapshotInTx(UUID trialId) {
        java.util.HashMap<String, Object>        snapshot = new java.util.HashMap<>();
        io.casehub.clinical.entity.ClinicalTrial trial    = io.casehub.clinical.entity.ClinicalTrial.findById(trialId);
        if (trial == null) {return snapshot;}
        snapshot.put("trialPhase", trial.phase != null ? trial.phase.name() : "UNKNOWN");
        snapshot.put("trialStatus", trial.status != null ? trial.status.name() : "UNKNOWN");
        snapshot.put("sponsor", trial.sponsor);
        long totalAes = io.casehub.clinical.entity.AdverseEvent.count(
                "enrollmentId in (select id from PatientEnrollment where siteId in (select id from TrialSite where trialId = ?1))", trialId);
        snapshot.put("totalAdverseEvents", totalAes);
        long grade3Plus = io.casehub.clinical.entity.AdverseEvent.count(
                "grade in (?1, ?2, ?3) and enrollmentId in (select id from PatientEnrollment where siteId in (select id from TrialSite where trialId = ?4))",
                io.casehub.clinical.api.model.CtcaeGrade.GRADE_3, io.casehub.clinical.api.model.CtcaeGrade.GRADE_4, io.casehub.clinical.api.model.CtcaeGrade.GRADE_5, trialId);
        snapshot.put("grade3PlusCount", grade3Plus);
        boolean hasGrade5 = io.casehub.clinical.entity.AdverseEvent.count(
                "grade = ?1 and enrollmentId in (select id from PatientEnrollment where siteId in (select id from TrialSite where trialId = ?2))",
                io.casehub.clinical.api.model.CtcaeGrade.GRADE_5, trialId) > 0;
        snapshot.put("hasGrade5", hasGrade5);
        snapshot.put("priorAmendmentCount", io.casehub.clinical.entity.ProtocolAmendment.findByTrialId(trialId).size());
        return snapshot;
    }
}
