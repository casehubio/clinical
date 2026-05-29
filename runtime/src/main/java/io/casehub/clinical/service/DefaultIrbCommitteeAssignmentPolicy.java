package io.casehub.clinical.service;

import io.casehub.clinical.api.spi.IrbCommitteeAssignment;
import io.casehub.clinical.api.spi.IrbCommitteeAssignmentPolicy;
import io.casehub.clinical.api.spi.IrbCommitteeContext;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
@DefaultBean
public class DefaultIrbCommitteeAssignmentPolicy implements IrbCommitteeAssignmentPolicy {

    @Override
    public IrbCommitteeAssignment evaluate(IrbCommitteeContext context) {
        return new IrbCommitteeAssignment("irb-committee", List.of("irb-committee"));
    }
}
