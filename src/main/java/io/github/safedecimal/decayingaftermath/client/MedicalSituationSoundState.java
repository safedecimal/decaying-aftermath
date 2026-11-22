package io.github.safedecimal.decayingaftermath.client;

import io.github.safedecimal.decayingaftermath.medical.BodySnapshot;

/** CU MusicManager drone conditions that have authoritative Minecraft equivalents. */
public final class MedicalSituationSoundState {
    public record Volumes(float pain, float exhausted, float tired, float dying) {
        public boolean silent() { return pain <= 0 && exhausted <= 0 && tired <= 0 && dying <= 0; }
    }

    private MedicalSituationSoundState() {}

    public static Volumes volumes(BodySnapshot body) {
        if (body == null || body.mode() == BodySnapshot.Mode.DEAD) return new Volumes(0, 0, 0, 0);
        if (body.mode() == BodySnapshot.Mode.DYING) return new Volumes(0, 0, 0, 1);
        float pain = body.pain() > .6f ? Math.clamp((body.pain() - .6f) / .25f * .8f + .2f, 0, 1) : 0;
        float exhausted = body.energy() < .5f ? Math.clamp(1 - body.energy() * 2, 0, 1) : 0;
        float tired = body.energy() < .25f ? Math.clamp(1 - body.energy() * 4, 0, 1) : 0;
        boolean deteriorating=body.mode()!=BodySnapshot.Mode.LAST_STANDING
                &&(body.cardiacArrest()||body.oxygen()<.65f||body.blood()<.4f
                ||body.brainHealth()<.3f||Math.max(body.survival().thermalStress(),body.survival().nausea())>.75f);
        float dying = deteriorating?1:0;
        if(dying>0)return new Volumes(0,0,0,dying);
        if(pain>0)return new Volumes(pain,0,0,0);
        if(tired>0)return new Volumes(0,0,tired,0);
        return new Volumes(0,exhausted,0,0);
    }
}
