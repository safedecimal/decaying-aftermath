package io.github.safedecimal.decayingaftermath.medical;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BloodGroupTest {
    @Test void allEightAboAndRhGroupsCanBeAssigned() {
        var labels = Arrays.stream(BloodGroup.values()).map(BloodGroup::label).toList();
        assertEquals(List.of("O-", "O+", "A-", "A+", "B-", "B+", "AB-", "AB+"), labels);
        assertEquals(labels, IntStream.range(0, 8)
                .mapToObj(id -> BloodGroup.forPlayer(new UUID(0, id)).label()).toList());
    }

    @Test void theSamePlayerIdKeepsItsGroupAcrossSessions() {
        String id = "206d36f3-2baa-48bb-8239-15d06c1bff77";
        assertEquals(BloodGroup.AB_POSITIVE, BloodGroup.forPlayer(UUID.fromString(id)));
        assertEquals(BloodGroup.AB_POSITIVE, BloodGroup.forPlayer(UUID.fromString(id)));
    }
}
