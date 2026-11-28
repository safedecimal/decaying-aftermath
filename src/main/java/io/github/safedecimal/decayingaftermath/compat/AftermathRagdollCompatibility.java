package io.github.safedecimal.decayingaftermath.compat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import io.github.safedecimal.decayingaftermath.server.ServerMedicalSystem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/** Extend the real death packet through the temporary spectator respawn, never start a live fake. */
public final class AftermathRagdollCompatibility {
    private static final int HEAD_BIT = 1 << 1; // RagdollPart.HEAD.index == 1
    private record Api(Field retained, Field packet, Field serial, Method copy, Method register, Method severedMask) { }
    private static Api api;
    private static boolean broken;
    private static boolean registered;
    private AftermathRagdollCompatibility() { }

    public static void register() {
        if (registered || broken || !OptionalMedicalCompatibility.ragdollifiedSupported()) return;
        registered = true;
        try {
            Class<?> serverApi = Class.forName("com.raiiiden.ragdollified.api.RagdollifiedServerApi");
            Class<?> providerType = Class.forName("com.raiiiden.ragdollified.api.SeveredPartProvider");
            Class<?> partEnum = Class.forName("com.raiiiden.ragdollified.RagdollPart");
            Object headPart = partEnum.getField("HEAD").get(null);
            int headBit = (int) partEnum.getMethod("bit").invoke(headPart);
            Object provider = Proxy.newProxyInstance(providerType.getClassLoader(), new Class<?>[]{providerType},
                    (proxy, method, args) -> {
                        if (method.getName().equals("severedPartMask") && args != null && args.length == 1
                                && args[0] instanceof ServerPlayer player
                                && ServerMedicalSystem.get(player).headBlownOff()) {
                            return headBit;
                        }
                        return 0;
                    });
            serverApi.getMethod("addSeveredPartProvider", providerType).invoke(null, provider);
        } catch (ReflectiveOperationException | LinkageError error) {
            broken = true;
            System.getLogger("abjection.compat").log(System.Logger.Level.WARNING,
                    "Cannot register Ragdollified severed-part provider: " + error);
        }
    }

    private static Api api() throws ReflectiveOperationException {
        if(api==null) {
            Class<?> manager=Class.forName("com.raiiiden.ragdollified.server.ServerRagdollSyncManager");
            Class<?> retained=Class.forName(manager.getName()+"$RetainedRagdoll");
            Class<?> packet=Class.forName("com.raiiiden.ragdollified.network.RagdollSpawnPacket");
            Field map=manager.getDeclaredField("RETAINED"),spawn=retained.getDeclaredField("spawnPacket"),serial=retained.getDeclaredField("bodySerial");
            map.setAccessible(true);spawn.setAccessible(true);serial.setAccessible(true);
            api=new Api(map,spawn,serial,packet.getMethod("copy"),
                    manager.getMethod("register",LivingEntity.class,packet,int.class),
                    packet.getMethod("severedMask",int.class));
        }
        return api;
    }
    public static int lifetime(int remainingTicks) {return Math.clamp(remainingTicks,0,72000)+1200;}
    public static int retainDeath(ServerPlayer deadPlayer,int remainingTicks) {
        if(broken || !OptionalMedicalCompatibility.ragdollifiedSupported())return 0;
        try {
            Api port=api();Map<?,?> bodies=(Map<?,?>)port.retained.get(null);
            Object retained=bodies.get(deadPlayer.getId());if(retained==null)return 0;
            Object packet=port.copy.invoke(port.packet.get(retained));
            if (ServerMedicalSystem.get(deadPlayer).headBlownOff()) {
                port.severedMask.invoke(packet, HEAD_BIT);
            }
            // register, unlike registerLive, creates a true dead-player ragdoll. Its packet
            // retains the original skin/equipment/motion and gets a fresh native body serial.
            port.register.invoke(null,deadPlayer,packet,lifetime(remainingTicks));
            return port.serial.getInt(bodies.get(deadPlayer.getId()));
        } catch(ReflectiveOperationException | LinkageError error) {
            broken=true;
            System.getLogger("abjection.compat").log(System.Logger.Level.WARNING,
                    "Cannot extend native Ragdollified death body: "+error);return 0;
        }
    }
}
