package io.github.safedecimal.decayingaftermath.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class ThoughtTyperTest {
    @BeforeEach void reset() { ThoughtTyper.reset(); }

    @Test void thoughtIsNotPrintedAllAtOnceAndUsesOriginalTalkerRate() {
        ThoughtTyper.enqueue("It's seems didn't help..");
        assertFalse(ThoughtTyper.tick());
        assertEquals("", ThoughtTyper.visible());
        assertTrue(ThoughtTyper.tick());
        assertEquals("I", ThoughtTyper.visible());
        for(int i=2;i<20;i++) ThoughtTyper.tick();
        assertEquals(18,ThoughtTyper.visible().length());
        for(int i=0;i<12;i++) ThoughtTyper.tick();
        assertEquals("It's seems didn't help..",ThoughtTyper.visible());
    }

    @Test void whitespaceDoesNotProduceCharacterSound() {
        ThoughtTyper.enqueue("A B");
        int characterSounds=0;
        for(int i=0;i<12;i++) if(ThoughtTyper.tick()) characterSounds++;
        assertEquals("A B",ThoughtTyper.visible());
        assertEquals(2,characterSounds);
    }

    @Test void unicodeIsRevealedByWholeCodePoint() {
        ThoughtTyper.enqueue("🙂x");
        ThoughtTyper.tick();
        assertTrue(ThoughtTyper.tick());
        assertEquals("🙂",ThoughtTyper.visible());
        assertTrue(ThoughtTyper.tick());
        assertEquals("🙂x",ThoughtTyper.visible());
    }

    @Test void resetClearsActiveMessageAndPendingQueue() {
        ThoughtTyper.enqueue("first");ThoughtTyper.enqueue("second");
        for(int i=0;i<6;i++) ThoughtTyper.tick();
        assertFalse(ThoughtTyper.visible().isEmpty());
        ThoughtTyper.reset();
        for(int i=0;i<200;i++) ThoughtTyper.tick();
        assertFalse(ThoughtTyper.hasMessage());assertEquals("",ThoughtTyper.visible());
    }

    @Test void nextThoughtWaitsUntilPreviousHoldFinishes() {
        ThoughtTyper.enqueue("A");ThoughtTyper.enqueue("B");
        for(int i=0;i<20;i++) ThoughtTyper.tick();
        assertEquals("A",ThoughtTyper.visible());
        for(int i=0;i<66;i++) ThoughtTyper.tick();
        assertEquals("B",ThoughtTyper.visible());
    }
}
