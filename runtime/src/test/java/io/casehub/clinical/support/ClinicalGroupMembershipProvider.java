package io.casehub.clinical.support;

import io.casehub.platform.api.identity.GroupMember;
import io.casehub.platform.api.identity.GroupMembershipProvider;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Set;

/**
 * Test-only GroupMembershipProvider that resolves CDI ambiguity between
 * MockGroupMembershipProvider (casehub-platform, @DefaultBean) and
 * NoOpGroupMembershipProvider (casehub-work, @DefaultBean). Both are @DefaultBean —
 * two defaults conflict. This non-@DefaultBean bean takes precedence and suppresses both.
 *
 * <p>See clinical#55 (group-membership-provider CDI ambiguity).
 */
@ApplicationScoped
public class ClinicalGroupMembershipProvider implements GroupMembershipProvider {

    @Override
    public Set<GroupMember> membersOf(String groupName, String tenancyId) {
        return Set.of();
    }
}
