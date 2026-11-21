package io.github.safedecimal.decayingaftermath.medical;

import org.junit.jupiter.api.Test;
import static io.github.safedecimal.decayingaftermath.medical.DefibGame.Result.*;
import static org.junit.jupiter.api.Assertions.*;

final class DefibGameTest {
    @Test void manualRequiresThreeSeparateOnTimeInputs() {
        var game=new DefibGame(false,0,100);
        assertFalse(game.autoReady(1000));
        assertEquals(MISS,game.input(DefibGame.CONFIRM,100));
        assertEquals(MISS,game.input(0,121));
        assertEquals(PROGRESS,game.input(DefibGame.CONFIRM,121));
        assertEquals(MISS,game.input(DefibGame.CONFIRM,121));
        assertEquals(MISS,game.input(DefibGame.CONFIRM,123));
        assertEquals(MISS,game.input(DefibGame.CONFIRM,137));
        assertEquals(PROGRESS,game.input(DefibGame.CONFIRM,153));
        assertEquals(2,game.locks());
        assertEquals(SHOCK,game.input(DefibGame.CONFIRM,185));
    }
    @Test void readyAtStartsSixTicksAfterStart() {
        assertEquals(106,new DefibGame(false,0,100).readyAt());
        assertEquals(106,new DefibGame(true,0,100).readyAt());
    }
    @Test void automaticOnlyCompletesAfterBothMatchingPadsAndFullAnalysis() {
        for(int layout=0;layout<4;layout++) {
            var game=new DefibGame(true,layout,100);
            int first=DefibGame.port(layout,0), second=DefibGame.port(layout,1);
            assertEquals(MISS,game.input(second,108));
            assertEquals(MISS,game.input(first^1,109));
            assertEquals(PROGRESS,game.input(first,110));
            assertEquals(MISS,game.input(first,111));
            assertFalse(game.autoReady(112));
            assertEquals(PROGRESS,game.input(second,113));
            assertEquals(MISS,game.input(DefibGame.CONFIRM,162));
            assertFalse(game.autoReady(162));
            assertTrue(game.autoReady(163));
            assertEquals(MISS,game.input(DefibGame.CONFIRM,163));
        }
    }
}
