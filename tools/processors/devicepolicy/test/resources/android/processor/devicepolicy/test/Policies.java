package android.app.admin.metadata;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Generated class to load policy metadata
 */
public class Policies {
    /**
     * Generated method that returns a list of all policy metadata
     */
    public static List<PolicyMetadata<?>> loadPolicyMetadata() {
        List<PolicyMetadata<?>> policies = new ArrayList<PolicyMetadata<?>>();
        policies.add(new BooleanPolicyMetadata(
            /* id= */ android.app.admin.PolicyIdentifier.SIMPLE_BOOLEAN_POLICY,
            /* allowedScopes= */ Set.of(
                1,
                2
            ),
            /* affectedResource= */ 2
        ));
        policies.add(new EnumPolicyMetadata(
            /* id= */ android.app.admin.PolicyIdentifier.SIMPLE_ENUM_POLICY,
            /* allowedScopes= */ Set.of(
                2,
                3
            ),
            /* affectedResource= */ 1,
            /* allowedValues= */ Set.of(
                0,
                1,
                2
            )
        ));
        policies.add(new IntegerPolicyMetadata(
            /* id= */ android.app.admin.PolicyIdentifier.SIMPLE_INTEGER_POLICY,
            /* allowedScopes= */ Set.of(
                1
            ),
            /* affectedResource= */ 1
        ));
        return policies;
    }
}
