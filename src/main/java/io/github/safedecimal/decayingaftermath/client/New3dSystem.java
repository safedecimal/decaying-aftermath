package io.github.safedecimal.decayingaftermath.client;

import io.github.safedecimal.decayingaftermath.network.Try3dPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Client-side toggle state for /trynew3dsystem 3D minigame mode. */
public final class New3dSystem {
    private static boolean registered;
    private static boolean enabled;

    private New3dSystem() { }

    public static void register() {
        if (registered) return;
        registered = true;
        ClientPlayNetworking.registerGlobalReceiver(Try3dPayload.TYPE, (payload, context) ->
                context.client().execute(() -> enabled = payload.enabled()));
    }

    public static boolean enabled() { return enabled; }
    public static void set(boolean value) { enabled = value; }
    public static boolean toggle() { enabled = !enabled; return enabled; }
}
