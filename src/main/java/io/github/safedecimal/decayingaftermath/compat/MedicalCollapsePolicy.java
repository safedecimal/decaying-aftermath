package io.github.safedecimal.decayingaftermath.compat;

import io.github.safedecimal.decayingaftermath.medical.BodySnapshot.Mode;

/** Sleeping is not a medical collapse; DYING still permits the rescue procedure. */
public final class MedicalCollapsePolicy {
    private MedicalCollapsePolicy() {}
    public static boolean collapsed(Mode mode, boolean alive, boolean sleeping, boolean exempt) {
        return alive && !sleeping && !exempt && mode == Mode.UNCONSCIOUS;
    }
}
