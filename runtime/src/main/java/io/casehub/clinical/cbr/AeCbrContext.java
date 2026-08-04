package io.casehub.clinical.cbr;

import io.casehub.clinical.entity.AdverseEvent;
import io.casehub.clinical.entity.ClinicalTrial;
import io.casehub.clinical.entity.PatientEnrollment;

public record AeCbrContext(AdverseEvent ae,
                            PatientEnrollment enrollment,
                            ClinicalTrial trial,
                            String safetyReviewOutcome,
                            boolean dsmbEscalated,
                            long priorAeCount,
                            long siteEnrollmentCount,
                            int siteTargetEnrollment,
                            double agentTrustScore) {}
