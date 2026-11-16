package io.github.safedecimal.decayingaftermath.client;

/** Standalone smoke checks; run with javac/java, no JUnit or game launch required. */
public final class PulseModelCheck {
    @org.junit.jupiter.api.Test
    void modelBehavior() {
        main(new String[0]);
    }

    public static void main(String[] args) {
        PulseModel model = new PulseModel();
        for (int tick = 0; tick < 200; tick++) {
            model.tick(0.0, false, false, true);
        }
        check(model.bpm() == 72, "resting pulse stays 72 BPM");
        check(model.condition() == 1.0, "resting condition is full");
        double resting = model.bpmExact();
        for (int tick = 0; tick < 200; tick++) {
            model.tick(1.0, true, false, true);
        }
        double sprinting = model.bpmExact();
        check(sprinting > resting + 60.0 && sprinting < 145.0, "sprinting raises pulse smoothly");
        check(model.condition() < 0.8, "condition shows pulse strain, not HP");
        for (int tick = 0; tick < 200; tick++) {
            model.tick(0.0, false, false, true);
        }
        check(model.bpmExact() < sprinting && model.bpmExact() > resting, "pulse gradually recovers");
        check(model.beatPhase() >= 0.0 && model.beatPhase() < 1.0, "beat phase remains normalized");
        model.tick(Double.NaN, false, false, true);
        check(Double.isFinite(model.bpmExact()), "invalid movement input cannot poison state");
        model.tick(0.0, false, false, false);
        check(model.bpm() == 0 && model.condition() == 0.0, "no pulse while dead");
        model.reset();
        check(model.bpm() == 72 && model.condition() == 1.0 && model.beatPhase() == 0.0, "respawn/disconnect reset");
        for (double bpm : new double[] {-1.0, 0.0, 40.0, 72.0, 100.0, 150.0, 300.0, Double.NaN}) {
            double condition = PulseModel.conditionForBpm(bpm);
            check(condition >= 0.0 && condition <= 1.0, "condition bounded for " + bpm);
        }
        System.out.printf("PulseModel checks passed: resting=%d, sprinting=%.2f, recovered/reset=%d%n",
                72, sprinting, model.bpm());
    }

    private static void check(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError(label);
        }
    }
}
