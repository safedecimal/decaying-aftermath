package io.github.safedecimal.decayingaftermath.client;

/** One frame-time pose for both the held tool and its hand grip; never reads a cursor. */
public final class ProcedureToolPose {
    public record Point(double x,double y) {}
    private ProcedureToolPose() {}
    public static double interpolate(double previous,double current,float partial) {
        return previous+(current-previous)*Math.clamp(partial,0,1);
    }
    public static Point localGrip(double handX,double handY,double x,double y,
                                  double pivotX,double pivotY,double degrees) {
        double a=Math.toRadians(-degrees),dx=handX-x-pivotX,dy=handY-y-pivotY;
        return new Point(dx*Math.cos(a)-dy*Math.sin(a),dx*Math.sin(a)+dy*Math.cos(a));
    }
    public static Point grip(double x,double y,double pivotX,double pivotY,
                             double localX,double localY,double degrees) {
        double a=Math.toRadians(degrees);
        return new Point(x+pivotX+localX*Math.cos(a)-localY*Math.sin(a),
                y+pivotY+localX*Math.sin(a)+localY*Math.cos(a));
    }
}
