package io.github.safedecimal.decayingaftermath.compat;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Predicate;

/** Tracking audience + self, exactly once and only with an advertised compatible S2C codec. */
public final class MedicalVisualAudience {
    private MedicalVisualAudience() {}
    public static <P> List<P> select(Collection<P> tracking, P self, Predicate<P> canSend) {
        var watchers = new LinkedHashSet<>(tracking); watchers.add(self);
        return watchers.stream().filter(canSend).toList();
    }
}
