package io.github.safedecimal.decayingaftermath.medical;

/** Tick-clock budget for fractional syringe use; repeated packets cannot create extra fill. */
public final class ProcedureBudget {
    private final long start;
    private float delivered;
    public ProcedureBudget(long start) { this.start=start; }
    public float grant(long now,float requested,float remaining) {
        if (!Float.isFinite(requested)||!Float.isFinite(remaining)||requested<=0||remaining<=0||now<start||now-start>2400) return 0;
        float allowed=Math.max(0,Math.min(1,(now-start)/120f)-delivered);
        float amount=Math.min(requested,Math.min(allowed,remaining));
        delivered+=amount;
        return amount;
    }
    public float delivered() { return delivered; }
}
