package io.github.safedecimal.decayingaftermath.medical;

import java.util.Random;

/** CU AEDMinigame/ManualDefibMinigame interactions, timed by the Minecraft server.
 * Values are fictional game/UI units, not instructions for a real device. */
public final class DefibGame {
    public static final int PAD_0=0, PAD_1=1, MOVE=2, RELEASE=3, CONFIRM=4, CANCEL=5,
            DIAL=6, CHARGE=7, SHOCK=8;
    public static final int ANALYZE_TICKS=85, AED_CHARGE_TICKS=70;
    public static final float PAD_RADIUS=150, DIAL_X=-225, DIAL_Y=-174, DIAL_RADIUS=75;
    public enum Stage { PLACING, ANALYZING, READY, CHARGING, COMPLETE, NO_SHOCK }
    public enum Result { MISS, PROGRESS, SHOCK }
    private final boolean automatic;
    private final float[] padX=new float[2],padY=new float[2];
    private final float rangeOffset;
    private Stage stage;
    private int held=-1,shocks;
    private float offsetX,offsetY,currentCharge,desiredCharge=100,battery,lastShockCharge;
    private long stageAt,lastTick;

    public DefibGame(boolean automatic,int seed,long started) { this(automatic,seed,started,1); }
    public DefibGame(boolean automatic,int seed,long started,float battery) {
        this.automatic=automatic;this.battery=sanitizeBattery(battery);stageAt=lastTick=started;
        stage=automatic?Stage.PLACING:Stage.READY;
        var random=new Random(seed);
        rangeOffset=random.nextFloat()*2-1;
        for(int i=0;i<2;i++) {
            double angle=random.nextDouble()*Math.PI*2,radius=Math.sqrt(random.nextDouble())*150;
            padX[i]=(float)(Math.cos(angle)*radius);padY[i]=(float)(Math.sin(angle)*radius);
        }
        if(automatic)drain(.01f); // AEDMinigame.Start
    }
    public Stage stage() {return stage;}
    public long stageAt() {return stageAt;}
    public int held() {return held;}
    public int shocks() {return shocks;}
    public float padX(int pad) {return padX[pad];}
    public float padY(int pad) {return padY[pad];}
    public float currentCharge() {return currentCharge;}
    public float desiredCharge() {return desiredCharge;}
    public float battery() {return battery;}
    public float lastShockCharge() {return lastShockCharge;}
    public int pads() {return (padPlaced(0)?1:0)+(padPlaced(1)?1:0);}
    public boolean padPlaced(int pad) {
        return Math.hypot(padX[pad]-(pad==0?-296:273),padY[pad]-(pad==0?153:-141))<PAD_RADIUS;
    }
    /** Only a previously pressed AED button can become due; analysis never fires a shock. */
    public boolean autoReady(long now) {
        return automatic&&stage==Stage.CHARGING&&now-stageAt>AED_CHARGE_TICKS;
    }
    public Result input(int control,long now) {return input(control,0,0,now);}

