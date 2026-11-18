package io.github.safedecimal.decayingaftermath;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DecayingAftermath implements ModInitializer {
    public static final String MOD_ID = "decaying_aftermath";
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
        LOGGER.info("Decaying Aftermath initialized.");
    }
}
