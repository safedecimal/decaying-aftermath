package io.github.safedecimal.decayingaftermath.client;

/** PlayerCamera.HandleScreenShaders _Pain: normalized averagePain, smoothing6 and 15% pulse. */
public final class MedicalPainState {
    public static final double MAX_INTENSITY=1.15;
    public static final int MASK_STEPS=96;
    private double pain,phase;
    public void advance(float normalizedPain,double seconds) {
        if(!Double.isFinite(seconds)||seconds<=0)return;
        double target=Float.isFinite(normalizedPain)?Math.clamp(normalizedPain,0,1):0;
        // Integrate in CU-sized frames so 20Hz body updates do not turn smoothing into snapping.
        double left=Math.min(seconds,1);
        while(left>1e-9) {
            double dt=Math.min(left,1.0/60);
            pain+=(target-pain)*Math.min(1,dt*6);
            phase=(phase+dt*7*pain)%(Math.PI*2);
            left-=dt;
        }
        if(target==0&&pain<.00001)pain=0;
    }
    public double intensity() {return Math.max(0,pain*(1+.15*Math.sin(phase)));}
    public double smoothedPain() {return pain;}
    public int maskIndex() {return (int)Math.floor(Math.clamp(intensity()/MAX_INTENSITY,0,1)*MASK_STEPS);}
    public double maskBlend() {return Math.clamp(intensity()/MAX_INTENSITY,0,1)*MASK_STEPS-maskIndex();}
    public void reset() {pain=phase=0;}
}
