package io.github.safedecimal.decayingaftermath;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DecayingAftermath implements ModInitializer {
    public static final String MOD_ID = "decaying_aftermath";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Decaying Aftermath initialized.");
    }
}
