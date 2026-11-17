package io.github.safedecimal.decayingaftermath.compat;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
class OptionalPortPolicyTest {
 @Test void missingModsAreNoOp(){assertFalse(OptionalPortPolicy.cruor(null,"26.3"));assertFalse(OptionalPortPolicy.ragdollified(null,"26.3"));}
 @Test void onlyVerifiedSuppliedPortsAreEnabled(){assertTrue(OptionalPortPolicy.cruor("0.1.0-port.4","26.3"));assertTrue(OptionalPortPolicy.ragdollified("1.2.2","26.3"));}
 @Test void unrelatedVersionsAndLoadersAreNotClaimedSupported(){assertFalse(OptionalPortPolicy.cruor("0.1.0","1.20.1"));assertFalse(OptionalPortPolicy.cruor("0.1.0-port.5","26.3"));assertFalse(OptionalPortPolicy.ragdollified("1.2.2","1.21.1"));assertFalse(OptionalPortPolicy.ragdollified("1.2.3","26.3"));}
}
