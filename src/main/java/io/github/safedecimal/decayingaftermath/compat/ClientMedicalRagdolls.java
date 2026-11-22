package io.github.safedecimal.decayingaftermath.compat;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import io.github.safedecimal.decayingaftermath.medical.BodyPart;

/** Native physics thread receives queued removal; game thread handles identity and visibility. */
public final class ClientMedicalRagdolls {
    private record Owned(UUID uuid, int serial) {}
    private record Api(Method get, Method hide, Method remove, Method persistent, Method serial,
            Method uuid, Method level, Method transform, Class<?> part) {}
    private static final Map<Integer, Owned> OWNED = new HashMap<>();
    private static Object level;
    private static Api api;
    private static boolean broken;
    private ClientMedicalRagdolls() {}
    private static Api api() throws ReflectiveOperationException {
        if (api == null) {
            Class<?> manager = Class.forName("com.raiiiden.ragdollified.client.ClientRagdollManager");
            Class<?> body = Class.forName("com.raiiiden.ragdollified.client.ClientRagdoll");
            Class<?> part = Class.forName("com.raiiiden.ragdollified.RagdollPart");
            api = new Api(manager.getMethod("get", int.class), manager.getMethod("setEntityHidden", int.class, boolean.class),
                    manager.getMethod("requestRemoveRagdoll", int.class), manager.getMethod("setPersistent", int.class, boolean.class),
                    body.getMethod("getBodySerial"), body.getMethod("getPlayerUUID"), body.getMethod("getLevel"),
                    body.getMethod("getSmoothedTransform", part), part);
        }
        return api;
    }
    public static void tick() {
        if (broken || !OptionalMedicalCompatibility.ragdollifiedSupported()) return;
        var client = Minecraft.getInstance();
        if (level != client.level) { reset(); level = client.level; }
        if (client.level == null || client.isPaused()) return;
        try {
            Api port = api();
            for (Player player : client.level.players()) {
                int id = player.getId(); var visual = ClientMedicalVisuals.forLiving(player);
                boolean collapse = visual != null && visual.collapsed() && !player.isSleeping();
                Object body = port.get().invoke(null, id); Owned owned = OWNED.get(id);
                boolean matches = body != null && player.getUUID().equals(port.uuid().invoke(body)) && port.level().invoke(body) == client.level;
                if (collapse && matches && visual.ragdollSerial() == (int)port.serial().invoke(body)) {
                    port.persistent().invoke(null, id, true); port.hide().invoke(null, id, true);
                    OWNED.put(id, new Owned(player.getUUID(), (int)port.serial().invoke(body)));
                } else if (owned != null) {
                    port.hide().invoke(null, id, false);
                    // Keep real death bodies, changed serials and reused entity identities untouched.
                    if (player.isAlive() && matches && owned.uuid().equals(player.getUUID())
                            && owned.serial() == (int)port.serial().invoke(body)) port.remove().invoke(null, id);
                    OWNED.remove(id);
                }
            }
            var iterator = OWNED.entrySet().iterator();
            while (iterator.hasNext()) {
                var entry = iterator.next(); var entity = client.level.getEntity(entry.getKey());
                if (entity == null || !entry.getValue().uuid().equals(entity.getUUID())) {
                    port.hide().invoke(null, entry.getKey(), false); iterator.remove();
                }
            }
        } catch (ReflectiveOperationException | LinkageError error) { fail(error); }
    }
    public static void reset() {
        if (api != null && !broken) {
            try { for (int id : OWNED.keySet()) api.hide().invoke(null, id, false); }
            catch (ReflectiveOperationException | LinkageError error) { fail(error); }
        }
        OWNED.clear(); level = null;
    }
    public static Vec3 origin(LivingEntity entity, BodyPart part) {
        if (broken || !OptionalMedicalCompatibility.ragdollifiedSupported()) return null;
        try {
            Api port = api(); Object body = port.get().invoke(null, entity.getId());
            if (body == null || !entity.getUUID().equals(port.uuid().invoke(body)) || port.level().invoke(body) != entity.level()) return null;
            Object nativePart = port.part().getField(part.name()).get(null);
            Object transform = port.transform().invoke(body, nativePart);
            if (transform == null) return null;
            Object position = transform.getClass().getField("position").get(transform);
            Class<?> vector = position.getClass();
            double x = vector.getField("x").getFloat(position), y = vector.getField("y").getFloat(position), z = vector.getField("z").getFloat(position);
            return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z) ? new Vec3(x, y, z) : null;
        } catch (ReflectiveOperationException | LinkageError error) { fail(error); return null; }
    }
    public static void detachDeathCamera() {
        if (broken || !OptionalMedicalCompatibility.ragdollifiedSupported()) return;
        try {Class.forName("com.raiiiden.ragdollified.api.RagdollifiedApi").getMethod("detachCamera").invoke(null);}
        catch(ReflectiveOperationException | LinkageError error) {fail(error);}
    }
    public static boolean pinDeath(int id, UUID uuid) {
        if (broken || uuid==null || !OptionalMedicalCompatibility.ragdollifiedSupported()) return false;
        try {
            Api port=api(); Object body=port.get().invoke(null,id);
            if (body==null || !uuid.equals(port.uuid().invoke(body)) || port.level().invoke(body)!=Minecraft.getInstance().level) return false;
            port.persistent().invoke(null,id,true); return true;
        } catch (ReflectiveOperationException | LinkageError error) {fail(error); return false;}
    }
    public static Vec3 deathOrigin(int id, UUID uuid, BodyPart part) {
        if (!pinDeath(id,uuid)) return null;
        try {
            Api port=api();Object body=port.get().invoke(null,id);
            Object transform=port.transform().invoke(body,port.part().getField(part.name()).get(null));
            if (transform==null) return null;
            Object position=transform.getClass().getField("position").get(transform);Class<?> vector=position.getClass();
            double x=vector.getField("x").getFloat(position),y=vector.getField("y").getFloat(position),z=vector.getField("z").getFloat(position);
            return Double.isFinite(x)&&Double.isFinite(y)&&Double.isFinite(z)?new Vec3(x,y,z):null;
        } catch (ReflectiveOperationException | LinkageError error) {fail(error); return null;}
    }
    public static void unpinDeath(int id, UUID uuid) {
        if (broken || uuid==null || !OptionalMedicalCompatibility.ragdollifiedSupported()) return;
        try {
            Api port=api();Object body=port.get().invoke(null,id);
            if(body!=null&&uuid.equals(port.uuid().invoke(body))&&port.level().invoke(body)==Minecraft.getInstance().level)
                port.persistent().invoke(null,id,false);
        } catch (ReflectiveOperationException | LinkageError error) {fail(error);}
    }
    private static void fail(Throwable error) {
        broken = true;
        System.getLogger("decaying_aftermath.compat").log(System.Logger.Level.WARNING, "Medical client ragdoll bridge disabled: " + error);
    }
}
