package io.github.safedecimal.decayingaftermath.client;

/** MinigameBase.UpdateHandPhysics: 60 Hz, velocity lerp, drag and authored 1/4 UI units. */
public final class CuHandMotion {
    public static final double STEP=1.0/60, UI_SCALE=.25;
    private double x,y,vx,vy,previousX,previousY,stepX,stepY,obstacleRadius;
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
        // CU averagePain is 0..100; the old 20 multiplier reduced the authored tremor fivefold.
        // Deterministic harmonics stand in for Unity PerlinNoise and stay in its -0.5..0.5 range.
        double t=steps++*STEP,a=(Float.isFinite(pain)?Math.clamp(pain,0,1)*100:0);
        double jitterX=a*(Math.sin(t*2*Math.PI*2.9)+.5*Math.sin(t*2*Math.PI*1.37))/3;
        double jitterY=a*(Math.sin(t*2*Math.PI*2.2+.7)+.5*Math.sin(t*2*Math.PI*1.61))/3;
        double tx=Math.clamp((targetX-128)/UI_SCALE+jitterX,-524,524),ty=Math.clamp((targetY-128)/UI_SCALE+jitterY,-524,524);
        double dx=(tx-x)*.25*sensitivity,dy=(ty-y)*.25*sensitivity,length=Math.hypot(dx,dy);
        if(length>75) {dx*=75/length;dy*=75/length;}
        vx+=(dx-vx)*(4*motionStep*responsiveness);vy+=(dy-vy)*(4*motionStep*responsiveness);
        vx*=1-1.5*motionStep;vy*=1-1.5*motionStep;
        stepX=x;stepY=y;x+=vx*motionStep*120;y+=vy*motionStep*120;
        if(wrapping) {vx*=1-34*motionStep;vy*=1-34*motionStep;}
    }
    /** CU BandageMinigame.PhysicsUpdate's solid inner disk, including zero and frame-time positions. */
    public void outsideRadius(double radius) {
        if(!Double.isFinite(radius)||radius<=0)return;
        obstacleRadius=radius;
        double length=Math.hypot(x,y),startLength=Math.hypot(stepX,stepY);
        // A point can end outside after sweeping through the disk: stop at its first contact.
        double dx=x-stepX,dy=y-stepY,aa=dx*dx+dy*dy;
        if(startLength>=radius&&length>=radius&&aa>0&&stepX*x+stepY*y<0) {
            double bb=2*(stepX*dx+stepY*dy),cc=startLength*startLength-radius*radius;
            double discriminant=bb*bb-4*aa*cc;
            if(discriminant>0) {
                double contact=(-bb-Math.sqrt(discriminant))/(2*aa);
                if(contact>=0&&contact<1) {x=stepX+dx*contact;y=stepY+dy*contact;length=Math.hypot(x,y);}
            }
        }
        if(length<=radius+1e-8) {
            double nx,ny;
            if(length>1e-8) {nx=x/length;ny=y/length;}
            else if(startLength>1e-8) {nx=stepX/startLength;ny=stepY/startLength;}
            else {nx=0;ny=-1;} // Start at the reachable roll side instead of disabling collision at zero.
            x=nx*radius;y=ny*radius;
            double inward=vx*nx+vy*ny;
            if(inward<0) {vx-=inward*nx;vy-=inward*ny;}
            // Source applies a small outward impulse; preserve tangential motion for wrapping.
            vx+=nx*STEP*25;vy+=ny*STEP*25;
        }
    }
    private double[] visual(float partialTick) {
        double partial=Float.isFinite(partialTick)?Math.clamp(partialTick,0,1):0;
        double xx=previousX+(x-previousX)*partial,yy=previousY+(y-previousY)*partial;
        double length=Math.hypot(xx,yy);
        if(obstacleRadius>0&&length<obstacleRadius) {
            if(length>1e-8) {xx*=obstacleRadius/length;yy*=obstacleRadius/length;}
            else {double currentLength=Math.hypot(x,y);xx=currentLength>0?x*obstacleRadius/currentLength:0;yy=currentLength>0?y*obstacleRadius/currentLength:obstacleRadius;}
        }
        return new double[]{128+xx*UI_SCALE,128+yy*UI_SCALE};
    }
    public float rotation(boolean wrapping) { return (float)Math.toRadians(vx*.5+(wrapping?x*.02:0)); }
    public double visualX(float partialTick) { return visual(partialTick)[0]; }
    public double visualY(float partialTick) { return visual(partialTick)[1]; }
}
