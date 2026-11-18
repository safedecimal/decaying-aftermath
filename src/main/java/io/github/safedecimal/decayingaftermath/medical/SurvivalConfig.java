package io.github.safedecimal.decayingaftermath.medical;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.safedecimal.decayingaftermath.DecayingAftermath;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Server-side balancing switches. Never silently overwrites an invalid player config. */
public final class SurvivalConfig {
    public boolean metabolism=true,temperature=true,encumbrance=true;
    public float carryingCapacity=48,backpackCapacityBonus=24,blockMass=.25f,ordinaryItemMass=.05f;
    public Map<String,Float> itemMass=new HashMap<>();
    /** Explicit block emissions in Gy/second; vanilla blocks are not radioactive by default. */
    public Map<String,Float> radiationSources=new HashMap<>();
    private static volatile SurvivalConfig current=new SurvivalConfig();
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
    private SurvivalConfig() { }
    public static SurvivalConfig get(){return current;}
    public static void load() {
        Path path=FabricLoader.getInstance().getConfigDir().resolve("decaying-aftermath-survival.json");
        try {
            SurvivalConfig loaded;
            if(Files.exists(path)) loaded=GSON.fromJson(Files.readString(path,StandardCharsets.UTF_8),SurvivalConfig.class);
            else {
                loaded=new SurvivalConfig(); Files.createDirectories(path.getParent());
                Files.writeString(path,GSON.toJson(loaded)+"\n",StandardCharsets.UTF_8);
            }
            if(loaded==null)throw new IllegalArgumentException("Empty survival config");
            loaded.carryingCapacity=checked(loaded.carryingCapacity,1,1000,"carryingCapacity");
            loaded.backpackCapacityBonus=checked(loaded.backpackCapacityBonus,0,1000,"backpackCapacityBonus");
            loaded.blockMass=checked(loaded.blockMass,0,1000,"blockMass");
            loaded.ordinaryItemMass=checked(loaded.ordinaryItemMass,0,1000,"ordinaryItemMass");
            if(loaded.itemMass==null)throw new IllegalArgumentException("itemMass must be an object");
            loaded.itemMass.forEach((id,mass)->{
                if(id==null||!id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")||mass==null)
                    throw new IllegalArgumentException("Invalid itemMass entry");
                checked(mass,0,1000,"itemMass."+id);
            });
            if(loaded.radiationSources==null)throw new IllegalArgumentException("radiationSources must be an object");
            loaded.radiationSources.forEach((id,rate)->{
                if(id==null||!id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")||rate==null)
                    throw new IllegalArgumentException("Invalid radiationSources entry");
                checked(rate,0,100,"radiationSources."+id);
            });
            current=loaded;
        } catch(Exception ex) {
            DecayingAftermath.LOGGER.warn("Cannot load {}. Keeping last valid survival config; file untouched.",path,ex);
        }
    }
    private static float checked(float value,float min,float max,String key) {
        if(!Float.isFinite(value)||value<min||value>max)throw new IllegalArgumentException("Invalid "+key);
        return value;
    }
}
