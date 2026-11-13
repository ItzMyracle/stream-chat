package com.streampop;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Locale;

public class StreamPopMod implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("streampop");
    public static PopConfig config;
    public static KeyBinding openKey;
    public static KeyBinding connectKey;
    private static boolean autoTried;

    @Override
    public void onInitializeClient() {
        config = PopConfig.load();

        // Both keys can be changed in Options > Controls > Key Binds > "Stream Pop"
        KeyBinding.Category category = KeyBinding.Category.create(Identifier.of("streampop", "main"));
        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.streampop.open", GLFW.GLFW_KEY_K, category));
        connectKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.streampop.connect", GLFW.GLFW_KEY_J, category));

        HudElementRegistry.attachElementAfter(VanillaHudElements.CHAT,
                Identifier.of("streampop", "cards"), new MessageHud());

        ClientTickEvents.END_CLIENT_TICK.register(StreamPopMod::tick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> TwitchClient.disconnect());

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            dispatcher.register(ClientCommandManager.literal("streampop")
                .then(ClientCommandManager.literal("channel")
                    .then(ClientCommandManager.argument("name", StringArgumentType.word()).executes(ctx -> {
                        config.channel = TwitchClient.cleanChannel(StringArgumentType.getString(ctx, "name"));
                        config.save();
                        say(ctx.getSource(), "Channel: #" + config.channel, Formatting.GREEN);
                        return 1;
                    })))
                .then(ClientCommandManager.literal("connect").executes(ctx -> {
                    connect(MinecraftClient.getInstance());
                    return 1;
                }))
                .then(ClientCommandManager.literal("disconnect").executes(ctx -> {
                    TwitchClient.disconnect();
                    say(ctx.getSource(), "Chat disconnected.", Formatting.YELLOW);
                    return 1;
                }))
                .then(ClientCommandManager.literal("test")
                    .then(ClientCommandManager.argument("text", StringArgumentType.greedyString()).executes(ctx -> {
                        accept(new ChatMsg("StreamPop", 0x67C8FF, List.of("moderator", "subscriber"),
                                StringArgumentType.getString(ctx, "text"), false, System.currentTimeMillis()));
                        return 1;
                    })))
                .then(listCmd("banword", "Banned word", () -> config.banwords))
                .then(listCmd("blockuser", "Blocked user", () -> config.blockedUsers))
                .then(ClientCommandManager.literal("offset")
                    .then(ClientCommandManager.argument("x", IntegerArgumentType.integer(0, 400))
                        .then(ClientCommandManager.argument("y", IntegerArgumentType.integer(0, 400)).executes(ctx -> {
                            config.marginX = IntegerArgumentType.getInteger(ctx, "x");
                            config.marginY = IntegerArgumentType.getInteger(ctx, "y");
                            config.save();
                            say(ctx.getSource(), "Offset: " + config.marginX + ", " + config.marginY, Formatting.GREEN);
                            return 1;
                        }))))
                .executes(ctx -> {
                    say(ctx.getSource(), "Stream Pop - " + TwitchClient.status()
                            + " | channel: " + (config.channel.isBlank() ? "(none)" : "#" + config.channel), Formatting.AQUA);
                    say(ctx.getSource(), "/streampop channel <name> | connect | disconnect | test <text>"
                            + " | banword add/remove <w> | blockuser add/remove <n> | offset <x> <y>", Formatting.GRAY);
                    return 1;
                })));
    }

    private interface ListSupplier { List<String> get(); }

    private static LiteralArgumentBuilder<FabricClientCommandSource> listCmd(String name, String label, ListSupplier list) {
        return ClientCommandManager.literal(name)
            .then(ClientCommandManager.literal("add")
                .then(ClientCommandManager.argument("value", StringArgumentType.greedyString()).executes(ctx -> {
                    String v = StringArgumentType.getString(ctx, "value").trim();
                    if (!v.isEmpty() && list.get().stream().noneMatch(s -> s.equalsIgnoreCase(v))) list.get().add(v);
                    config.save();
                    say(ctx.getSource(), label + " added: " + v, Formatting.GREEN);
                    return 1;
                })))
            .then(ClientCommandManager.literal("remove")
                .then(ClientCommandManager.argument("value", StringArgumentType.greedyString()).executes(ctx -> {
                    String v = StringArgumentType.getString(ctx, "value").trim();
                    list.get().removeIf(s -> s.equalsIgnoreCase(v));
                    config.save();
                    say(ctx.getSource(), label + " removed: " + v, Formatting.YELLOW);
                    return 1;
                })));
    }

    private static void say(FabricClientCommandSource src, String msg, Formatting f) {
        src.sendFeedback(Text.literal(msg).formatted(f));
    }

    private static void tick(MinecraftClient client) {
        while (openKey.wasPressed()) {
            if (client.currentScreen == null) client.setScreen(new PopScreen());
        }
        while (connectKey.wasPressed()) {
            if (TwitchClient.state() == TwitchClient.State.OFF) connect(client);
            else {
                TwitchClient.disconnect();
                actionbar(client, "Stream chat OFF", Formatting.YELLOW);
            }
        }

        int n = 0;
        ChatMsg m;
        while (n++ < 20 && (m = TwitchClient.INBOX.poll()) != null) accept(m);

        if (client.world == null) {
            autoTried = false;
        } else if (config.autoConnect && !autoTried) {
            autoTried = true;
            if (!config.channel.isBlank() && TwitchClient.state() == TwitchClient.State.OFF) {
                TwitchClient.connect(config.channel);
            }
        }
    }

    public static void connect(MinecraftClient client) {
        if (config.channel.isBlank()) {
            actionbar(client, "Set your Twitch channel first (open Stream Pop settings)", Formatting.RED);
            return;
        }
        TwitchClient.connect(config.channel);
        actionbar(client, "Stream chat: connecting to #" + config.channel, Formatting.AQUA);
    }

    private static void actionbar(MinecraftClient client, String msg, Formatting f) {
        if (client.player != null) client.player.sendMessage(Text.literal(msg).formatted(f), true);
    }

    /** Filters a message and shows it. */
    public static void accept(ChatMsg m) {
        PopConfig c = config;
        if (c == null) return;
        if (c.hideCommands && m.text().startsWith("!")) return;
        for (String u : c.blockedUsers) if (u.equalsIgnoreCase(m.user())) return;
        String lower = m.text().toLowerCase(Locale.ROOT);
        for (String w : c.banwords) if (!w.isBlank() && lower.contains(w.toLowerCase(Locale.ROOT))) return;

        MessageHud.add(m, c);
        MinecraftClient mc = MinecraftClient.getInstance();
        if (c.sound && c.hud && mc.player != null) {
            mc.player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.25f, 1.7f);
        }
    }
}
