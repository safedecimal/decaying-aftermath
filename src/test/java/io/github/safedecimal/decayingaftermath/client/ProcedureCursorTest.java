package io.github.safedecimal.decayingaftermath.client;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.HashMap;
import static org.junit.jupiter.api.Assertions.*;

class ProcedureCursorTest {
    private static class Modes implements ProcedureCursor.ModeDriver {
        final HashMap<Long,Integer> modes = new HashMap<>();
        final ArrayList<Integer> writes = new ArrayList<>();
        public int get(long window) {return modes.getOrDefault(window, ProcedureCursor.VISIBLE);}
        public void set(long window,int mode) {modes.put(window,mode);writes.add(mode);}
    }
    @Test void hidesCursorWithoutDisablingAbsoluteInput() {
        var modes = new Modes(); var cursor = new ProcedureCursor(modes); cursor.hide(1);
        assertEquals(ProcedureCursor.HIDDEN,modes.get(1));
        assertTrue(modes.writes.stream().allMatch(mode->mode==ProcedureCursor.VISIBLE||mode==ProcedureCursor.HIDDEN));
    }
    @Test void restoreIsIdempotentAndReturnsOriginalMode() {
        var modes = new Modes(); var cursor = new ProcedureCursor(modes);
        cursor.hide(1);cursor.restore();cursor.restore();
        assertEquals(ProcedureCursor.VISIBLE,modes.get(1));assertEquals(2,modes.writes.size());
    }
    @Test void repeatedFramesDoNotResetOwnershipOrWriteSameMode() {
        var modes = new Modes(); var cursor = new ProcedureCursor(modes);
        cursor.hide(1);cursor.hide(1);cursor.hide(1);assertEquals(1,modes.writes.size());
        cursor.restore();assertEquals(ProcedureCursor.VISIBLE,modes.get(1));
    }
    @Test void focusResetIsHiddenAgainWithoutLosingOriginalMode() {
        var modes = new Modes(); var cursor = new ProcedureCursor(modes);
        cursor.hide(1);modes.modes.put(1L,ProcedureCursor.VISIBLE);cursor.hide(1);cursor.restore();
        assertEquals(ProcedureCursor.VISIBLE,modes.get(1));assertEquals(3,modes.writes.size());
    }
    @Test void switchingWindowsRestoresOldWindowFirst() {
        var modes = new Modes(); var cursor = new ProcedureCursor(modes);
        cursor.hide(1);cursor.hide(2);assertEquals(ProcedureCursor.VISIBLE,modes.get(1));
        assertEquals(ProcedureCursor.HIDDEN,modes.get(2));cursor.restore();assertEquals(ProcedureCursor.VISIBLE,modes.get(2));
    }
    @Test void noWindowDoesNotTouchNativeInputState() {
        var modes = new Modes(); var cursor = new ProcedureCursor(modes);
        cursor.hide(0);cursor.restore();assertTrue(modes.writes.isEmpty());
    }
}
