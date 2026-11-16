package io.github.safedecimal.decayingaftermath.client;

import java.util.ArrayDeque;

/** Tick-driven thought queue. No wall-clock/FPS dependence and no real medication guidance. */
public final class ThoughtTyper {
    private static final ArrayDeque<String> QUEUE = new ArrayDeque<>();
    private static String active = "";
    private static int shown;
    private static int clock;
    private static int hold;
    private ThoughtTyper() {}

    public static void enqueue(String message) {
        if (message == null || message.isBlank()) return;
        if (QUEUE.size() >= 8) QUEUE.removeFirst();
        QUEUE.addLast(message);
    }

    /** Returns true only when a non-whitespace character was actually revealed. */
    public static boolean tick() {
        if (active.isEmpty()) {
            if (QUEUE.isEmpty()) return false;
            active = QUEUE.removeFirst(); shown = 0; clock = 0; hold = 80;
        }
        if (shown < active.length()) {
            // CU's Talker reveals approximately 18 characters/sec; one game tick is 1/20 s.
            clock += 18;
            if (clock >= 20) {
                clock -= 20;
                int cp = active.codePointAt(shown);
                shown += Character.charCount(cp);
                return !Character.isWhitespace(cp);
            }
        } else if (--hold <= 0) {
            active = ""; shown = 0;
        }
        return false;
    }

    public static String visible() { return active.substring(0, shown); }
    public static boolean hasMessage() { return !active.isEmpty(); }
    public static void reset() { QUEUE.clear(); active = ""; shown = clock = hold = 0; }
}
