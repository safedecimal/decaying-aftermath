package io.github.safedecimal.decayingaftermath.client;

import io.github.safedecimal.decayingaftermath.medical.BodySnapshot;

/** Critical gameplay cannot freeze time; chat is still available for server/admin commands. */
public final class MedicalPausePolicy {
    private MedicalPausePolicy() {}
    public static boolean blocked(BodySnapshot body) {
        if(body==null||body.mode()==BodySnapshot.Mode.DEAD)return false;
        return body.mode()==BodySnapshot.Mode.UNCONSCIOUS||body.mode()==BodySnapshot.Mode.DYING
                ||body.mode()==BodySnapshot.Mode.LAST_STANDING||body.cardiacArrest()
                ||body.blood()<.3f||body.oxygen()<.5f||body.brainHealth()<.2f||body.pain()>.85f;
    }
}
