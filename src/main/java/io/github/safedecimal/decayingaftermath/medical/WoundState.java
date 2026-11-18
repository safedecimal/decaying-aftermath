package io.github.safedecimal.decayingaftermath.medical;

import java.util.Objects;

/** Persistent trauma details. Arrays never alias a saved state or a caller's mutable buffers. */
public record WoundState(float[] burns,float[] internalBleeding,boolean[] openWounds,boolean[] foreignBodies,
        int[] boneHealing,int[] coolingTicks,float lungCollapse,boolean chestDrain,float[] suppressedBleeding,boolean[] coldInjuries) {
    public WoundState(float[] burns,float[] internalBleeding,boolean[] openWounds,boolean[] foreignBodies,
            int[] boneHealing,int[] coolingTicks,float lungCollapse,boolean chestDrain,float[] suppressedBleeding) {
        this(burns,internalBleeding,openWounds,foreignBodies,boneHealing,coolingTicks,lungCollapse,chestDrain,suppressedBleeding,new boolean[COUNT]);
    }
    public WoundState(float[] burns,float[] internalBleeding,boolean[] openWounds,boolean[] foreignBodies,
            int[] boneHealing,int[] coolingTicks,float lungCollapse,boolean chestDrain) {
        this(burns,internalBleeding,openWounds,foreignBodies,boneHealing,coolingTicks,lungCollapse,chestDrain,new float[COUNT]);
    }
    private static final int COUNT=BodyPart.values().length;
    public WoundState {
        burns=floats(burns); internalBleeding=floats(internalBleeding);
        openWounds=flags(openWounds); foreignBodies=flags(foreignBodies);
        boneHealing=timers(boneHealing); coolingTicks=timers(coolingTicks);
        suppressedBleeding=floats(suppressedBleeding);
        coldInjuries=flags(coldInjuries);
        if (!Float.isFinite(lungCollapse)) throw new IllegalArgumentException("Invalid lung collapse");
        lungCollapse=Math.clamp(lungCollapse,0,1);
    }
    public static WoundState legacy(BodySnapshot body) {
        float[] burns=new float[COUNT],bleeds=new float[COUNT]; boolean[] open=new boolean[COUNT],foreign=new boolean[COUNT];
        int[] bones=new int[COUNT];float[] suppressed=new float[COUNT];boolean[] cold=new boolean[COUNT];
        for(BodyPart part:BodyPart.values()) {
            var p=body.part(part); int i=part.ordinal(); burns[i]=p.burn(); bleeds[i]=p.internalBleeding();
            open[i]=p.openWound(); foreign[i]=p.foreignBody(); bones[i]=Math.round(p.boneRecovery()*BodyModel.BONE_HEAL_TICKS);
            if(p.tourniquet())suppressed[i]=Math.clamp((1-p.integrity())*.18f,0,1); // Legacy save had no residual-flow field.
            cold[i]=p.coldInjury();
        }
        return new WoundState(burns,bleeds,open,foreign,bones,new int[COUNT],1-body.survival().lungFunction(),false,suppressed,cold);
    }
    @Override public float[] burns(){return burns.clone();}
    @Override public float[] internalBleeding(){return internalBleeding.clone();}
    @Override public boolean[] openWounds(){return openWounds.clone();}
    @Override public boolean[] foreignBodies(){return foreignBodies.clone();}
    @Override public int[] boneHealing(){return boneHealing.clone();}
    @Override public int[] coolingTicks(){return coolingTicks.clone();}
    @Override public float[] suppressedBleeding(){return suppressedBleeding.clone();}
    @Override public boolean[] coldInjuries(){return coldInjuries.clone();}
    private static float[] floats(float[] a) {
        Objects.requireNonNull(a); if(a.length!=COUNT)throw new IllegalArgumentException("Six wound values required");
        float[] c=a.clone(); for(int i=0;i<c.length;i++){if(!Float.isFinite(c[i]))throw new IllegalArgumentException("Invalid wound");c[i]=Math.clamp(c[i],0,1);} return c;
    }
    private static boolean[] flags(boolean[] a){Objects.requireNonNull(a);if(a.length!=COUNT)throw new IllegalArgumentException("Six wound flags required");return a.clone();}
    private static int[] timers(int[] a){Objects.requireNonNull(a);if(a.length!=COUNT)throw new IllegalArgumentException("Six wound timers required");int[] c=a.clone();for(int i=0;i<c.length;i++)c[i]=Math.clamp(c[i],0,1_000_000);return c;}
}
