package io.github.safedecimal.decayingaftermath.compat;

import java.lang.reflect.Method;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import io.github.safedecimal.decayingaftermath.medical.BodySnapshot;
import io.github.safedecimal.decayingaftermath.server.ServerMedicalSystem;

/** Late optional API lookup; native mods are never bundled or hard-linked. */
public final class OptionalMedicalCompatibility {
    private static volatile Method cruorSend;
    private static volatile boolean cruorBroken;
    private OptionalMedicalCompatibility() {}
    private static String version(String id) {
        return FabricLoader.getInstance().getModContainer(id)
                .map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse(null);
    }
    public static boolean cruorSupported() { return OptionalPortPolicy.cruor(version("cruor"), version("minecraft")); }
    public static boolean ragdollifiedSupported() { return OptionalPortPolicy.ragdollified(version("ragdollified"), version("minecraft")); }
    static void emitCruorHit(DeferredHitContext.Hit<ServerPlayer, DamageSource> hit) {
        if (cruorBroken || !cruorSupported()) return;
        try {
            Method send = cruorSend;
            if (send == null) {
                Class<?> type = Class.forName("com.raiiiden.cruor.network.CruorNetwork", false,
                        OptionalMedicalCompatibility.class.getClassLoader());
                send = type.getMethod("send", LivingEntity.class, DamageSource.class, float.class, boolean.class);
                if (send.getReturnType() != void.class || !java.lang.reflect.Modifier.isStatic(send.getModifiers()))
                    throw new NoSuchMethodException("Unexpected Cruor send API");
                cruorSend = send;
            }
            boolean fatal = ServerMedicalSystem.get(hit.victim()).snapshot().mode() == BodySnapshot.Mode.DEAD;
            // Cruor retains its own damage-category, min-damage, range and canSend filters.
            send.invoke(null, hit.victim(), hit.source(), hit.damage(), fatal);
        } catch (ReflectiveOperationException | LinkageError error) {
            cruorBroken = true;
            System.getLogger("decaying_aftermath.compat").log(System.Logger.Level.WARNING,
                    "Cruor bridge disabled after incompatible native API: " + error);
        }
    }
}
