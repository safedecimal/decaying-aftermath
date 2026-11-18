package io.github.safedecimal.decayingaftermath.medical;

import java.util.UUID;

/** The player's ABO and Rh group, independent of their current blood volume. */
public enum BloodGroup {
    // Keep the order stable: it determines existing players' groups and the packet IDs.
    O_NEGATIVE("O-"), O_POSITIVE("O+"), A_NEGATIVE("A-"), A_POSITIVE("A+"),
    B_NEGATIVE("B-"), B_POSITIVE("B+"), AB_NEGATIVE("AB-"), AB_POSITIVE("AB+");

    private static final BloodGroup[] GROUPS = values();
    private final String label;

    BloodGroup(String label) { this.label = label; }

    public String label() { return label; }

    public static BloodGroup forPlayer(UUID playerId) {
        return GROUPS[Math.floorMod(playerId.hashCode(), GROUPS.length)];
    }
}
