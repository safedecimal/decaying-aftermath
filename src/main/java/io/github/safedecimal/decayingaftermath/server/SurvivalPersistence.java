package io.github.safedecimal.decayingaftermath.server;

import io.github.safedecimal.decayingaftermath.medical.*;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Body-part and survival save data, including migration of older untyped bleeding. */
public final class SurvivalPersistence {
    static final int SAVE_VERSION=6;
    private SurvivalPersistence(){ }
    public static void save(ValueOutput node,BodyModel.SavedState state) {
        node.putBoolean("faint_recovered",state.faintRecovered());
        var s=state.survivalState();var v=s.snapshot();ValueOutput out=node.child("survival");
        out.putFloat("hydration",v.hydration());out.putFloat("nutrition",v.nutrition());out.putFloat("temperature",v.temperature());
        out.putFloat("fatigue",v.fatigue());out.putFloat("toxin",v.toxin());out.putFloat("plasma",v.plasma());out.putFloat("red_cells",v.redCells());
        out.putFloat("antibiotic",v.antibiotic());out.putFloat("stimulant",v.stimulant());out.putFloat("antagonist",v.antagonist());
        out.putFloat("body_mass_kg",v.bodyMassKg());out.putFloat("radiation_gy",v.radiationGy());
        out.putFloat("blood_lost_liters",v.bloodLostLiters());
        out.putFloat("coagulant",v.coagulant());out.putFloat("gut_infection",v.gutInfection());
        out.putFloat("mood_reserve",v.moodReserve());out.putInt("depression",v.depression().ordinal());
        out.putFloat("antidepressant",v.antidepressant());out.putFloat("anxiolytic",v.anxiolytic());
        out.putFloat("therapy_progress",v.therapyProgress());out.putInt("mood_factors",v.moodFactors());
        out.putFloat("light_deficit",s.lightDeficit());out.putFloat("situational_stress",s.situationalStress());
        out.putFloat("chronic_burden",s.chronicBurden());out.putFloat("psychosis_load",s.psychosisLoad());
        out.putFloat("ptsd_load",v.ptsdLoad());out.putInt("remembered_deaths",v.rememberedDeaths());
        out.putBoolean("ptsd_flashback",v.ptsdFlashback());
        out.putInt("ptsd_flashback_ticks",s.ptsdFlashbackTicks());out.putInt("ptsd_flashback_cooldown",s.ptsdFlashbackCooldown());
        out.putFloat("pending_food",s.pendingFood());out.putFloat("pending_water",s.pendingWater());
        out.putFloat("pending_germs",s.pendingGerms());out.putInt("incubation",s.incubationTicks());
        var w=state.wounds();out.putFloat("lung_collapse",w.lungCollapse());out.putBoolean("chest_drain",w.chestDrain());
        float[] burns=w.burns(),bleed=w.internalBleeding();boolean[] open=w.openWounds(),foreign=w.foreignBodies(),cold=w.coldInjuries();
        var suppressed=w.suppressedFlows();
        float[] skinCare=state.skinHealAmount(),bleedCare=state.bandageSlowAmount(),rawPain=state.rawLocalPain();
        int[] bone=w.boneHealing(),cool=w.coolingTicks();
        for(BodyPart part:BodyPart.values()) {
            int i=part.ordinal();ValueOutput p=node.child("part"+i);
            var limb=state.snapshot().part(part);
            p.putFloat("integrity",limb.integrity());p.putFloat("bleeding",limb.bleeding());p.putFloat("pain",limb.pain());
            p.putBoolean("fractured",limb.fractured());p.putBoolean("bandaged",limb.bandaged());
            p.putBoolean("splinted",limb.splinted());p.putBoolean("tourniquet",limb.tourniquet());
            p.putFloat("infection",limb.infection());p.putFloat("raw_pain",rawPain[i]);
            p.putFloat("skin_care",skinCare[i]);p.putFloat("bleed_care",bleedCare[i]);
            putFlow(p,"bleed_",limb.externalBleeding());
            p.putFloat("burn",burns[i]);p.putFloat("internal_bleed",bleed[i]);p.putBoolean("open_wound",open[i]);
            p.putBoolean("foreign_body",foreign[i]);p.putInt("bone_heal",bone[i]);p.putInt("cooling_ticks",cool[i]);
            p.putFloat("tourniquet_residual",suppressed[i].total());
            putFlow(p,"tourniquet_",suppressed[i]);
            p.putBoolean("cold_injury",cold[i]);
        }
    }
    public static BodyModel.SavedState load(ValueInput node,BodyModel.SavedState legacy) {
        int version=node.getIntOr("version",0);
        if(version<3)return legacy;
        var parts=legacy.snapshot().parts();
        var oldSuppressed=legacy.wounds().suppressedFlows();
        ValueInput s=node.childOrEmpty("survival");int count=BodyPart.values().length;
        float[] burns=new float[count],bleed=new float[count];boolean[] open=new boolean[count],foreign=new boolean[count];
        int[] bone=new int[count],cool=new int[count];var suppressed=new BleedProfile[count];boolean[] cold=new boolean[count];float internal=0;
        for(BodyPart part:BodyPart.values()) {
            int i=part.ordinal();ValueInput p=node.childOrEmpty("part"+i);burns[i]=number(p,"burn",0,0,1);
            bleed[i]=number(p,"internal_bleed",0,0,1);internal+=bleed[i];open[i]=p.getBooleanOr("open_wound",legacy.snapshot().part(part).openWound());
            foreign[i]=p.getBooleanOr("foreign_body",false);bone[i]=Math.clamp(p.getIntOr("bone_heal",0),0,1_000_000);
            cool[i]=Math.clamp(p.getIntOr("cooling_ticks",0),0,2400);
            var residual=BleedProfile.legacy(number(p,"tourniquet_residual",oldSuppressed[i].total(),0,1));
            suppressed[i]=version>=4?readFlow(p,"tourniquet_",residual):residual;
            if(version>=4)parts[i]=parts[i].withExternalBleeding(readFlow(p,"bleed_",parts[i].externalBleeding()));
            cold[i]=p.getBooleanOr("cold_injury",false);
        }
        float collapse=number(s,"lung_collapse",0,0,1),blood=legacy.snapshot().blood();
        int depIdx=Math.clamp(s.getIntOr("depression",0),0,DepressionType.values().length-1);
        SurvivalSnapshot snapshot=new SurvivalSnapshot(number(s,"hydration",1,0,1),number(s,"nutrition",1,0,1),
                number(s,"temperature",37,25,45),number(s,"fatigue",0,0,1),number(s,"toxin",0,0,3),
                number(s,"plasma",blood,0,1),number(s,"red_cells",blood,0,1),number(s,"antibiotic",0,0,3),
                number(s,"stimulant",0,0,3),number(s,"antagonist",0,0,3),number(s,"coagulant",0,0,3),
                internal,1-collapse,0,number(s,"gut_infection",0,0,1),
                number(s,"body_mass_kg",SurvivalSnapshot.DEFAULT_BODY_MASS_KG,35,250),
                number(s,"radiation_gy",0,0,100),number(s,"blood_lost_liters",0,0,10000),
                number(s,"mood_reserve",1,-1,1),DepressionType.values()[depIdx],
                number(s,"antidepressant",0,0,3),number(s,"anxiolytic",0,0,3),
                number(s,"therapy_progress",0,0,1),s.getIntOr("mood_factors",0),
                number(s,"ptsd_load",0,0,1),Math.clamp(s.getIntOr("remembered_deaths",0),0,1_000_000),
                s.getBooleanOr("ptsd_flashback",false));
        WoundState wounds=new WoundState(burns,bleed,open,foreign,bone,cool,collapse,s.getBooleanOr("chest_drain",false),suppressed,cold);
        int flashbackTicks=Math.clamp(s.getIntOr("ptsd_flashback_ticks",snapshot.ptsdFlashback()?120:0),0,12000);
        int flashbackCooldown=Math.clamp(s.getIntOr("ptsd_flashback_cooldown",0),0,12000);
        var survival=new SurvivalModel.SavedState(snapshot,number(s,"pending_food",0,0,1),number(s,"pending_water",0,0,1),
                number(s,"pending_germs",0,0,1),Math.clamp(s.getIntOr("incubation",0),0,12000),
                number(s,"light_deficit",0,0,1),number(s,"situational_stress",0,0,1),
                number(s,"chronic_burden",0,0,1),number(s,"psychosis_load",0,0,1),
                flashbackTicks,flashbackCooldown);
        return new BodyModel.SavedState(legacy.snapshot().withParts(parts),legacy.careTicks(),legacy.regenerationTicks(),legacy.tourniquetTicks(),
                legacy.lastStandingSpent(),legacy.stableTicks(),legacy.lastStandingTicks(),legacy.lastStandingBoostTicks(),
                legacy.dyingTicks(),legacy.disinfectionTicks(),legacy.anestheticTicks(),legacy.infectionAgeTicks(),
                legacy.skinHealAmount(),legacy.bandageSlowAmount(),legacy.rawLocalPain(),wounds,survival,node.getBooleanOr("faint_recovered",false));
    }
    private static void putFlow(ValueOutput node,String prefix,BleedProfile flow) {
        node.putFloat(prefix+"capillary",flow.capillary());
        node.putFloat(prefix+"venous",flow.venous());
        node.putFloat(prefix+"arterial",flow.arterial());
    }
    private static BleedProfile readFlow(ValueInput node,String prefix,BleedProfile fallback) {
        float capillary=number(node,prefix+"capillary",Float.NaN,0,1);
        float venous=number(node,prefix+"venous",Float.NaN,0,1);
        float arterial=number(node,prefix+"arterial",Float.NaN,0,1);
        return Float.isNaN(capillary+venous+arterial)?fallback:new BleedProfile(capillary,venous,arterial);
    }
    private static float number(ValueInput node,String key,float fallback,float minimum,float maximum) {
        float v=node.getFloatOr(key,fallback);return Float.isFinite(v)?Math.clamp(v,minimum,maximum):fallback;
    }
}
