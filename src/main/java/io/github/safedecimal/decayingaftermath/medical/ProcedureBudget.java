package io.github.safedecimal.decayingaftermath.medical;

/** Server tick-clock budget for fractional supplies; packet floods cannot create extra material. */
public final class ProcedureBudget {
    private final long start;
    private final int ticksPerFill;
    private float delivered;
    public ProcedureBudget(long start) { this(start,120); }
    public ProcedureBudget(long start,int ticksPerFill) {
        if(ticksPerFill<=0) throw new IllegalArgumentException("Positive fill duration required");
        this.start=start;this.ticksPerFill=ticksPerFill;
    }
    public float grant(long now,float requested,float remaining) {
        if (!Float.isFinite(requested)||!Float.isFinite(remaining)||requested<=0||remaining<=0||now<start||now-start>2400) return 0;
        float allowed=Math.max(0,Math.min(1,(now-start)/(float)ticksPerFill)-delivered);
        float amount=Math.min(requested,Math.min(allowed,remaining));
        delivered+=amount;
        return amount;
    }
    /** A spray uses a whole shot (or the last residue), never a trickle. */
    public float grantSpray(long now, float requested, float available) {
        float cost = MedicalSupplyPolicy.sprayCost(available);
        if (!Float.isFinite(requested) || cost <= MedicalSupplyPolicy.EMPTY || requested + .000001f < cost
                || now < start || now-start > 2400) return 0;
        if (delivered + cost > Math.min(1, (now-start)/(float)ticksPerFill) + .000001f) return 0;
        delivered += cost;
        return cost;
    }
    public float delivered() { return delivered; }
}
