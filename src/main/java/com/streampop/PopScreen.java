package com.streampop;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Small settings window: black, see-through, light-blue accents. */
public class PopScreen extends Screen {
    private static final int VW = 320, VH = 204;
    private static final String[] CORNERS = {"Top left", "Top right", "Bottom left", "Bottom right"};
    private static final String[] SAMPLES = {
        "this stream is so good, I love it",
        "LETS GOOO that was insane",
        "first time here, hi everyone",
        "can you check the new update?",
        "gg well played"
    };

    private enum Tab { SETTINGS, HISTORY }

    private record Hit(int x1, int y1, int x2, int y2, Runnable action) {}

    private TextRenderer tr;
    private final List<Hit> hits = new ArrayList<>();
    private Tab tab = Tab.SETTINGS;
    private final long openStart = System.currentTimeMillis();
    private float sc = 1f, ox, oy;
    private int vmx, vmy;
    private boolean editing;
    private String text = "";
    private boolean dragSlider;
    private int sliderX, sliderW = 144;
    private int histScroll;
    private int sampleIdx;

    public PopScreen() {
        super(Text.literal("Stream Pop"));
    }

    private static PopConfig cfg() { return StreamPopMod.config; }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void close() {
        commitEdit();
        cfg().save();
        super.close();
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() != 0) return super.mouseClicked(click, doubled);
        commitEdit();
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (vmx >= h.x1() && vmx < h.x2() && vmy >= h.y1() && vmy < h.y2()) {
                h.action().run();
                break;
            }
        }
        return true;
    }

    @Override
    public boolean mouseDragged(Click click, double dx, double dy) {
        if (dragSlider) {
            setOpacityFromMouse();
            return true;
        }
        return super.mouseDragged(click, dx, dy);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (dragSlider) {
            dragSlider = false;
            cfg().save();
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (tab == Tab.HISTORY) histScroll += vertical > 0 ? -1 : 1;
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (editing) {
            int key = input.getKeycode();
            if (input.isEscape()) { editing = false; return true; }
            if (input.isEnter()) { commitEdit(); return true; }
            if (key == GLFW.GLFW_KEY_BACKSPACE) {
                if (!text.isEmpty()) text = text.substring(0, text.length() - 1);
                return true;
            }
            if (input.isPaste()) {
                String clip = MinecraftClient.getInstance().keyboard.getClipboard();
                if (clip != null) for (char ch : clip.toCharArray()) append(ch);
            }
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (!editing || !input.isValidChar()) return super.charTyped(input);
        for (char ch : input.asString().toCharArray()) append(ch);
        return true;
    }

    private void append(char ch) {
        boolean ok = Character.isLetterOrDigit(ch) || "_./:#@-".indexOf(ch) >= 0;
        if (ok && text.length() < 60) text += ch;
    }

    private void commitEdit() {
        if (!editing) return;
        editing = false;
        cfg().channel = TwitchClient.cleanChannel(text);
        cfg().save();
    }

    private void setOpacityFromMouse() {
        float t = (vmx - sliderX) / (float) sliderW;
        cfg().opacity = Math.max(15, Math.min(255, Math.round(t * 255f)));
    }

    // ----------------------------------------------------------------- render

    @Override
    public void render(DrawContext c, int mouseX, int mouseY, float delta) {
        tr = MinecraftClient.getInstance().textRenderer;
        long now = System.currentTimeMillis();
        float open = Math.min(1f, (now - openStart) / 200f);
        float e = 1f - (1f - open) * (1f - open) * (1f - open);

        c.fillGradient(0, 0, width, height, ((int) (60 * e) << 24), ((int) (90 * e) << 24));

        float base = Math.min(1f, Math.min((width - 10) / (float) VW, (height - 10) / (float) VH));
        sc = base * (0.94f + 0.06f * e);
        ox = (width - VW * sc) / 2f;
        oy = (height - VH * sc) / 2f;
        vmx = (int) ((mouseX - ox) / sc);
        vmy = (int) ((mouseY - oy) / sc);
        hits.clear();

        var m = c.getMatrices();
        m.pushMatrix();
        m.translate(ox, oy);
        m.scale(sc, sc);
        drawAll(c, now);
        m.popMatrix();
    }

    private void drawAll(DrawContext c, long now) {
        PopConfig cfg = cfg();
        int SKY = Draw.SKY;

        Draw.rrect(c, 0, 0, VW, VH, 0x99000000);
        c.fillGradient(1, 1, VW - 1, 27, 0xCC000000, 0x55000000);
        Draw.outline(c, -1, -1, VW + 1, VH + 1, Draw.a(50, SKY));
        Draw.outline(c, 0, 0, VW, VH, Draw.a(200, SKY));
        c.fill(1, 27, VW - 1, 28, Draw.a(220, SKY));

        Draw.scaled(c, tr, "STREAM POP", 10, 8, SKY, 1.25f, true);

        TwitchClient.State st = TwitchClient.state();
        int stCol = st == TwitchClient.State.ON ? Draw.GREEN : (st == TwitchClient.State.CONNECTING ? Draw.AMBER : Draw.MUTED);
        String stText = st == TwitchClient.State.ON ? "LIVE" : (st == TwitchClient.State.CONNECTING ? "..." : "OFF");
        Draw.rrect(c, 98, 8, 34, 12, Draw.a(70, stCol));
        txtC(c, stText, 115, 10, stCol);

        button(c, VW - 22, 7, 14, 14, "x", false, this::close);

        tabButton(c, 10, 32, 64, 14, "SETTINGS", Tab.SETTINGS);
        tabButton(c, 78, 32, 64, 14, "HISTORY", Tab.HISTORY);
        c.fill(10, 46, VW - 10, 47, Draw.a(60, SKY));

        if (tab == Tab.SETTINGS) drawSettings(c, cfg, st, now);
        else drawHistory(c, cfg);
    }

    private void drawSettings(DrawContext c, PopConfig cfg, TwitchClient.State st, long now) {
        final int LX = 10, LW = 146, RX = 166, RW = 144;

        // ---- left column
        txt(c, "TWITCH CHANNEL", LX, 52, Draw.MUTED);
        Draw.rrect(c, LX, 62, LW, 14, 0x90000000);
        Draw.outline(c, LX, 62, LX + LW, 76, editing ? Draw.a(255, Draw.SKY) : Draw.a(110, Draw.SKY));
        String shown = editing ? text : (cfg.channel.isEmpty() ? "" : "#" + cfg.channel);
        if (shown.isEmpty()) {
            txt(c, "type a channel name", LX + 5, 66, Draw.a(120, Draw.MUTED));
        } else {
            txt(c, Draw.trim(tr, shown, LW - 12), LX + 5, 66, Draw.WHITE);
        }
        if (editing && (now / 450) % 2 == 0) {
            int cx = LX + 5 + tr.getWidth(Draw.trim(tr, shown, LW - 12));
            c.fill(cx, 65, cx + 1, 74, Draw.SKY);
        }
        hit(LX, 62, LW, 14, () -> { editing = true; text = cfg.channel; });

        boolean on = st != TwitchClient.State.OFF;
        button(c, LX, 80, LW, 16, on ? "CHAT ON  (click to stop)" : "CHAT OFF  (click to start)", on, () -> {
            if (TwitchClient.state() == TwitchClient.State.OFF) StreamPopMod.connect(MinecraftClient.getInstance());
            else TwitchClient.disconnect();
        });
        txt(c, Draw.trim(tr, TwitchClient.status(), LW), LX, 100, Draw.MUTED);

        button(c, LX, 112, 70, 14, "Send test", false, () -> {
            String s = SAMPLES[sampleIdx++ % SAMPLES.length];
            StreamPopMod.accept(new ChatMsg("StreamPop", 0x67C8FF, List.of("moderator", "subscriber"), s, false, System.currentTimeMillis()));
        });
        button(c, LX + 76, 112, 70, 14, "Clear cards", false, MessageHud::clearCards);

        toggle(c, LX, 130, LW, "Show chat on screen", cfg.hud, () -> { cfg.hud = !cfg.hud; cfg.save(); });
        toggle(c, LX, 145, LW, "Hide !commands", cfg.hideCommands, () -> { cfg.hideCommands = !cfg.hideCommands; cfg.save(); });
        toggle(c, LX, 160, LW, "Auto-connect in world", cfg.autoConnect, () -> { cfg.autoConnect = !cfg.autoConnect; cfg.save(); });
        toggle(c, LX, 175, LW, "Sound ping", cfg.sound, () -> { cfg.sound = !cfg.sound; cfg.save(); });

        // ---- right column
        txt(c, "CORNER", RX, 52, Draw.MUTED);
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            int bx = RX + (i % 2) * 74, by = 62 + (i / 2) * 15;
            button(c, bx, by, 70, 13, CORNERS[i], cfg.corner == i, () -> { cfg.corner = idx; cfg.save(); });
        }

        int pct = Math.round(cfg.opacity * 100f / 255f);
        txt(c, "BACKGROUND  " + pct + "%", RX, 96, Draw.MUTED);
        sliderX = RX;
        sliderW = RW;
        Draw.rrect(c, RX, 107, RW, 8, 0x90000000);
        int fillW = Math.round((cfg.opacity / 255f) * RW);
        Draw.rrect(c, RX, 107, Math.max(2, fillW), 8, Draw.a(190, Draw.SKY));
        Draw.outline(c, RX, 107, RX + RW, 115, Draw.a(130, Draw.SKY));
        c.fill(RX + Math.max(0, Math.min(RW - 2, fillW - 1)), 105, RX + Math.max(2, Math.min(RW, fillW + 1)), 117, Draw.WHITE);
        hit(RX, 103, RW, 16, () -> { dragSlider = true; setOpacityFromMouse(); });

        stepper(c, RX, 122, RW, "Cards on screen", String.valueOf(cfg.maxCards),
                () -> { cfg.maxCards = Math.max(1, cfg.maxCards - 1); cfg.save(); },
                () -> { cfg.maxCards = Math.min(10, cfg.maxCards + 1); cfg.save(); });
        stepper(c, RX, 140, RW, "Seconds shown", String.valueOf(cfg.durationSeconds),
                () -> { cfg.durationSeconds = Math.max(2, cfg.durationSeconds - 1); cfg.save(); },
                () -> { cfg.durationSeconds = Math.min(30, cfg.durationSeconds + 1); cfg.save(); });
        stepper(c, RX, 158, RW, "Card width", String.valueOf(cfg.cardWidth),
                () -> { cfg.cardWidth = Math.max(120, cfg.cardWidth - 10); cfg.save(); },
                () -> { cfg.cardWidth = Math.min(260, cfg.cardWidth + 10); cfg.save(); });
        toggle(c, RX, 176, RW, "Role badges", cfg.badges, () -> { cfg.badges = !cfg.badges; cfg.save(); });

        txt(c, "Change keys: Options > Controls > Stream Pop", LX, 193, Draw.a(150, Draw.MUTED));
    }

    private void drawHistory(DrawContext c, PopConfig cfg) {
        List<ChatMsg> list = new ArrayList<>(MessageHud.HISTORY);
        Collections.reverse(list);
        int rows = 10, rh = 14;
        histScroll = Math.max(0, Math.min(histScroll, Math.max(0, list.size() - rows)));
        if (list.isEmpty()) {
            txtC(c, "No messages yet", VW / 2, 90, Draw.MUTED);
            txtC(c, "Connect your channel or press Send test", VW / 2, 103, Draw.a(150, Draw.MUTED));
            return;
        }
        for (int i = 0; i < rows && i + histScroll < list.size(); i++) {
            ChatMsg m = list.get(i + histScroll);
            int y = 52 + i * rh;
            if (i % 2 == 0) c.fill(10, y - 1, VW - 10, y + rh - 2, Draw.a(22, Draw.SKY));
            int col = m.color() >= 0 ? Draw.a(255, Draw.readable(m.color())) : Draw.SKY;
            c.drawText(tr, Draw.trim(tr, m.user(), 62), 16, y + 2, col, true);
            txt(c, Draw.trim(tr, m.text(), 188), 82, y + 2, Draw.WHITE);
            int bx = VW - 46;
            boolean hov = inside(bx, y, 32, 11);
            Draw.rrect(c, bx, y, 32, 11, Draw.a(hov ? 110 : 55, Draw.RED));
            txtC(c, "block", bx + 16, y + 2, Draw.RED);
            hit(bx, y, 32, 11, () -> {
                if (cfg.blockedUsers.stream().noneMatch(u -> u.equalsIgnoreCase(m.user()))) cfg.blockedUsers.add(m.user());
                cfg.save();
            });
        }
        if (list.size() > rows) {
            int h = rows * rh;
            int barH = Math.max(10, h * rows / list.size());
            int barY = 52 + (h - barH) * histScroll / Math.max(1, list.size() - rows);
            c.fill(VW - 8, 52, VW - 6, 52 + h, Draw.a(40, Draw.SKY));
            c.fill(VW - 8, barY, VW - 6, barY + barH, Draw.a(200, Draw.SKY));
        }
    }

    // ---------------------------------------------------------------- widgets

    private void hit(int x, int y, int w, int h, Runnable r) {
        hits.add(new Hit(x, y, x + w, y + h, r));
    }

    private boolean inside(int x, int y, int w, int h) {
        return vmx >= x && vmx < x + w && vmy >= y && vmy < y + h;
    }

    private void button(DrawContext c, int x, int y, int w, int h, String label, boolean active, Runnable r) {
        boolean hov = inside(x, y, w, h);
        Draw.rrect(c, x, y, w, h, 0x78000000);
        if (active) Draw.rrect(c, x, y, w, h, Draw.a(hov ? 120 : 85, Draw.SKY));
        else if (hov) Draw.rrect(c, x, y, w, h, Draw.a(50, Draw.SKY));
        Draw.outline(c, x, y, x + w, y + h, Draw.a(active ? 235 : (hov ? 180 : 105), Draw.SKY));
        txtC(c, label, x + w / 2, y + (h - 8) / 2 + 1, active ? Draw.WHITE : Draw.SKY);
        hit(x, y, w, h, r);
    }

    private void tabButton(DrawContext c, int x, int y, int w, int h, String label, Tab t) {
        boolean on = tab == t;
        boolean hov = inside(x, y, w, h);
        if (on) Draw.rrect(c, x, y, w, h, Draw.a(70, Draw.SKY));
        else if (hov) Draw.rrect(c, x, y, w, h, Draw.a(30, Draw.SKY));
        txtC(c, label, x + w / 2, y + 3, on ? Draw.WHITE : Draw.MUTED);
        if (on) c.fill(x + 4, y + h - 1, x + w - 4, y + h + 1, Draw.SKY);
        hit(x, y, w, h, () -> { commitEdit(); tab = t; });
    }

    private void toggle(DrawContext c, int x, int y, int w, String label, boolean on, Runnable r) {
        boolean hov = inside(x - 2, y - 1, w + 4, 14);
        if (hov) Draw.rrect(c, x - 2, y - 1, w + 4, 14, Draw.a(30, Draw.SKY));
        txt(c, label, x, y + 3, on ? Draw.WHITE : Draw.MUTED);
        int px = x + w - 24;
        Draw.rrect(c, px, y + 2, 22, 10, on ? Draw.a(200, Draw.SKY) : 0x90000000);
        Draw.outline(c, px, y + 2, px + 22, y + 12, Draw.a(on ? 255 : 130, Draw.SKY));
        Draw.rrect(c, on ? px + 13 : px + 1, y + 3, 8, 8, on ? Draw.WHITE : Draw.a(220, Draw.SKY));
        hit(x - 2, y - 1, w + 4, 14, r);
    }

    private void stepper(DrawContext c, int x, int y, int w, String label, String value, Runnable minus, Runnable plus) {
        txt(c, label, x, y + 3, Draw.WHITE);
        button(c, x + w - 56, y, 14, 13, "-", false, minus);
        txtC(c, value, x + w - 35, y + 3, Draw.SKY);
        button(c, x + w - 14, y, 14, 13, "+", false, plus);
    }

    private void txt(DrawContext c, String s, int x, int y, int col) {
        c.drawText(tr, s, x, y, col, false);
    }

    private void txtC(DrawContext c, String s, int cx, int y, int col) {
        c.drawText(tr, s, cx - tr.getWidth(s) / 2, y, col, false);
    }
}
