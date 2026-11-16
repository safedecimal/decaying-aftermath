package io.github.safedecimal.decayingaftermath.server;

import io.github.safedecimal.decayingaftermath.medical.BodyModel;
import net.minecraft.world.damagesource.DamageSource;

public interface ServerBodyOwner {
    BodyModel decayingAftermath$getBody();
    void decayingAftermath$setBody(BodyModel body);
    boolean decayingAftermath$isBodyReady();
    void decayingAftermath$setBodyReady(boolean ready);
    boolean decayingAftermath$tryTotem(DamageSource source);
}
