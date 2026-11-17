package io.github.safedecimal.decayingaftermath.client;

/** MinigameBase.UpdateHandPhysics: 60 Hz, velocity lerp, drag and authored 1/4 UI units. */
public final class CuHandMotion {
    public static final double STEP=1.0/60, UI_SCALE=.25;
    private double x,y,vx,vy,previousX,previousY;
    private long steps;
    public double x() { return 128+x*UI_SCALE; }
    public double y() { return 128+y*UI_SCALE; }
    public double velocityX() { return vx; }
    public void beginTick() {previousX=x;previousY=y;}
    public void step(double targetX,double targetY,boolean wrapping) {
        step(targetX,targetY,wrapping,0);
    }
    public void step(double targetX,double targetY,boolean wrapping,float pain) {
        step(targetX,targetY,wrapping,pain,1,1);
    }
    public void step(double targetX,double targetY,boolean wrapping,float pain,float consciousness,float energy) {
        // CU GetMousePos applies pain-scaled coherent noise. Minecraft has no CU RES/STR stat;
        // bounded deterministic harmonics adapt the existing pain field, not random cursor jumps.
        double conscious=Float.isFinite(consciousness)?Math.clamp(consciousness,0,1):0;
        double reserve=Float.isFinite(energy)?Math.clamp(energy,0,1):0;
        // CU scales both sensitivity and responsiveness by consciousness. Nonzero floors
        // preserve a usable rescue action. Energy slowness is the requested Minecraft adaptation.
        double sensitivity=.35+.65*conscious, responsiveness=.3+.7*conscious;
        double energySpeed=.2+.8*reserve;
        double motionStep=STEP*energySpeed;
        double t=steps++*STEP,a=(Float.isFinite(pain)?Math.clamp(pain,0,1)*20:0)
                *(1+.75*(1-conscious))+(1-conscious)*5;
        double jitterX=a*(Math.sin(t*2*Math.PI*2.9)+.5*Math.sin(t*2*Math.PI*1.37))/1.5;
        double jitterY=a*(Math.sin(t*2*Math.PI*2.2+.7)+.5*Math.sin(t*2*Math.PI*1.61))/1.5;
        double tx=Math.clamp((targetX-128)/UI_SCALE+jitterX,-524,524),ty=Math.clamp((targetY-128)/UI_SCALE+jitterY,-524,524);
        double dx=(tx-x)*.25*sensitivity,dy=(ty-y)*.25*sensitivity,length=Math.hypot(dx,dy);
        if(length>75) {dx*=75/length;dy*=75/length;}
        vx+=(dx-vx)*(4*motionStep*responsiveness);vy+=(dy-vy)*(4*motionStep*responsiveness);
        vx*=1-1.5*motionStep;vy*=1-1.5*motionStep;
        x+=vx*motionStep*120;y+=vy*motionStep*120;
        if(wrapping) {vx*=1-34*motionStep;vy*=1-34*motionStep;}
    }
    public void outsideRadius(double radius) {
        double length=Math.hypot(x,y);
        if(length<radius&&length>0) {x*=radius/length;y*=radius/length;}
    }
    public float rotation(boolean wrapping) { return (float)Math.toRadians(vx*.5+(wrapping?x*.02:0)); }
    public double visualX(float partialTick) { return 128+(previousX+(x-previousX)*Math.clamp(partialTick,0,1))*UI_SCALE; }
    public double visualY(float partialTick) { return 128+(previousY+(y-previousY)*Math.clamp(partialTick,0,1))*UI_SCALE; }
}
