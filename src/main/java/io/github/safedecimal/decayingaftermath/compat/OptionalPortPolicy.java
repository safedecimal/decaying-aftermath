package io.github.safedecimal.decayingaftermath.compat;

/** Only the supplied, byte-inspected native API versions are claimed supported. */
public final class OptionalPortPolicy {
    private OptionalPortPolicy() {}
    public static boolean cruor(String version, String minecraft) {
        return "0.1.0-port.4".equals(version) && "26.3".equals(minecraft);
    }
    public static boolean ragdollified(String version, String minecraft) {
        return "1.2.2".equals(version) && "26.3".equals(minecraft);
    }
}
