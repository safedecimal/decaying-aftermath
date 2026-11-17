package io.github.safedecimal.decayingaftermath.compat;

import java.util.Objects;
import java.util.function.Consumer;

/** One native cosmetic hit emitted only after the corresponding authoritative damage chain. */
public final class DeferredHitContext<P, S> {
    public record Hit<P, S>(P victim, S source, float damage) {}
    private final ThreadLocal<Frame> active = new ThreadLocal<>();
    private final ThreadLocal<Integer> replaying = ThreadLocal.withInitial(() -> 0);
    private final Consumer<Hit<P, S>> emitter;

    public DeferredHitContext(Consumer<Hit<P, S>> emitter) { this.emitter = Objects.requireNonNull(emitter); }
    public Frame begin(P victim, S source) {
        Frame frame = new Frame(Objects.requireNonNull(victim), Objects.requireNonNull(source), active.get());
        active.set(frame); return frame;
    }
    public S source(P victim) { Frame frame = active.get(); return frame != null && frame.victim == victim ? frame.source : null; }
    public boolean capture(P victim, S source, float damage) {
        Frame frame = active.get();
        if (replaying.get() != 0 || frame == null || frame.victim != victim || frame.source != source
                || !Float.isFinite(damage) || damage <= 0) return false;
        // There is one final HP write per actuallyHurt invocation. Duplicate observers are not additive damage.
        frame.damage = Math.max(frame.damage, Math.min(1000, damage)); return true;
    }
    public final class Frame implements AutoCloseable {
        private final P victim; private final S source; private final Frame previous;
        private float damage; private boolean completed, closed;
        private Frame(P victim, S source, Frame previous) { this.victim = victim; this.source = source; this.previous = previous; }
        public void complete() { if (closed || active.get() != this) throw new IllegalStateException("Inactive damage scope"); completed = true; }
        @Override public void close() {
            if (closed) return;
            if (active.get() != this) throw new IllegalStateException("Damage scopes closed out of order");
            closed = true;
            if (previous == null) active.remove(); else active.set(previous);
            if (!completed || damage <= 0) return;
            replaying.set(replaying.get() + 1);
            try { emitter.accept(new Hit<>(victim, source, damage)); }
            finally { int n = replaying.get() - 1; if (n == 0) replaying.remove(); else replaying.set(n); }
        }
    }
}
