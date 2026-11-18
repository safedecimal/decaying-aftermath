package io.github.safedecimal.decayingaftermath.medical;

import java.util.List;

/** One already-mitigated impact budget: legs first, increasing secondary whole-body trauma. */
public final class FallTrauma {
    private FallTrauma() { }
    public record Hit(BodyPart part,float amount,DamageKind kind) { }
    public static List<Hit> hits(float damage) {
        if(!Float.isFinite(damage)||damage<=0)return List.of();
        damage=Math.min(damage,1000);
        float severity=Math.clamp((damage-8)/32,0,1);
        float torso=damage*.24f*severity,head=damage*.10f*severity,arm=damage*.03f*severity;
        float leg=(damage-torso-head-arm*2)*.5f;
        DamageKind contact=damage>8?DamageKind.FALL_IMPACT:DamageKind.BLUNT;
        return List.of(new Hit(BodyPart.LEFT_LEG,leg,contact),new Hit(BodyPart.RIGHT_LEG,leg,contact),
                new Hit(BodyPart.TORSO,torso,damage>16?DamageKind.FALL_IMPACT:DamageKind.BLUNT),
                new Hit(BodyPart.HEAD,head,DamageKind.BLUNT),
                new Hit(BodyPart.LEFT_ARM,arm,DamageKind.BLUNT),new Hit(BodyPart.RIGHT_ARM,arm,DamageKind.BLUNT));
    }
}
