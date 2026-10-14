package dev.basefinder;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;

/** Client-side Base Finder for Minecraft 1.21.11 + Fabric. */
public final class BaseFinderClient implements ClientModInitializer {
    private static KeyBinding toggleKey, rescanKey, increaseRadiusKey, decreaseRadiusKey, configKey;
    private static boolean enabled = false;
    private static boolean chunkFinderEnabled = true;
    private static int tickCounter = 0;
    private static int radius = 24;
    private static int sensitivity = 3;
    private static int simulationDistance = 4;
    private static final int MIN_RADIUS = 8, MAX_RADIUS = 64, MAX_RESULTS = 12;
    private static final List<FoundBlock> found = new ArrayList<>();
    private static String scanStatus = "Press G to enable";
    private static final KeyBinding.Category CATEGORY =
            KeyBinding.Category.create(Identifier.of("basefinder", "controls"));

    @Override
    public void onInitializeClient() {
        loadConfig();
        toggleKey = registerKey("key.basefinder.toggle", GLFW.GLFW_KEY_G);
        rescanKey = registerKey("key.basefinder.rescan", GLFW.GLFW_KEY_R);
        increaseRadiusKey = registerKey("key.basefinder.radius_increase", GLFW.GLFW_KEY_RIGHT_BRACKET);
        decreaseRadiusKey = registerKey("key.basefinder.radius_decrease", GLFW.GLFW_KEY_LEFT_BRACKET);
        configKey = registerKey("key.basefinder.config", GLFW.GLFW_KEY_F8);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (configKey.wasPressed()) {
                if (client.currentScreen == null) client.setScreen(new FinderConfigScreen());
            }
            while (toggleKey.wasPressed()) {
                enabled = !enabled;
                if (!enabled) { found.clear(); scanStatus = "Disabled"; }
                else { scanStatus = "Scanning nearby loaded blocks..."; tickCounter = 39; }
                notifyPlayer(client, "Base Finder: " + (enabled ? "ON" : "OFF"));
            }
            while (rescanKey.wasPressed()) {
                if (enabled && client.player != null && client.world != null) {
                    scan(client);
                    notifyPlayer(client, "Base Finder: scan complete (" + found.size() + " displayed)");
                } else notifyPlayer(client, "Base Finder is off. Press G first.");
            }
            while (increaseRadiusKey.wasPressed()) {
                radius = Math.min(MAX_RADIUS, radius + 8);
                saveConfig();
                notifyPlayer(client, "Base Finder radius: " + radius + " blocks");
                if (enabled) tickCounter = 39;
            }
            while (decreaseRadiusKey.wasPressed()) {
                radius = Math.max(MIN_RADIUS, radius - 8);
                saveConfig();
                notifyPlayer(client, "Base Finder radius: " + radius + " blocks");
                if (enabled) tickCounter = 39;
            }
            if (enabled && client.player != null && client.world != null && ++tickCounter >= 40) {
                tickCounter = 0;
                scan(client);
            }
        });
        HudRenderCallback.EVENT.register((drawContext, counter) ->
                renderHud(MinecraftClient.getInstance(), drawContext));
    }

    private static KeyBinding registerKey(String translationKey, int keyCode) {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding(translationKey, InputUtil.Type.KEYSYM, keyCode, CATEGORY));
    }

    private static void notifyPlayer(MinecraftClient client, String message) {
        if (client.player != null) client.player.sendMessage(Text.literal(message), true);
    }

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("base-finder.properties");
    }

    private static void loadConfig() {
        Properties p = new Properties();
        Path path = configPath();
        if (Files.exists(path)) {
            try (InputStream in = Files.newInputStream(path)) { p.load(in); }
            catch (IOException ignored) { }
        }
        sensitivity = clamp(parseInt(p.getProperty("sensitivity"), 3), 1, 5);
        simulationDistance = clamp(parseInt(p.getProperty("simulationDistance"), 4), 2, 16);
        radius = clamp(parseInt(p.getProperty("radius"), 24), MIN_RADIUS, MAX_RADIUS);
        chunkFinderEnabled = Boolean.parseBoolean(p.getProperty("chunkFinderEnabled", "true"));
    }

    private static int parseInt(String value, int fallback) {
        try { return Integer.parseInt(value); } catch (Exception ignored) { return fallback; }
    }
    private static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }

    private static void saveConfig() {
        Properties p = new Properties();
        p.setProperty("sensitivity", Integer.toString(sensitivity));
        p.setProperty("simulationDistance", Integer.toString(simulationDistance));
        p.setProperty("radius", Integer.toString(radius));
        p.setProperty("chunkFinderEnabled", Boolean.toString(chunkFinderEnabled));
        try (OutputStream out = Files.newOutputStream(configPath())) {
            p.store(out, "Base Finder settings");
        } catch (IOException ignored) { }
    }

    private static void scan(MinecraftClient client) {
        if (client.player == null || client.world == null) return;
        BlockPos center = client.player.getBlockPos();
        Vec3d playerPos = new Vec3d(client.player.getX(), client.player.getY(), client.player.getZ());
        List<FoundBlock> results = new ArrayList<>();
        int scanRadius = Math.min(radius, simulationDistance * 16);
        int verticalRadius = Math.min(12 + sensitivity * 2, 20);
        for (int x = -scanRadius; x <= scanRadius; x++) {
            for (int y = -verticalRadius; y <= verticalRadius; y++) {
                for (int z = -scanRadius; z <= scanRadius; z++) {
                    BlockPos pos = center.add(x, y, z);
                    if (!client.world.isChunkLoaded(pos)) continue;
                    Block block = client.world.getBlockState(pos).getBlock();
                    if (isBaseClue(block)) {
                        double distance = Math.sqrt(pos.getSquaredDistance(playerPos));
                        results.add(new FoundBlock(pos.toImmutable(), Registries.BLOCK.getId(block).getPath(), distance));
                    }
                }
            }
        }
        results.sort(Comparator.comparingDouble(FoundBlock::distance));
        found.clear();
        found.addAll(results.subList(0, Math.min(MAX_RESULTS, results.size())));
        scanStatus = results.size() + " clues found; showing nearest " + found.size();
    }

    private static boolean isBaseClue(Block block) {
        boolean storage = block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST || block == Blocks.BARREL
                || block == Blocks.ENDER_CHEST || block == Blocks.SHULKER_BOX || block == Blocks.HOPPER
                || block == Blocks.DROPPER || block == Blocks.DISPENSER || block == Blocks.FURNACE
                || block == Blocks.BLAST_FURNACE || block == Blocks.SMOKER || block == Blocks.CRAFTING_TABLE
                || block == Blocks.ENCHANTING_TABLE || block == Blocks.ANVIL || block == Blocks.CHIPPED_ANVIL
                || block == Blocks.DAMAGED_ANVIL || block == Blocks.BREWING_STAND || block == Blocks.SMITHING_TABLE
                || block == Blocks.GRINDSTONE || block == Blocks.STONECUTTER || block == Blocks.LECTERN
                || block == Blocks.LOOM || block == Blocks.CARTOGRAPHY_TABLE || block == Blocks.JUKEBOX
                || block == Blocks.BEEHIVE || block == Blocks.BEE_NEST;
        boolean geode = block == Blocks.BUDDING_AMETHYST || block == Blocks.AMETHYST_BLOCK
                || block == Blocks.AMETHYST_CLUSTER || block == Blocks.LARGE_AMETHYST_BUD
                || block == Blocks.MEDIUM_AMETHYST_BUD || block == Blocks.SMALL_AMETHYST_BUD;
        return (chunkFinderEnabled && geode) || storage;
    }

    private static void renderHud(MinecraftClient client, DrawContext context) {
        if (!enabled || client.player == null || client.options.hudHidden) return;
        int x = 8, y = 8;
        context.fill(x - 4, y - 4, x + 330, y + 50 + found.size() * 11, 0xB51B1028);
        context.drawTextWithShadow(client.textRenderer, Text.literal("BASE FINDER"), x, y, 0xFFD36BFF);
        y += 13;
        context.drawTextWithShadow(client.textRenderer,
                Text.literal("Radius " + radius + "  [ ] adjust  [R] rescan  [F8] config"), x, y, 0xFFE4C9FF);
        y += 13;
        context.drawTextWithShadow(client.textRenderer, Text.literal(scanStatus), x, y, 0xFFFFFFFF);
        y += 13;
        if (found.isEmpty()) {
            context.drawTextWithShadow(client.textRenderer, Text.literal("No clues in loaded blocks nearby"), x, y, 0xFFB7B0C2);
            return;
        }
        for (FoundBlock item : found) {
            BlockPos p = item.pos();
            String line = String.format("%s  %d %d %d  (%.0fm)", item.name(), p.getX(), p.getY(), p.getZ(), item.distance());
            context.drawTextWithShadow(client.textRenderer, Text.literal(line), x, y, 0xFFFFFFFF);
            y += 11;
        }
    }

    private static final class FinderConfigScreen extends Screen {
        protected FinderConfigScreen() { super(Text.literal("Base Finder Settings")); }

        @Override
        protected void init() {
            int cx = width / 2;
            int top = height / 4;
            addDrawableChild(ButtonWidget.builder(moduleLabel(), b -> {
                chunkFinderEnabled = !chunkFinderEnabled;
                b.setMessage(moduleLabel());
                saveConfig();
            }).dimensions(cx - 120, top, 240, 24).build());
            addDrawableChild(ButtonWidget.builder(sensitivityLabel(), b -> {
                sensitivity = sensitivity % 5 + 1;
                b.setMessage(sensitivityLabel());
                saveConfig();
            }).dimensions(cx - 120, top + 32, 240, 24).build());
            addDrawableChild(ButtonWidget.builder(distanceLabel(), b -> {
                simulationDistance = simulationDistance >= 16 ? 2 : simulationDistance + 2;
                b.setMessage(distanceLabel());
                saveConfig();
                if (enabled) tickCounter = 39;
            }).dimensions(cx - 120, top + 64, 240, 24).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("Scan radius: " + radius + " blocks (click to increase)"), b -> {
                radius = radius >= MAX_RADIUS ? MIN_RADIUS : radius + 8;
                b.setMessage(Text.literal("Scan radius: " + radius + " blocks (click to increase)"));
                saveConfig();
                if (enabled) tickCounter = 39;
            }).dimensions(cx - 120, top + 96, 240, 24).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close())
                    .dimensions(cx - 60, top + 136, 120, 24).build());
        }

        private Text moduleLabel() {
            return Text.literal("Visuals > Chunk Finder: " + (chunkFinderEnabled ? "ON" : "OFF"));
        }
        private Text sensitivityLabel() { return Text.literal("Sensitivity: " + sensitivity + "/5 (click to cycle)"); }
        private Text distanceLabel() { return Text.literal("Simulation distance: " + simulationDistance + " chunks (click to cycle)"); }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            renderBackground(context, mouseX, mouseY, delta);
            context.fill(width / 2 - 150, height / 4 - 30, width / 2 + 150, height / 4 + 175, 0xD51B1028);
            context.drawCenteredTextWithShadow(textRenderer, title, width / 2, height / 4 - 18, 0xFFD36BFF);
            context.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("F8 opens this menu. Click settings to change them."), width / 2, height / 4 + 168, 0xFFE4C9FF);
            super.render(context, mouseX, mouseY, delta);
        }

        @Override
        public void close() { if (client != null) client.setScreen(null); }
    }

    private record FoundBlock(BlockPos pos, String name, double distance) { }
}
