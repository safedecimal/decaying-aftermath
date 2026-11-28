package io.github.safedecimal.decayingaftermath;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DecayingAftermath implements ModInitializer {
    public static final String MOD_ID = "abjection";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        io.github.safedecimal.decayingaftermath.medical.MedicalItems.register();
        io.github.safedecimal.decayingaftermath.medical.SurvivalItems.register();
        io.github.safedecimal.decayingaftermath.medical.SurvivalConfig.load();
        io.github.safedecimal.decayingaftermath.medical.MedicalLoot.register();
        io.github.safedecimal.decayingaftermath.server.ServerMedicalSystem.register();
        io.github.safedecimal.decayingaftermath.server.SurvivalWaterSources.register();
        io.github.safedecimal.decayingaftermath.server.MedicalCommands.register();
        io.github.safedecimal.decayingaftermath.compat.MedicalVisualNetwork.register();
        io.github.safedecimal.decayingaftermath.network.MedicalNetwork.register();
        io.github.safedecimal.decayingaftermath.network.DefibNetwork.register();
        io.github.safedecimal.decayingaftermath.network.SelfHarmNetwork.register();
        io.github.safedecimal.decayingaftermath.server.ServerAftermathSystem.register();
        io.github.safedecimal.decayingaftermath.server.ServerSuicideSystem.register();
        LOGGER.info("Abjection initialized.");
    }
}
