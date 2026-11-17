package io.github.safedecimal.decayingaftermath.client;

import java.util.Arrays;

/** Game-only CU curve/sweep: inventory 120x25, consciousness 139x23, original 28 ms cadence. */
public final class EcgTrace {
    public static final int WIDTH = 120;
    public static final int HEIGHT = 25;
    public static final double SAMPLE_SECONDS = 0.028;
    private final int width, height;
    private final int[] samples;
    private final int[] alpha;
    private int writeIndex;
    private int lastY;
    private double sampleAccumulator;
    private double phase;

    public EcgTrace() {
        this(WIDTH, HEIGHT);
    }

    public EcgTrace(int width, int height) {
        if (width < 2 || height < 2 || width > 4096 || height > 4096)
            throw new IllegalArgumentException("Invalid ECG grid");
        this.width = width; this.height = height;
        samples = new int[width]; alpha = new int[width * height];
        reset();
    }

    public int width() { return width; }
    public int height() { return height; }
    public void reset() {
        Arrays.fill(samples, height / 2); Arrays.fill(alpha, 0);
        writeIndex = 0; lastY = height / 2; sampleAccumulator = phase = 0;
    }

    public void advance(double seconds, double bpm) {
        advance(seconds, bpm, Double.NaN);
    }

    /** Follow the same beat phase as the audible pulse; reopening a screen cannot desync it. */
    public void follow(double seconds, double bpm, double currentPhase) {
        advance(seconds, bpm, currentPhase);
    }

    private void advance(double seconds, double bpm, double currentPhase) {
        if (!Double.isFinite(seconds) || seconds <= 0) {
            return;
        }
        double delta = Math.min(seconds, 0.1);
        sampleAccumulator += delta;
        double rate = Double.isFinite(bpm) ? Math.clamp(bpm, 0.0, 220.0) : 0.0;
        while (sampleAccumulator >= SAMPLE_SECONDS) {
            phase = Double.isFinite(currentPhase)
                    ? ((currentPhase - (sampleAccumulator - SAMPLE_SECONDS) * rate / 60) % 1 + 1) % 1
                    : (phase + rate / 60.0 * SAMPLE_SECONDS) % 1.0;
            double value = rate == 0 ? 0 : waveform(phase);
            int y = Math.clamp((int) Math.round((0.5 - value * .5) * (height - 1)), 0, height - 1);
            samples[writeIndex] = y;
            for (int row = Math.min(lastY, y); row <= Math.max(lastY, y); row++) {
                alpha[row * width + writeIndex] = 255;
                glow(writeIndex, row - 1);
                glow(writeIndex, row + 1);
                glow(writeIndex - 1, row);
                glow(writeIndex + 1, row);
            }
            for (int pixel = 0; pixel < alpha.length; pixel++) {
                alpha[pixel] = (int) (alpha[pixel] * 0.985);
            }
            lastY = y;
            writeIndex = (writeIndex + 1) % width;
            sampleAccumulator -= SAMPLE_SECONDS;
        }
    }

    public int sample(int age) {
        return samples[Math.floorMod(writeIndex - 1 - Math.clamp(age, 0, width - 1), width)];
    }

    public int alpha(int x, int y) {
        return alpha[Math.clamp(y, 0, height - 1) * width + Math.clamp(x, 0, width - 1)];
    }

    private void glow(int x, int y) {
        if (x >= 0 && x < width && y >= 0 && y < height) {
            alpha[y * width + x] = Math.max(50, alpha[y * width + x]);
        }
    }

    public static double waveform(double phase) {
        return CuPulseCurve.normal(phase);
    }
}
