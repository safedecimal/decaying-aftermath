package io.github.safedecimal.decayingaftermath.medical;

/** Deterministic, server-timed fictional minigames; no clinical settings. */
public final class DefibGame {
    public static final int CYCLE=32, SCAN=50, CONFIRM=4, CANCEL=5;
    public static final int WINDOW_MIN=8, WINDOW_MAX=27;
    public enum Result { MISS, PROGRESS, SHOCK }
    private final boolean automatic;
    private final int layout;
    private int pads, locks;
    private long readyAt, lastCycle=-1;

    public DefibGame(boolean automatic,int layout,long started) {
        this.automatic=automatic; this.layout=layout & 3; pads=automatic?0:2; readyAt=started+6;
    }
    public static int port(int layout,int pad) { return pad*2 + ((layout >> pad) & 1); }
    public int pads() { return pads; }
    public long readyAt() { return readyAt; }
    public int locks() { return locks; }
    public boolean autoReady(long now) {return automatic && pads==2 && now-readyAt>=SCAN;}

    public Result input(int control,long now) {
        if(pads<2) {
            if(control!=port(layout,pads))return Result.MISS;
            if(++pads==2)readyAt=now;
            return Result.PROGRESS;
        }
        if(automatic || control!=CONFIRM || now<readyAt)return Result.MISS;
        long elapsed=now-readyAt;
        long cycle=elapsed/CYCLE, phase=elapsed%CYCLE;
        if(phase<WINDOW_MIN || phase>WINDOW_MAX || cycle<=lastCycle)return Result.MISS;
        lastCycle=cycle;
        return ++locks==3 ? Result.SHOCK : Result.PROGRESS;
    }
}
