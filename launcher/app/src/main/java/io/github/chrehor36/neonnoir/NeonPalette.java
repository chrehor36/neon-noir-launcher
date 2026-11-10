package io.github.chrehor36.neonnoir;

import android.graphics.Color;

/**
 * Neon Noir colors, dark (the default) and light. The values come from design/tokens.json, which was taken from
 * the mockups on the TV Home Screen Layouts canvas.
 */
final class NeonPalette {
    final boolean light;
    final int background;
    final int text;
    final int textSecondary;
    final int label;
    final int gold;
    final int tile;
    final int tileText;
    final int tileSelected;
    final int tileSelectedText;
    final int focusOutline;
    final int focusBar;
    final int glassFill;
    final int glassEdge;
    final int rain;
    final int scanline;
    final int glowA;
    final int glowB;
    final int clockDot;
    final int edgeRed;
    final int edgeGold;
    /** Dark mode's focused icon button: a faint gold disc (light mode uses the selected red). */
    final int iconFocusFill;
    final int liveDot;
    // Jazz mode: dimmer than Home (antique brass, oxblood, warm gray); light mode has its own.
    final int jazzBackground;
    final int jazzText;
    final int jazzLabel;
    final int jazzAccent;
    final int jazzAccentDeep;
    final int jazzButton;
    final int jazzButtonText;
    final int jazzCard;
    final int jazzCardText;
    final int jazzGlassFill;
    final int jazzGlassEdge;
    final int[] jazzGlows;

    private NeonPalette(boolean light) {
        this.light = light;
        liveDot = Color.rgb(0xE0, 0x53, 0x3B);
        if (!light) {
            jazzBackground = Color.rgb(0x16, 0x05, 0x04);
            jazzText = Color.rgb(0xD8, 0xCC, 0xBF);
            jazzLabel = Color.rgb(0x9A, 0x85, 0x72);
            jazzAccent = Color.rgb(0xB0, 0x8A, 0x45);
            jazzAccentDeep = Color.rgb(0x9C, 0x7A, 0x3E);
            jazzButton = Color.rgb(0x3A, 0x13, 0x10);
            jazzButtonText = Color.rgb(0xD8, 0xCC, 0xBF);
            jazzCard = Color.argb(230, 38, 12, 10);
            jazzCardText = Color.rgb(0xCF, 0xC2, 0xB4);
            jazzGlassFill = Color.argb(128, 16, 6, 6);
            jazzGlassEdge = Color.argb(71, 176, 138, 69);
            jazzGlows = new int[]{Color.argb(107, 255, 140, 60), Color.argb(71, 214, 64, 32)};
        } else {
            jazzBackground = Color.rgb(0xED, 0xE3, 0xD3);
            jazzText = Color.rgb(0x2A, 0x0F, 0x10);
            jazzLabel = Color.rgb(0x7A, 0x5A, 0x50);
            jazzAccent = Color.rgb(0x94, 0x66, 0x1A);
            jazzAccentDeep = Color.rgb(0x94, 0x66, 0x1A);
            jazzButton = Color.rgb(0xA8, 0x13, 0x1D);
            jazzButtonText = Color.rgb(0xFF, 0xF6, 0xEC);
            jazzCard = Color.argb(235, 255, 248, 240);
            jazzCardText = Color.rgb(0x2A, 0x0F, 0x10);
            jazzGlassFill = Color.argb(128, 255, 250, 244);
            jazzGlassEdge = Color.argb(115, 148, 102, 26);
            jazzGlows = new int[]{Color.argb(80, 240, 150, 80), Color.argb(56, 214, 90, 60)};
        }
        if (!light) {
            background = Color.rgb(0x0A, 0x06, 0x06);
            text = Color.rgb(0xF3, 0xEC, 0xE4);
            textSecondary = Color.rgb(0xDC, 0xC9, 0xB8);
            label = Color.rgb(0xBB, 0xA3, 0x8C);
            gold = Color.rgb(0xE2, 0xB0, 0x4A);
            tile = Color.argb(230, 56, 16, 14);
            tileText = text;
            tileSelected = Color.rgb(0x4E, 0x19, 0x13);
            tileSelectedText = text;
            focusOutline = gold;
            focusBar = Color.rgb(0x8F, 0xD8, 0xF0);
            glassFill = Color.argb(128, 22, 7, 7);
            glassEdge = Color.argb(97, 226, 176, 74);
            rain = Color.argb(20, 240, 200, 170);
            scanline = Color.argb(46, 0, 0, 0);
            glowA = Color.argb(140, 168, 19, 29);
            glowB = Color.argb(77, 40, 80, 150);
            clockDot = Color.rgb(0xA8, 0x13, 0x1D);   // the crimson of glowA, the top-right glow (2026-11-09)
            edgeRed = Color.argb(140, 224, 38, 46);
            edgeGold = Color.argb(140, 226, 176, 74);
            iconFocusFill = Color.argb(60, 226, 176, 74);
        } else {
            background = Color.rgb(0xF3, 0xEC, 0xE2);
            text = Color.rgb(0x2A, 0x0F, 0x10);
            textSecondary = Color.rgb(0x5E, 0x3E, 0x36);
            label = Color.rgb(0x7A, 0x5A, 0x50);
            gold = Color.rgb(0xA8, 0x70, 0x1A);
            tile = Color.argb(235, 255, 248, 240);
            tileText = text;
            tileSelected = Color.rgb(0xA8, 0x13, 0x1D);
            tileSelectedText = Color.WHITE;
            focusOutline = Color.rgb(0xB0, 0x7A, 0x1E);
            focusBar = Color.rgb(0xE2, 0xB0, 0x4A);
            glassFill = Color.argb(128, 255, 250, 244);
            glassEdge = Color.argb(115, 168, 112, 26);
            // Fainter than the tokens: dark streaks on cream read as smudges, not rain.
            rain = Color.argb(9, 140, 70, 50);
            scanline = Color.argb(5, 60, 20, 20);
            glowA = Color.argb(66, 214, 60, 60);
            glowB = Color.argb(87, 230, 180, 90);
            clockDot = Color.rgb(0xA8, 0x13, 0x1D);
            edgeRed = Color.argb(90, 200, 30, 40);
            edgeGold = Color.argb(115, 200, 140, 40);
            iconFocusFill = tileSelected;
        }
    }

    private static final NeonPalette DARK = new NeonPalette(false);
    private static final NeonPalette LIGHT = new NeonPalette(true);

    static NeonPalette of(boolean light) {
        return light ? LIGHT : DARK;
    }
}
