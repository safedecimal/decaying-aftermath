package io.github.safedecimal.decayingaftermath.server;

import io.github.safedecimal.decayingaftermath.medical.*;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Version-3 extensions; old bodies default to hydrated/nourished with matching blood components. */
public final class SurvivalPersistence {
    private SurvivalPersistence(){ }
    public static void save(ValueOutput node,BodyModel.SavedState state) {
        var s=state.survivalState();var v=s.snapshot();ValueOutput out=node.child("survival");
        out.putFloat("hydration",v.hydration());out.putFloat("nutrition",v.nutrition());out.putFloat("temperature",v.temperature());
        out.putFloat("fatigue",v.fatigue());out.putFloat("toxin",v.toxin());out.putFloat("plasma",v.plasma());out.putFloat("red_cells",v.redCells());
        out.putFloat("antibiotic",v.antibiotic());out.putFloat("stimulant",v.stimulant());out.putFloat("antagonist",v.antagonist());
        out.putFloat("coagulant",v.coagulant());out.putFloat("gut_infection",v.gutInfection());
        out.putFloat("pending_food",s.pendingFood());out.putFloat("pending_water",s.pendingWater());
        out.putFloat("pending_germs",s.pendingGerms());out.putInt("incubation",s.incubationTicks());
        var w=state.wounds();out.putFloat("lung_collapse",w.lungCollapse());out.putBoolean("chest_drain",w.chestDrain());
        float[] burns=w.burns(),bleed=w.internalBleeding(),suppressed=w.suppressedBleeding();boolean[] open=w.openWounds(),foreign=w.foreignBodies(),cold=w.coldInjuries();
        int[] bone=w.boneHealing(),cool=w.coolingTicks();
        for(BodyPart part:BodyPart.values()) {
            int i=part.ordinal();ValueOutput p=node.child("part"+i);
            p.putFloat("burn",burns[i]);p.putFloat("internal_bleed",bleed[i]);p.putBoolean("open_wound",open[i]);
            p.putBoolean("foreign_body",foreign[i]);p.putInt("bone_heal",bone[i]);p.putInt("cooling_ticks",cool[i]);
            p.putFloat("tourniquet_residual",suppressed[i]);
            p.putBoolean("cold_injury",cold[i]);
        }
    }
    public static BodyModel.SavedState load(ValueInput node,BodyModel.SavedState legacy) {
        if(node.getIntOr("version",0)<3)return legacy;
        ValueInput s=node.childOrEmpty("survival");int count=BodyPart.values().length;
        float[] burns=new float[count],bleed=new float[count];boolean[] open=new boolean[count],foreign=new boolean[count];
        int[] bone=new int[count],cool=new int[count];float[] suppressed=new float[count];boolean[] cold=new boolean[count];float internal=0;
        for(BodyPart part:BodyPart.values()) {
            int i=part.ordinal();ValueInput p=node.childOrEmpty("part"+i);burns[i]=number(p,"burn",0,0,1);
            bleed[i]=number(p,"internal_bleed",0,0,1);internal+=bleed[i];open[i]=p.getBooleanOr("open_wound",legacy.snapshot().part(part).openWound());
            foreign[i]=p.getBooleanOr("foreign_body",false);bone[i]=Math.clamp(p.getIntOr("bone_heal",0),0,1_000_000);
            cool[i]=Math.clamp(p.getIntOr("cooling_ticks",0),0,2400);
            suppressed[i]=number(p,"tourniquet_residual",legacy.wounds().suppressedBleeding()[i],0,1);
            cold[i]=p.getBooleanOr("cold_injury",false);
        }
        float collapse=number(s,"lung_collapse",0,0,1),blood=legacy.snapshot().blood();
        SurvivalSnapshot snapshot=new SurvivalSnapshot(number(s,"hydration",1,0,1),number(s,"nutrition",1,0,1),
                number(s,"temperature",37,25,45),number(s,"fatigue",0,0,1),number(s,"toxin",0,0,3),
                number(s,"plasma",blood,0,1),number(s,"red_cells",blood,0,1),number(s,"antibiotic",0,0,3),
                number(s,"stimulant",0,0,3),number(s,"antagonist",0,0,3),number(s,"coagulant",0,0,3),
                internal,1-collapse,0,number(s,"gut_infection",0,0,1));
        WoundState wounds=new WoundState(burns,bleed,open,foreign,bone,cool,collapse,s.getBooleanOr("chest_drain",false),suppressed,cold);
        var survival=new SurvivalModel.SavedState(snapshot,number(s,"pending_food",0,0,1),number(s,"pending_water",0,0,1),
                number(s,"pending_germs",0,0,1),Math.clamp(s.getIntOr("incubation",0),0,12000));
        return new BodyModel.SavedState(legacy.snapshot(),legacy.careTicks(),legacy.regenerationTicks(),legacy.tourniquetTicks(),
                legacy.lastStandingSpent(),legacy.stableTicks(),legacy.lastStandingTicks(),legacy.lastStandingBoostTicks(),
                legacy.dyingTicks(),legacy.disinfectionTicks(),legacy.anestheticTicks(),legacy.infectionAgeTicks(),
                legacy.skinHealAmount(),legacy.bandageSlowAmount(),legacy.rawLocalPain(),wounds,survival);
    }
    private static float number(ValueInput node,String key,float fallback,float minimum,float maximum) {
        float v=node.getFloatOr(key,fallback);return Float.isFinite(v)?Math.clamp(v,minimum,maximum):fallback;
    }
}
