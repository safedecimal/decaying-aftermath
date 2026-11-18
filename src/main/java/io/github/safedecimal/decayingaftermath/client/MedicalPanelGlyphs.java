package io.github.safedecimal.decayingaftermath.client;

/** Advances from CU's I-pixel-u TMP glyph table, reduced from authored 3x UI. */
public final class MedicalPanelGlyphs {
    private static final int[] ADVANCE = {
        4,2,4,6,4,6,6,2,4,4,4,4,2,4,2,6,
        6,4,6,6,6,6,6,4,6,6,2,2,4,4,4,6,
        6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,
        6,6,6,6,6,6,6,6,6,6,6,4,6,4,4,6,
        3,5,4,4,4,4,3,4,4,2,2,4,3,6,4,4,
        4,4,3,3,3,4,4,6,4,4,4,4,2,4,5,6
    };

    private MedicalPanelGlyphs() {
    }

    public static int index(char character) {
        return (character >= 32 && character < 127 ? character : '?') - 32;
    }

    public static int advance(char character) {
        return character=='°'?4:ADVANCE[index(character)];
    }

    public static int width(String value) {
        int width = 0;
        for (int index = 0; index < value.length(); index++) {
            width += advance(value.charAt(index));
        }
        return width;
    }
}
