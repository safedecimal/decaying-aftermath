package io.github.safedecimal.decayingaftermath.client;

import net.minecraft.client.player.LocalPlayer;

/** Shared snapshot consumed by both the inventory panel and the condition hearts. */
public final class PulseState {
    private static final PulseModel MODEL = new PulseModel();
    private static LocalPlayer trackedPlayer;
    private static double serverBeatPhase;
    private static volatile Snapshot snapshot = new Snapshot(PulseModel.RESTING_BPM, 1.0, 0.0);

    private PulseState() {
    }

    /** Call once per unpaused client tick. Null clears state on disconnect. */
    public static void tick(LocalPlayer player) {
        if (player == null) {
            reset();
            return;
        }
        if (player != trackedPlayer) {
            MODEL.reset();
            trackedPlayer = player;
        }
        double movement = player.getDeltaMovement().horizontalDistance() / 0.10;
        MODEL.tick(movement, player.isSprinting(), player.isInWater(), player.isAlive());
        if (ClientMedicalState.received()) {
            var body = ClientMedicalState.snapshot();
            serverBeatPhase = body.bpm() <= 0 ? 0 : (serverBeatPhase + body.bpm() / 60.0 / 20.0) % 1.0;
            snapshot = new Snapshot(body.bpm(), body.condition(), serverBeatPhase);
        } else {
            snapshot = new Snapshot(MODEL.bpmExact(), MODEL.condition(), MODEL.beatPhase());
        }
    }

    public static void reset() {
        ClientMedicalState.reset();
        serverBeatPhase = 0;
        trackedPlayer = null;
        MODEL.reset();
        snapshot = new Snapshot(PulseModel.RESTING_BPM, 1.0, 0.0);
    }

    public static int bpm() {
        return (int) Math.round(snapshot.bpm);
    }

    public static double bpmExact() {
        return snapshot.bpm;
    }

    public static double condition() {
        return snapshot.condition;
    }

    public static double beatPhase() {
        return snapshot.beatPhase;
    }

    public static String conditionTranslationKey() {
        return PulseModel.conditionTranslationKey(snapshot.condition);
    }

    public static int filledHalfHearts() {
        return (int) Math.round(Math.clamp(snapshot.condition, 0.0, 1.0) * 20.0);
    }

    private record Snapshot(double bpm, double condition, double beatPhase) {
    }
}
