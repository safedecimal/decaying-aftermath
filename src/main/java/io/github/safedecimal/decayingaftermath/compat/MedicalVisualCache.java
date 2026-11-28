package io.github.safedecimal.decayingaftermath.compat;

import java.util.LinkedHashMap;
import java.util.UUID;

/** Client-thread cache. Entry identity is level-reference + UUID + entity id, never id alone. */
public final class MedicalVisualCache<L> {
    public static final long TTL_NANOS = 5_000_000_000L;
    public static final int CAPACITY = 256;
    public record Visual(float bleeding, boolean dead, java.util.List<Float> partBleeding, int ragdollSerial, int posture) {
        public Visual { partBleeding = MedicalBleedOrigins.checked(partBleeding); }
        public Visual(float bleeding, boolean dead, java.util.List<Float> partBleeding, int ragdollSerial) {
            this(bleeding, dead, partBleeding, ragdollSerial, ragdollSerial > 0 ? 2 : 0);
        }
        public Visual(float bleeding, boolean dead) { this(bleeding, dead, MedicalBleedOrigins.uniform(bleeding), 0, 0); }
        public boolean collapsed() { return ragdollSerial > 0 || posture == 2; }
    }
    private record Entry(int entityId, Visual visual, long updated) {}
    private final LinkedHashMap<UUID, Entry> entries = new LinkedHashMap<>();
    private L level;
    public void enterLevel(L current) { if (level != current) { entries.clear(); level = current; } }
    public boolean accept(L observedLevel, L currentLevel, int id, UUID uuid, float bleeding, boolean dead, long now) {
        if (currentLevel == null || observedLevel != currentLevel || id < 0 || uuid == null
                || !Float.isFinite(bleeding) || bleeding < 0 || bleeding > 6) return false;
        return accept(observedLevel, currentLevel, id, uuid, bleeding, dead, MedicalBleedOrigins.uniform(bleeding), 0, 0, now);
    }
    public boolean accept(L observedLevel, L currentLevel, int id, UUID uuid, float bleeding, boolean dead,
            java.util.List<Float> parts, int ragdollSerial, long now) {
        return accept(observedLevel, currentLevel, id, uuid, bleeding, dead, parts, ragdollSerial, ragdollSerial > 0 ? 2 : 0, now);
    }
    public boolean accept(L observedLevel, L currentLevel, int id, UUID uuid, float bleeding, boolean dead,
            java.util.List<Float> parts, int ragdollSerial, int posture, long now) {
        if (currentLevel == null || observedLevel != currentLevel || id < 0 || uuid == null
                || !Float.isFinite(bleeding) || bleeding < 0 || bleeding > 6 || ragdollSerial < 0 || dead && ragdollSerial != 0
                || posture < 0 || posture > 3 || (dead && posture != 0)) return false;
        try { parts = MedicalBleedOrigins.checked(parts); } catch (IllegalArgumentException | NullPointerException error) { return false; }
        if (Math.abs(MedicalBleedOrigins.total(parts) - bleeding) > .0001f) return false;
        enterLevel(currentLevel);
        entries.entrySet().removeIf(e -> now - e.getValue().updated() < 0 || now - e.getValue().updated() > TTL_NANOS);
        entries.remove(uuid); entries.put(uuid, new Entry(id, new Visual(bleeding, dead, parts, ragdollSerial, posture), now));
        while (entries.size() > CAPACITY) entries.remove(entries.keySet().iterator().next());
        return true;
    }
    public Visual live(L current, int id, UUID uuid, boolean playerEntity, boolean actuallyAlive, long now) {
        enterLevel(current);
        if (current == null || uuid == null || !playerEntity || !actuallyAlive) return null;
        Entry entry = entries.get(uuid);
        if (entry == null || entry.entityId() != id) return null;
        long age = now - entry.updated();
        if (age < 0 || age > TTL_NANOS) { entries.remove(uuid); return null; }
        return entry.visual().dead() ? null : entry.visual();
    }
    public void reset() { entries.clear(); level = null; }
    public int size() { return entries.size(); }
}
