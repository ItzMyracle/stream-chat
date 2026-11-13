package com.streampop;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

/** Colours and small drawing helpers (black + light blue theme). */
public final class Draw {
    public static final int SKY = 0xFF67C8FF;
    public static final int WHITE = 0xFFEAF6FF;
    public static final int MUTED = 0xFF8FB4C9;
    public static final int GREEN = 0xFF4ADE80;
    public static final int RED = 0xFFFF6B6B;
    public static final int PINK = 0xFFF472B6;
    public static final int AMBER = 0xFFFFD166;

    private Draw() {}

    public static int a(int alpha, int rgb) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0xFFFFFF);
    }

    /** Rounded (1px) rectangle that is safe for see-through colours (no overlapping fills). */
    public static void rrect(DrawContext c, int x, int y, int w, int h, int col) {
        c.fill(x + 1, y, x + w - 1, y + 1, col);
        c.fill(x, y + 1, x + w, y + h - 1, col);
        c.fill(x + 1, y + h - 1, x + w - 1, y + h, col);
    }

    public static void outline(DrawContext c, int x1, int y1, int x2, int y2, int col) {
        c.fill(x1 + 1, y1, x2 - 1, y1 + 1, col);
        c.fill(x1 + 1, y2 - 1, x2 - 1, y2, col);
        c.fill(x1, y1 + 1, x1 + 1, y2 - 1, col);
        c.fill(x2 - 1, y1 + 1, x2, y2 - 1, col);
    }

    /** Lightens dark Twitch name colours so they stay readable on black. */
    public static int readable(int rgb) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        double lum = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0;
        if (lum < 0.55) {
            double t = (0.55 - lum) / 0.55 * 0.65;
            r += (int) ((255 - r) * t);
            g += (int) ((255 - g) * t);
            b += (int) ((255 - b) * t);
        }
        return (r << 16) | (g << 8) | b;
    }

    public static String trim(TextRenderer tr, String s, int maxW) {
        if (s == null) return "";
        if (tr.getWidth(s) <= maxW) return s;
        String t = s;
        while (t.length() > 1 && tr.getWidth(t + "..") > maxW) t = t.substring(0, t.length() - 1);
        return t + "..";
    }

    public static List<String> wrap(TextRenderer tr, String text, int maxW, int maxLines) {
        List<String> all = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : text.trim().split("\\s+")) {
            String w = word;
            if (w.isEmpty()) continue;
            while (tr.getWidth(w) > maxW) { // very long word: cut it
                int cut = w.length();
                while (cut > 1 && tr.getWidth(w.substring(0, cut)) > maxW) cut--;
                if (cur.length() > 0) { all.add(cur.toString()); cur.setLength(0); }
                all.add(w.substring(0, cut));
                w = w.substring(cut);
            }
            if (w.isEmpty()) continue;
            String test = cur.length() == 0 ? w : cur + " " + w;
            if (tr.getWidth(test) <= maxW) {
                cur.setLength(0);
                cur.append(test);
            } else {
                all.add(cur.toString());
                cur.setLength(0);
                cur.append(w);
            }
        }
        if (cur.length() > 0) all.add(cur.toString());
        if (all.isEmpty()) all.add("");
        if (all.size() > maxLines) {
            List<String> cut = new ArrayList<>(all.subList(0, maxLines));
            String last = cut.get(maxLines - 1);
            while (last.length() > 1 && tr.getWidth(last + "...") > maxW) last = last.substring(0, last.length() - 1);
            cut.set(maxLines - 1, last + "...");
            return cut;
        }
        return all;
    }

    public static void scaled(DrawContext c, TextRenderer tr, String s, float x, float y, int col, float scale, boolean shadow) {
        var m = c.getMatrices();
        m.pushMatrix();
        m.translate(x, y);
        m.scale(scale, scale);
        c.drawText(tr, s, 0, 0, col, shadow);
        m.popMatrix();
    }
}
