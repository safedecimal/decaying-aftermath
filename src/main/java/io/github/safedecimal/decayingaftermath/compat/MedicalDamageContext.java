package io.github.safedecimal.decayingaftermath.compat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import io.github.safedecimal.decayingaftermath.server.ServerMedicalSystem;

/** Source lock surrounding vanilla/Cruor's original, single post-mitigation HP write. */
public final class MedicalDamageContext {
    private static final DeferredHitContext<ServerPlayer, DamageSource> CHAIN =
            new DeferredHitContext<>(OptionalMedicalCompatibility::emitCruorHit);
    private MedicalDamageContext() {}
    public static DeferredHitContext<ServerPlayer, DamageSource>.Frame begin(ServerPlayer player, DamageSource source) {
        ServerMedicalSystem.get(player); // Ensure first-hit health interception is ready too.
        return CHAIN.begin(player, source);
    }
    public static DamageSource activeDamageSource(ServerPlayer player) { return CHAIN.source(player); }
    public static boolean captureCruorHit(LivingEntity victim, DamageSource source, float damage) {
        return victim instanceof ServerPlayer player && CHAIN.capture(player, source, damage);
    }
}
