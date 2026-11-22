package io.github.safedecimal.decayingaftermath.medical;

import java.util.List;
import java.util.Locale;

/** Presentation of fictional deaths, independent of respiratory drug depression. */
public final class AftermathPolicy {
    public enum Cause { ORDINARY, SEVERE_DEPRESSION }
    public static final int DEFAULT_SECONDS = 360;
    public static final double EMERGE_SECONDS = 3.4;
    public static final List<String> PLAYLIST = List.of("afterlife_lobby_time", "afterlife_airport_lounge",
            "afterlife_bossa_antigua", "afterlife_leopard_print_elevator", "afterlife_george_street_shuffle");
    public static final int THOUGHT_COUNT = 8;
    public record Point(double x, double y, double z) { }
    public record Shot(Point position, Point target, float yaw, float pitch) { }
    public record Thought(int index, double x, double y, double opacity, double scale) { }

    private AftermathPolicy() { }
    public static Cause fromMood(float moodPoints) {
        return Float.isFinite(moodPoints) && moodPoints < -75f ? Cause.SEVERE_DEPRESSION : Cause.ORDINARY;
    }
    public static boolean hasTimer(Cause cause) { return cause == Cause.ORDINARY; }
    public static boolean monochrome(Cause cause) { return cause == Cause.SEVERE_DEPRESSION; }
    public static boolean redactFigures(Cause cause) { return monochrome(cause); }
    public static boolean lockMovement() { return true; }
    public static boolean showVanillaDeathScreen() { return false; }
    public static int nextTrack(int current) { return Math.floorMod(current + 1, PLAYLIST.size()); }
    /** A temporary or stale saved spectator mode is never a normal respawn result. */
    public static int returnGameModeId(int originalMode,boolean hardcore) {
        if(hardcore)return 3;
        return originalMode==3 ? 0 : Math.clamp(originalMode,0,2);
    }
    public static int checkedSeconds(int seconds) {
        if (seconds < 1 || seconds > 3600) throw new IllegalArgumentException("deathAftermathSeconds must be 1..3600");
        return seconds;
    }
    public static String timerText(double seconds) {
        int remaining = (int) Math.ceil(finiteNonnegative(seconds));
        return String.format(Locale.ROOT, "%02d:%02d", remaining / 60, remaining % 60);
    }
    public static double timerOpacity(double seconds) { return .62 * smooth((seconds - 1.2) / 1.8); }
    private static double finiteNonnegative(double value) {
        return Double.isFinite(value) ? Math.clamp(value, 0, 3600 * 20) : 0;
    }
    private static double smooth(double t) { t = Math.clamp(t, 0, 1); return t * t * (3 - 2 * t); }

    /** Camera rises out of the body, then slowly orbits it. Input is never part of the shot. */
    public static Shot camera(Point anchor, float deathYaw, double elapsed) {
        double seconds = finiteNonnegative(elapsed), opening = smooth(seconds / EMERGE_SECONDS);
        double angle = Math.toRadians(deathYaw) + Math.max(0, seconds - EMERGE_SECONDS) * (Math.PI * 2 / 46);
        double radius = .06 + 4.8 * opening;
        Point position = new Point(anchor.x + Math.sin(angle) * radius,
                anchor.y + .24 + 2.45 * opening + Math.sin(seconds * .19) * .15 * opening,
                anchor.z + Math.cos(angle) * radius);
        Point target = new Point(anchor.x, anchor.y + .20, anchor.z);
        double dx = target.x - position.x, dy = target.y - position.y, dz = target.z - position.z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
        return new Shot(position, target, yaw, pitch);
    }

    /** Five staggered, slowly drifting lanes; never an action-bar hint or a timer. */
    public static Thought thought(int lane, double elapsed) {
        if (lane < 0 || lane >= 5) throw new IllegalArgumentException("thought lane must be 0..4");
        double time = finiteNonnegative(elapsed) + lane * 7.7;
        double progress = (time % 38) / 38;
        int cycle = (int) (time / 38);
        int index = Math.floorMod(cycle * 3 + lane, THOUGHT_COUNT);
        double opacity = Math.pow(Math.sin(Math.PI * progress), 2) * .43;
        return new Thought(index, 1.08 - progress * 1.55,
                .16 + lane * .145 + Math.sin(time * .08 + lane) * .018, opacity, .85 + lane * .065);
    }
}
