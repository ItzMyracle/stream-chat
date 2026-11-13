package com.streampop;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** The pop-up cards in the screen corner. Newest card sits closest to the corner. */
public class MessageHud implements HudElement {
    private static final int ENTER_MS = 420, EXIT_MS = 420, GAP = 4;

    public static final ArrayDeque<ChatMsg> HISTORY = new ArrayDeque<>();
    private static final List<Card> CARDS = new ArrayList<>();

    private record Tag(String label, int color) {}

    private static final class Card {
        final ChatMsg msg;
        final long born;
        long expireAt;
        List<String> lines;
        List<Tag> tags = new ArrayList<>();
        int layoutW = -1, h = 20;
        float off;
        boolean placed;

        Card(ChatMsg msg, long born, long expireAt) {
            this.msg = msg;
            this.born = born;
            this.expireAt = expireAt;
        }
    }

    private long lastFrame;

    public static void add(ChatMsg m, PopConfig cfg) {
        long now = System.currentTimeMillis();
        HISTORY.addLast(m);
        while (HISTORY.size() > 200) HISTORY.removeFirst();
        if (!cfg.hud) return;
        CARDS.add(0, new Card(m, now, now + Math.max(2000, cfg.durationSeconds * 1000L)));
        for (int i = Math.max(1, cfg.maxCards); i < CARDS.size(); i++) {
            CARDS.get(i).expireAt = Math.min(CARDS.get(i).expireAt, now);
        }
    }

    public static void clearCards() {
        CARDS.clear();
    }

    @Override
    public void render(DrawContext c, RenderTickCounter tick) {
        PopConfig cfg = StreamPopMod.config;
        if (cfg == null || !cfg.hud || CARDS.isEmpty()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer tr = mc.textRenderer;
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        long now = System.currentTimeMillis();
        float dt = lastFrame == 0 ? 0.016f : Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;

        CARDS.removeIf(k -> now > k.expireAt + EXIT_MS);

        int w = Math.max(110, Math.min(300, cfg.cardWidth));
        boolean right = cfg.corner == 1 || cfg.corner == 3;
        boolean bottom = cfg.corner >= 2;
        float acc = 0;
        for (Card k : CARDS) {
            layout(k, tr, cfg, w);
            float target = acc;
            acc += k.h + GAP;
            if (!k.placed) {
                k.off = target;
                k.placed = true;
            } else {
                k.off += (target - k.off) * (1f - (float) Math.exp(-dt * 14f));
            }

            float inT = Math.min(1f, (now - k.born) / (float) ENTER_MS);
            float outT = Math.max(0f, Math.min(1f, (now - k.expireAt) / (float) EXIT_MS));
            float slide = (1f - easeOutBack(inT)) + outT * outT;
            int shift = Math.round(slide * (w + cfg.marginX + 24));
            int x = right ? sw - w - cfg.marginX + shift : cfg.marginX - shift;
            int y = bottom ? Math.round(sh - cfg.marginY - k.off - k.h) : Math.round(cfg.marginY + k.off);
            drawCard(c, tr, k, x, y, w, 1f - outT, now, cfg);
        }
    }

    private void layout(Card k, TextRenderer tr, PopConfig cfg, int w) {
        if (k.layoutW == w && k.lines != null) return;
        k.layoutW = w;
        String body = (k.msg.action() ? "* " : "") + k.msg.text();
        k.lines = Draw.wrap(tr, body, w - 14, Math.max(1, Math.min(8, cfg.maxLines)));
        k.h = 15 + k.lines.size() * 9 + 5;
        k.tags.clear();
        for (String b : k.msg.badges()) {
            switch (b) {
                case "broadcaster" -> k.tags.add(new Tag("HOST", Draw.RED));
                case "moderator" -> k.tags.add(new Tag("MOD", Draw.GREEN));
                case "vip" -> k.tags.add(new Tag("VIP", Draw.PINK));
                case "subscriber", "founder" -> k.tags.add(new Tag("SUB", Draw.SKY));
                default -> { }
            }
        }
    }

    private void drawCard(DrawContext c, TextRenderer tr, Card k, int x, int y, int w, float fade, long now, PopConfig cfg) {
        int h = k.h;
        int bg = Draw.a(Math.round(cfg.opacity * fade), 0x000000);

        c.fill(x + 1, y + 2, x + w + 1, y + h + 2, Draw.a(Math.round(40 * fade), 0x000000));
        Draw.rrect(c, x, y, w, h, bg);
        c.fillGradient(x + 1, y + 1, x + w - 1, y + 13, Draw.a(Math.round(38 * fade), Draw.SKY), 0x00000000);
        Draw.outline(c, x - 1, y - 1, x + w + 1, y + h + 1, Draw.a(Math.round(40 * fade), Draw.SKY));
        Draw.outline(c, x, y, x + w, y + h, Draw.a(Math.round(170 * fade), Draw.SKY));

        int accent = k.msg.color() >= 0 ? Draw.readable(k.msg.color()) : (Draw.SKY & 0xFFFFFF);
        c.fill(x + 1, y + 3, x + 3, y + h - 3, Draw.a(Math.round(255 * fade), accent));

        // header: badges + name
        int hx = x + 8, hy = y + 4;
        if (cfg.badges) {
            for (Tag t : k.tags) {
                int tw = Math.round(tr.getWidth(t.label()) * 0.65f) + 5;
                Draw.rrect(c, hx, hy, tw, 8, Draw.a(Math.round(70 * fade), t.color()));
                Draw.scaled(c, tr, t.label(), hx + 2.5f, hy + 1.6f, t.color(), 0.65f, false);
                hx += tw + 2;
            }
        }
        String name = Draw.trim(tr, k.msg.user(), Math.max(30, x + w - hx - 6));
        c.drawText(tr, Text.literal(name).formatted(Formatting.BOLD), hx, hy, Draw.a(255, accent), true);

        // message
        int ly = y + 15;
        for (String line : k.lines) {
            c.drawText(tr, line, x + 8, ly, Draw.WHITE, true);
            ly += 9;
        }

        // countdown line
        float rem = Math.max(0f, Math.min(1f, 1f - (now - k.born) / (float) Math.max(1, k.expireAt - k.born)));
        c.fill(x + 3, y + h - 2, x + 3 + Math.round((w - 6) * rem), y + h - 1, Draw.a(Math.round(190 * fade), Draw.SKY));
    }

    private static float easeOutBack(float t) {
        float c1 = 1.4f, c3 = c1 + 1f;
        float u = t - 1f;
        return 1f + c3 * u * u * u + c1 * u * u;
    }
}
