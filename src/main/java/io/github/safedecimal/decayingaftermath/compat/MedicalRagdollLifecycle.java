package io.github.safedecimal.decayingaftermath.compat;

import java.util.Map;
import java.util.WeakHashMap;

/** The serial is the ownership boundary: recovery cannot remove a replacement death body. */
public final class MedicalRagdollLifecycle<P> {
    public interface Port<P> {
        int liveSerial(int id) throws ReflectiveOperationException;
        int start(P player) throws ReflectiveOperationException;
        void stop(int id) throws ReflectiveOperationException;
    }
    private record Owned(int id, int serial, Object level) {}
    private final Map<P, Owned> owned = new WeakHashMap<>();
    private final Port<P> port;
    public MedicalRagdollLifecycle(Port<P> port) { this.port = java.util.Objects.requireNonNull(port); }
    public void update(P player, int id, Object level, boolean collapsed) throws ReflectiveOperationException {
        Owned state = owned.get(player);
        if (state != null && (state.id() != id || state.level() != level || !collapsed)) {
            release(player); state = null;
        }
        if (collapsed && state == null && port.liveSerial(id) == 0) {
            int serial = port.start(player);
            if (serial != 0) owned.put(player, new Owned(id, serial, level));
        }
    }
    public void release(P player) throws ReflectiveOperationException {
        Owned state = owned.remove(player);
        if (state != null && port.liveSerial(state.id()) == state.serial()) port.stop(state.id());
    }
    public boolean owns(P player) throws ReflectiveOperationException {
        Owned state = owned.get(player);
        return state != null && port.liveSerial(state.id()) == state.serial();
    }
}
