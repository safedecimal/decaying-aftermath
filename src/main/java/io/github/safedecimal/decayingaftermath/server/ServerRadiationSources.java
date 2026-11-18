package io.github.safedecimal.decayingaftermath.server;

import io.github.safedecimal.decayingaftermath.medical.SurvivalConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;

/** Only explicitly configured blocks emit radiation. Sampled by the authoritative server. */
public final class ServerRadiationSources {
    private ServerRadiationSources() { }
    public static float dosePerTick(ServerPlayer player) {
        var sources=SurvivalConfig.get().radiationSources;
        if(sources.isEmpty())return 0;
        float rate=0;var origin=player.blockPosition();
        for(int x=-2;x<=2;x++)for(int y=-2;y<=2;y++)for(int z=-2;z<=2;z++) {
            var block=player.level().getBlockState(origin.offset(x,y,z)).getBlock();
            float emission=sources.getOrDefault(BuiltInRegistries.BLOCK.getKey(block).toString(),0f);
            rate+=emission/(1+x*x+y*y+z*z);
        }
        return Math.min(100,rate/20);
    }
}
