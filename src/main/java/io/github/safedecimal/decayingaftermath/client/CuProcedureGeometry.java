package io.github.safedecimal.decayingaftermath.client;

import io.github.safedecimal.decayingaftermath.medical.BodyPart;

/** One geometry for visible tools, hit tests and pouring; no invisible completion targets. */
public final class CuProcedureGeometry {
    public record Point(double x,double y) {}
    public record Bounds(int x,int y,int width,int height) {}
    private CuProcedureGeometry() {}
    public static Bounds part(BodyPart part,int x,int y,int width,int height) {
        int pw=part==BodyPart.HEAD||part==BodyPart.TORSO?8:4,ph=part==BodyPart.HEAD?8:12;
        float ratio=Math.min(width/(float)pw,height/(float)ph);
        int w=Math.round(pw*ratio),h=Math.round(ph*ratio);
        return new Bounds(x+(width-w)/2,y+(height-h)/2,w,h);
    }
    public static boolean windlassGrip(double x,double y,double angle) {
        double r=Math.toRadians(angle),dx=x-128,dy=y-128;
        double localX=dx*Math.cos(r)+dy*Math.sin(r),localY=-dx*Math.sin(r)+dy*Math.cos(r);
        return Math.abs(localX)>=12&&Math.abs(localX)<=31&&Math.abs(localY)<=11;
    }
    public static boolean bottleGrip(double x,double y,double left,double top) {
        return toolGrip(x,y,left,top,32,32,0,4);
    }
    public static boolean toolGrip(double x,double y,double left,double top,double width,double height,double angle,double margin) {
        double r=Math.toRadians(angle),dx=x-left-width/2,dy=y-top-height/2;
        double lx=dx*Math.cos(r)+dy*Math.sin(r),ly=-dx*Math.sin(r)+dy*Math.cos(r);
        return Math.abs(lx)<=width/2+margin&&Math.abs(ly)<=height/2+margin;
    }
    public static boolean deviceAligned(double x,double y,double angle,boolean splint) {
        return Math.abs(x-116)<13&&Math.abs(y-105)<18&&(!splint||Math.abs(angle)<=7);
    }
    public static Point bottleMouth(double left,double top,double tilt) {
        double r=Math.toRadians(tilt);
        return new Point(left+16+11*Math.sin(r),top+16-11*Math.cos(r));
    }
    public static boolean overWound(BodyPart part,Point mouth) {
        var b=part(part,111,80,34,116);
        return mouth.x()>=b.x()+2&&mouth.x()<=b.x()+b.width()-2
                &&mouth.y()>=b.y()-42&&mouth.y()<b.y()+b.height()-8;
    }
    public static float pourStrength(double tilt) {
        if(!Double.isFinite(tilt))return 0;
        return (float)Math.clamp(Math.min((tilt-35)/20,(85-tilt)/15),0,1);
    }
}
