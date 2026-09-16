package io.casehub.clinical.service.api;

import io.casehub.clinical.api.spi.ClinicalGdprApi;
import io.casehub.clinical.api.view.EraseResult;
import io.casehub.clinical.service.GdprErasureService;
import io.casehub.clinical.service.PatientNotFoundException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;

@ApplicationScoped
public class DefaultClinicalGdprApi implements ClinicalGdprApi {

    @Inject GdprErasureService erasureService;

    @Override
    public EraseResult erasePatient(String patientId, String tenancyId) {
        try {
            int count = erasureService.erasePatient(patientId, tenancyId);
            return new EraseResult(count);
        } catch (PatientNotFoundException e) {
            throw new NotFoundException("Patient not found: " + patientId);
        }
    }
}
