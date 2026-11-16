package io.github.safedecimal.decayingaftermath.client;

import java.util.Arrays;

/** Decorative, game-only trace: the source panel's native 120x25 pixels and 28 ms cadence. */
public final class EcgTrace {
    public static final int WIDTH = 120;
    public static final int HEIGHT = 25;
    public static final double SAMPLE_SECONDS = 0.028;
    private final int[] samples = new int[WIDTH];
    private final int[] alpha = new int[WIDTH * HEIGHT];
    private int writeIndex;
    private int lastY = HEIGHT / 2;
    private double sampleAccumulator;
    private double phase;

    public EcgTrace() {
        Arrays.fill(samples, HEIGHT / 2);
    }

    public void advance(double seconds, double bpm) {
        if (!Double.isFinite(seconds) || seconds <= 0) {
            return;
        }
        double delta = Math.min(seconds, 0.1);
        sampleAccumulator += delta;
        double rate = Double.isFinite(bpm) ? Math.clamp(bpm, 0.0, 190.0) : 0.0;
        while (sampleAccumulator >= SAMPLE_SECONDS) {
            phase = (phase + rate / 60.0 * SAMPLE_SECONDS) % 1.0;
            double value = rate == 0 ? 0 : waveform(phase);
            int y = (int) Math.round((0.5 - value * 0.44) * (HEIGHT - 1));
            samples[writeIndex] = y;
            for (int row = Math.min(lastY, y); row <= Math.max(lastY, y); row++) {
                alpha[row * WIDTH + writeIndex] = 255;
                glow(writeIndex, row - 1);
                glow(writeIndex, row + 1);
                glow(writeIndex - 1, row);
                glow(writeIndex + 1, row);
            }
            for (int pixel = 0; pixel < alpha.length; pixel++) {
                alpha[pixel] = (int) (alpha[pixel] * 0.985);
            }
            lastY = y;
            writeIndex = (writeIndex + 1) % WIDTH;
            sampleAccumulator -= SAMPLE_SECONDS;
        }
    }

    public int sample(int age) {
        return samples[Math.floorMod(writeIndex - 1 - Math.clamp(age, 0, WIDTH - 1), WIDTH)];
    }

    public int alpha(int x, int y) {
        return alpha[Math.clamp(y, 0, HEIGHT - 1) * WIDTH + Math.clamp(x, 0, WIDTH - 1)];
    }

    private void glow(int x, int y) {
        if (x >= 0 && x < WIDTH && y >= 0 && y < HEIGHT) {
            alpha[y * WIDTH + x] = Math.max(50, alpha[y * WIDTH + x]);
        }
    }

    public static double waveform(double phase) {
        return 0.12 * bump(phase, 0.12, 0.027) - 0.19 * bump(phase, 0.265, 0.013)
                + 0.96 * bump(phase, 0.30, 0.013) - 0.28 * bump(phase, 0.338, 0.018)
                + 0.22 * bump(phase, 0.53, 0.054);
    }

    private static double bump(double phase, double center, double width) {
        double distance = (phase - center) / width;
        return Math.exp(-0.5 * distance * distance);
    }
}
