package io.github.safedecimal.decayingaftermath.compat;

import java.lang.reflect.Method;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import io.github.safedecimal.decayingaftermath.medical.BodySnapshot;

/** Own only the native live body started by medical collapse, never a death corpse. */
public final class MedicalRagdollCompatibility {
    private record Api(Method start, Method stop, Method live, Method serial, java.lang.reflect.Field retained, java.lang.reflect.Field bodySerial) {}
    private static final MedicalRagdollLifecycle<ServerPlayer> LIFECYCLE = new MedicalRagdollLifecycle<>(new MedicalRagdollLifecycle.Port<>() {
        public int liveSerial(int id) throws ReflectiveOperationException {
            Api port = api(); return (boolean)port.live().invoke(null, id) ? (int)port.serial().invoke(null, id) : 0;
        }
        public int start(ServerPlayer player) throws ReflectiveOperationException {
            api().start().invoke(null, player, player.position(), player.yBodyRot, player.getXRot(), player.getDeltaMovement(), Integer.MAX_VALUE);
            return liveSerial(player.getId());
        }
        public void stop(int id) throws ReflectiveOperationException { api().stop().invoke(null, id); }
    });
    private static Api api;
    private static boolean broken;
    private MedicalRagdollCompatibility() {}
    private static Api api() throws ReflectiveOperationException {
        if (api == null) {
            Class<?> type = Class.forName("com.raiiiden.ragdollified.api.RagdollifiedServerApi");
            Class<?> manager = Class.forName("com.raiiiden.ragdollified.server.ServerRagdollSyncManager");
            var retained = manager.getDeclaredField("RETAINED"); retained.setAccessible(true);
            var bodySerial = Class.forName(manager.getName() + "$RetainedRagdoll").getDeclaredField("bodySerial"); bodySerial.setAccessible(true);
            api = new Api(type.getMethod("startPlayerRagdoll", ServerPlayer.class, Vec3.class, float.class,
                    float.class, Vec3.class, int.class), type.getMethod("stopRagdoll", int.class),
                    type.getMethod("isLiveRagdoll", int.class), type.getMethod("liveRagdollSerial", int.class), retained, bodySerial);
        }
        return api;
    }
    public static int bodySerial(ServerPlayer player) {
        if (broken || !OptionalMedicalCompatibility.ragdollifiedSupported()) return 0;
        try {
            if (!LIFECYCLE.owns(player)) return 0;
            Api port = api(); Object body = ((java.util.Map<?, ?>)port.retained().get(null)).get(player.getId());
            return body == null ? 0 : port.bodySerial().getInt(body);
        } catch (ReflectiveOperationException | LinkageError error) { fail(error); return 0; }
    }
    public static void update(ServerPlayer player, BodySnapshot snapshot) {
        if (broken || !OptionalMedicalCompatibility.ragdollifiedSupported()) return;
        boolean collapse = MedicalCollapsePolicy.collapsed(snapshot.mode(), player.isAlive(), player.isSleeping(),
                player.isCreative() || player.isSpectator());
        try {
            LIFECYCLE.update(player, player.getId(), player.level(), collapse);
        } catch (ReflectiveOperationException | LinkageError error) { fail(error); }
    }
    public static void release(ServerPlayer player) {
        if (broken) return;
        try {
            LIFECYCLE.release(player);
        } catch (ReflectiveOperationException | LinkageError error) { fail(error); }
    }
    private static void fail(Throwable error) {
        broken = true;
        System.getLogger("decaying_aftermath.compat").log(System.Logger.Level.WARNING, "Medical live ragdoll bridge disabled: " + error);
    }
}