    public Result input(int control,float x,float y,long now) {
        if(!validPoint(x,y)||now<lastTick||battery<=0)return Result.MISS;
        if(!automatic)tick(now,false);
        if(automatic&&stage==Stage.PLACING) {
            if(control==PAD_0||control==PAD_1) {
                if(held>=0||!padHit(control,x,y))return Result.MISS;
                held=control;offsetX=padX[held]-x;offsetY=padY[held]-y;
                return Result.PROGRESS;
            }
            if(control==MOVE&&held>=0) {movePad(x,y);return Result.PROGRESS;}
            if(control==RELEASE&&held>=0) {
                movePad(x,y);held=-1;
                if(pads()==2) {stage=Stage.ANALYZING;stageAt=now;}
                return Result.PROGRESS;
            }
            return Result.MISS;
        }
        if(automatic) {
            // A no-shock advisory cannot be overridden; request a fresh analysis instead.
            if(control!=CONFIRM||stage.ordinal()<Stage.READY.ordinal()||stage==Stage.CHARGING
                    ||!hit(x,y,0,-421,141,141))return Result.MISS;
            if(stage==Stage.NO_SHOCK) {stage=Stage.ANALYZING;stageAt=now;return Result.PROGRESS;}
            stage=Stage.CHARGING;stageAt=now;return Result.PROGRESS;
        }
        if(control==DIAL&&hit(x,y,DIAL_X,DIAL_Y,150,150)) {
            held=2;turnDial(x,y);return Result.PROGRESS;
        }
        if(control==MOVE&&held==2) {turnDial(x,y);return Result.PROGRESS;}
        if(control==RELEASE&&held==2) {turnDial(x,y);held=-1;return Result.PROGRESS;}
        if(control==CHARGE&&hit(x,y,8,-174,111,111)) {
            stage=stage==Stage.CHARGING?Stage.READY:Stage.CHARGING;
            return Result.PROGRESS;
        }
        if(control==SHOCK&&currentCharge>=10&&hit(x,y,234.3f,-174,111,111)) {
            lastShockCharge=currentCharge;drain(currentCharge/4000);
            currentCharge=0;stage=Stage.READY;shocks++;return Result.SHOCK;
        }
        return Result.MISS;
    }
    public Result tick(long now,boolean shockable) {
        if(now<lastTick||battery<=0)return Result.MISS;
        long delta=now-lastTick;lastTick=now;
        if(!automatic) {
            drain(delta/(20f*800));
            if(stage==Stage.CHARGING) {
                if(currentCharge<desiredCharge)currentCharge=Math.min(desiredCharge,currentCharge+delta*2);
                if(currentCharge>=desiredCharge)stage=Stage.READY;
            }
        } else if(stage==Stage.ANALYZING&&now-stageAt>ANALYZE_TICKS) {
            stage=shockable?Stage.READY:Stage.NO_SHOCK;stageAt=now;drain(.02f);
        } else if(autoReady(now)) {
            // Rhythm can change while charging. Re-check at the actual delivery tick.
            if(!shockable) {stage=Stage.NO_SHOCK;stageAt=now;return Result.PROGRESS;}
            stage=Stage.COMPLETE;stageAt=now;drain(.14f);shocks++;return Result.SHOCK;
        }
        return Result.PROGRESS;
    }
    private void movePad(float x,float y) {
        padX[held]=Math.clamp(x+offsetX,-524,524);padY[held]=Math.clamp(y+offsetY,-524,524);
    }
    private void turnDial(float x,float y) {
        float angle=(float)Math.toDegrees(Math.atan2(y-DIAL_Y,x-DIAL_X))-90;
        angle=(angle+540)%360-180;
        desiredCharge=chargeForAngle(angle);
    }
    public static float chargeForAngle(float angle) {
        if(!Float.isFinite(angle))return 100;
        return 200-(Math.clamp(angle,-170,170)+170)*190/340;
    }
    public static float angleForCharge(float amount) {return (200-Math.clamp(amount,10,200))*340/190-170;}
    public boolean padHit(int pad,float x,float y) {return hit(x,y,padX[pad],padY[pad],267,pad==0?342:276);}
    public static boolean validPoint(float x,float y) {
        return Float.isFinite(x)&&Float.isFinite(y)&&Math.abs(x)<=524&&Math.abs(y)<=524;
    }
    public static boolean hit(float x,float y,float cx,float cy,float width,float height) {
        return Math.abs(x-cx)<=width/2&&Math.abs(y-cy)<=height/2;
    }
    private void drain(float amount) {battery=Math.max(0,battery-amount);}
    public static float sanitizeBattery(float value) {return Float.isFinite(value)?Math.clamp(value,0,1):0;}
    public float chanceToWork(float fibrillation) {
        return automatic?1:Math.clamp(1-Math.abs(fibrillation-lastShockCharge*.5f)/40,0,1);
    }
    public int estimateLow(float fibrillation) {return Math.round(Math.clamp(fibrillation*2-70+rangeOffset*70,10,200));}
    public int estimateHigh(float fibrillation) {return Math.round(Math.clamp(fibrillation*2+70+rangeOffset*70,10,200));}
    /** Adapter: Minecraft has a signed rhythm timer, not CU's fibrillationProgress/INT skill. */
    public static float fibrillation(int cardiacTicks) {
        if(cardiacTicks<0)return 100;
        return cardiacTicks>=40&&cardiacTicks<200?(cardiacTicks-40)*100f/160:0;
    }
}
