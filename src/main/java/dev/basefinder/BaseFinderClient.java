package dev.basefinder;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.BaseFinderRenderLayers;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.render.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
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
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Properties;

/** Client-side Base Finder for Minecraft 1.21.11 + Fabric. */
public final class BaseFinderClient implements ClientModInitializer {
    private static KeyBinding toggleKey, rescanKey, increaseRadiusKey, decreaseRadiusKey, configKey, freecamKey;
    private static boolean freecamEnabled = false;
    private static Entity freecamCamera;
    private static double freecamSpeed = 0.35;
    private static boolean enabled = false;
    private static boolean chunkFinderEnabled = true;
    private static int tickCounter = 0;
    private static boolean highlightsEnabled = true;
    private static boolean oreXrayEnabled = false;
    private static int radius = 24;
    private static int sensitivity = 3;
    private static int simulationDistance = 4;
    private static final int MIN_RADIUS = 8, MAX_RADIUS = 64, MAX_RESULTS = 64;
    private static final List<FoundBlock> found = new ArrayList<>();
    private static final Map<Block, Boolean> blockFilters = new LinkedHashMap<>();
    private static final List<Block> ORE_BLOCKS = List.of(
            Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE,
            Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE,
            Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE,
            Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE,
            Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE,
            Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE,
            Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE,
            Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE,
            Blocks.NETHER_QUARTZ_ORE, Blocks.NETHER_GOLD_ORE, Blocks.ANCIENT_DEBRIS);
    private static final List<Block> filterBlocks = new ArrayList<>(List.of(Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.BARREL, Blocks.ENDER_CHEST, Blocks.SHULKER_BOX, Blocks.HOPPER, Blocks.DROPPER, Blocks.DISPENSER, Blocks.FURNACE, Blocks.BLAST_FURNACE, Blocks.SMOKER, Blocks.CRAFTING_TABLE, Blocks.ENCHANTING_TABLE, Blocks.ANVIL, Blocks.CHIPPED_ANVIL, Blocks.DAMAGED_ANVIL, Blocks.BREWING_STAND, Blocks.SMITHING_TABLE, Blocks.GRINDSTONE, Blocks.STONECUTTER, Blocks.LECTERN, Blocks.LOOM, Blocks.CARTOGRAPHY_TABLE, Blocks.JUKEBOX, Blocks.BEEHIVE, Blocks.BEE_NEST, Blocks.BUDDING_AMETHYST, Blocks.AMETHYST_BLOCK, Blocks.AMETHYST_CLUSTER, Blocks.LARGE_AMETHYST_BUD, Blocks.MEDIUM_AMETHYST_BUD, Blocks.SMALL_AMETHYST_BUD, Blocks.SPAWNER));
    static { filterBlocks.addAll(ORE_BLOCKS); }
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
        freecamKey = registerKey("key.basefinder.freecam", GLFW.GLFW_KEY_V);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (freecamKey.wasPressed()) toggleFreecam(client);
            if (freecamEnabled && client.player != null && client.world != null && freecamCamera != null) tickFreecam(client);
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
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (!enabled || !highlightsEnabled || client.world == null || client.player == null || found.isEmpty()) return;
            var matrices = context.matrices();
            if (matrices == null) return;
            Vec3d camera = client.player.getCameraPosVec(1.0F);
            matrices.push();
            matrices.translate(-camera.x, -camera.y, -camera.z);
            // Draw a translucent, depth-disabled fill first. This remains visible
            // through terrain even when Sodium/Iris alter the normal line pass.
            VertexConsumer throughWall = context.consumers().getBuffer(BaseFinderRenderLayers.THROUGH_WALL_FILL);
            for (FoundBlock item : found) {
                BlockPos pos = item.pos();
                drawThroughWallCube(matrices, throughWall, pos.getX(), pos.getY(), pos.getZ());
            }

            // Keep the normal crisp outline for blocks that are not occluded.
            VertexConsumer lines = context.consumers().getBuffer(RenderLayers.lines());
            for (FoundBlock item : found) {
                BlockPos pos = item.pos();
                var state = client.world.getBlockState(pos);
                var shape = state.getOutlineShape(client.world, pos);
                VertexRendering.drawOutline(matrices, lines, shape,
                        pos.getX(), pos.getY(), pos.getZ(), 0xFF33FFDD, 2.0f);
            }
            matrices.pop();
        });
    }


    private static void toggleFreecam(MinecraftClient client) {
        if (client.player == null || client.world == null) return;
        if (freecamEnabled) {
            freecamEnabled = false;
            client.setCameraEntity(client.player);
            if (freecamCamera != null) {
                freecamCamera.setRemoved(Entity.RemovalReason.DISCARDED);
                freecamCamera = null;
            }
            notifyPlayer(client, "Freecam disabled.");
            return;
        }
        Entity camera = EntityType.ARMOR_STAND.create(client.world, SpawnReason.LOAD);
        if (camera == null) {
            notifyPlayer(client, "Freecam could not create its camera entity.");
            return;
        }
        camera.refreshPositionAndAngles(client.player.getX(), client.player.getEyeY(),
                client.player.getZ(), client.player.getYaw(), client.player.getPitch());
        camera.setNoGravity(true);
        camera.noClip = true;
        camera.setInvisible(true);
        camera.setId(-1337);
        client.world.addEntity(camera);
        freecamCamera = camera;
        freecamEnabled = true;
        client.setCameraEntity(camera);
        notifyPlayer(client, "Freecam enabled (V to return).");
    }

    private static void tickFreecam(MinecraftClient client) {
        if (client.player == null || freecamCamera == null) return;
        double forward = 0.0, strafe = 0.0, vertical = 0.0;
        long window = client.getWindow().getHandle();
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_W) == GLFW.GLFW_PRESS) forward += 1.0;
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_S) == GLFW.GLFW_PRESS) forward -= 1.0;
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_D) == GLFW.GLFW_PRESS) strafe += 1.0;
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_A) == GLFW.GLFW_PRESS) strafe -= 1.0;
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_SPACE) == GLFW.GLFW_PRESS) vertical += 1.0;
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS) vertical -= 1.0;
        float yaw = client.player.getYaw();
        double radians = Math.toRadians(yaw);
        double dx = (strafe * Math.cos(radians) - forward * Math.sin(radians)) * freecamSpeed;
        double dz = (forward * Math.cos(radians) + strafe * Math.sin(radians)) * freecamSpeed;
        freecamCamera.setPosition(freecamCamera.getX() + dx, freecamCamera.getY() + vertical * freecamSpeed,
                freecamCamera.getZ() + dz);
        // Follow the player's live view angles, but don't mutate player velocity each tick;
        // doing so can fight vanilla movement/network updates and cause jitter.
        freecamCamera.setYaw(client.player.getYaw());
        freecamCamera.setPitch(client.player.getPitch());
    }

    /**
     * Adds a translucent cube using the custom depth-disabled pipeline. The inset
     * avoids overly bright seams where adjacent highlighted blocks touch.
     */
    private static void drawThroughWallCube(net.minecraft.client.util.math.MatrixStack matrices,
                                            VertexConsumer out, int x, int y, int z) {
        var matrix = matrices.peek().getPositionMatrix();
        float x0 = x + 0.025f, y0 = y + 0.025f, z0 = z + 0.025f;
        float x1 = x + 0.975f, y1 = y + 0.975f, z1 = z + 0.975f;
        int alpha = 72;

        // Front and back
        vertex(out, matrix, x0, y0, z1, alpha); vertex(out, matrix, x1, y0, z1, alpha);
        vertex(out, matrix, x1, y1, z1, alpha); vertex(out, matrix, x0, y1, z1, alpha);
        vertex(out, matrix, x1, y0, z0, alpha); vertex(out, matrix, x0, y0, z0, alpha);
        vertex(out, matrix, x0, y1, z0, alpha); vertex(out, matrix, x1, y1, z0, alpha);
        // Left and right
        vertex(out, matrix, x0, y0, z0, alpha); vertex(out, matrix, x0, y0, z1, alpha);
        vertex(out, matrix, x0, y1, z1, alpha); vertex(out, matrix, x0, y1, z0, alpha);
        vertex(out, matrix, x1, y0, z1, alpha); vertex(out, matrix, x1, y0, z0, alpha);
        vertex(out, matrix, x1, y1, z0, alpha); vertex(out, matrix, x1, y1, z1, alpha);
        // Top and bottom
        vertex(out, matrix, x0, y1, z1, alpha); vertex(out, matrix, x1, y1, z1, alpha);
        vertex(out, matrix, x1, y1, z0, alpha); vertex(out, matrix, x0, y1, z0, alpha);
        vertex(out, matrix, x0, y0, z0, alpha); vertex(out, matrix, x1, y0, z0, alpha);
        vertex(out, matrix, x1, y0, z1, alpha); vertex(out, matrix, x0, y0, z1, alpha);
    }

    private static void vertex(VertexConsumer out, org.joml.Matrix4f matrix,
                               float x, float y, float z, int alpha) {
        out.vertex(matrix, x, y, z).color(51, 255, 221, alpha);
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
        highlightsEnabled = Boolean.parseBoolean(p.getProperty("highlightsEnabled", "true"));
        oreXrayEnabled = Boolean.parseBoolean(p.getProperty("oreXrayEnabled", "false"));
        blockFilters.clear();
        for (Block block : filterBlocks) {
            String key = "block." + Registries.BLOCK.getId(block).toString().replace(':', '.');
            blockFilters.put(block, Boolean.parseBoolean(p.getProperty(key, "true")));
        }
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
        p.setProperty("highlightsEnabled", Boolean.toString(highlightsEnabled));
        p.setProperty("oreXrayEnabled", Boolean.toString(oreXrayEnabled));
        for (Block block : filterBlocks) {
            String key = "block." + Registries.BLOCK.getId(block).toString().replace(':', '.');
            p.setProperty(key, Boolean.toString(blockFilters.getOrDefault(block, true)));
        }
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
                    if ((isOreBlock(block) && oreXrayEnabled && blockFilters.getOrDefault(block, true)) || isBaseClue(block)) {
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


    private static boolean isOreBlock(Block block) { return ORE_BLOCKS.contains(block); }

    private static boolean isBaseClue(Block block) {
        if (isOreBlock(block)) return false;
        // Only explicitly supported filter blocks are clues. Unknown blocks (including air,
        // grass, dirt, stone, and other terrain) must never be added to the results.
        if (!blockFilters.containsKey(block) || !blockFilters.get(block)) return false;
        boolean geode = block == Blocks.BUDDING_AMETHYST || block == Blocks.AMETHYST_BLOCK
                || block == Blocks.AMETHYST_CLUSTER || block == Blocks.LARGE_AMETHYST_BUD
                || block == Blocks.MEDIUM_AMETHYST_BUD || block == Blocks.SMALL_AMETHYST_BUD;
        return !geode || chunkFinderEnabled;
    }

    private static final class FinderConfigScreen extends Screen {
        private int filterPage = 0;
        private String activeTab = "Visual";
        private static final int FILTERS_PER_PAGE = 10;

        protected FinderConfigScreen() { super(Text.literal("Donut Client")); }

        @Override
        protected void init() {
            clearChildren();
            int side = Math.max(155, Math.min(195, width / 5));
            int left = side + 18;
            int contentWidth = Math.max(250, width - left - 18);
            int top = 58;

            addDrawableChild(ButtonWidget.builder(Text.literal("◉  Visual"), b -> { activeTab = "Visual"; init(); })
                    .dimensions(12, 112, side - 24, 24).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("◇  Blatant"), b -> { activeTab = "Blatant"; init(); })
                    .dimensions(12, 141, side - 24, 24).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("☆  Favorites"), b -> { activeTab = "Favorites"; init(); })
                    .dimensions(12, 170, side - 24, 24).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("◎  Appearance"), b -> { activeTab = "Appearance"; init(); })
                    .dimensions(12, 226, side - 24, 24).build());

            if (activeTab.equals("Visual") || activeTab.equals("Favorites")) {
                int cardW = Math.max(220, (contentWidth - 12) / 2);
                int card2X = left + cardW + 12;
                addDrawableChild(ButtonWidget.builder(Text.literal("Base Finder     " + (enabled ? "ON" : "OFF")), b -> {
                    enabled = !enabled;
                    if (!enabled) { found.clear(); scanStatus = "Disabled"; }
                    else { scanStatus = "Scanning nearby loaded blocks..."; tickCounter = 39; }
                    notifyPlayer(MinecraftClient.getInstance(), "Base Finder: " + (enabled ? "ON" : "OFF"));
                    init();
                }).dimensions(left, top, cardW, 28).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("Ore X-ray       " + (oreXrayEnabled ? "ON" : "OFF")), b -> {
                    oreXrayEnabled = !oreXrayEnabled;
                    saveConfig();
                    if (enabled) tickCounter = 39;
                    init();
                }).dimensions(card2X, top, cardW, 28).build());

                addDrawableChild(ButtonWidget.builder(moduleLabel(), b -> {
                    chunkFinderEnabled = !chunkFinderEnabled;
                    b.setMessage(moduleLabel()); saveConfig();
                    if (enabled) tickCounter = 39;
                }).dimensions(left, top + 36, cardW, 22).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("Highlights: " + (highlightsEnabled ? "ON" : "OFF")), b -> {
                    highlightsEnabled = !highlightsEnabled;
                    b.setMessage(Text.literal("Highlights: " + (highlightsEnabled ? "ON" : "OFF")));
                    saveConfig();
                }).dimensions(card2X, top + 36, cardW, 22).build());

                int half = (contentWidth - 8) / 2;
                addDrawableChild(ButtonWidget.builder(sensitivityLabel(), b -> {
                    sensitivity = sensitivity % 5 + 1; b.setMessage(sensitivityLabel()); saveConfig();
                    if (enabled) tickCounter = 39;
                }).dimensions(left, top + 64, half, 20).build());
                addDrawableChild(ButtonWidget.builder(distanceLabel(), b -> {
                    simulationDistance = simulationDistance >= 16 ? 2 : simulationDistance + 2;
                    b.setMessage(distanceLabel()); saveConfig(); if (enabled) tickCounter = 39;
                }).dimensions(left + half + 8, top + 64, half, 20).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("Scan radius: " + radius + " blocks"), b -> {
                    radius = radius >= MAX_RADIUS ? MIN_RADIUS : radius + 8;
                    b.setMessage(Text.literal("Scan radius: " + radius + " blocks")); saveConfig();
                    if (enabled) tickCounter = 39;
                }).dimensions(left, top + 90, contentWidth, 20).build());

                int pages = Math.max(1, (filterBlocks.size() + FILTERS_PER_PAGE - 1) / FILTERS_PER_PAGE);
                int first = filterPage * FILTERS_PER_PAGE;
                for (int i = first; i < Math.min(first + FILTERS_PER_PAGE, filterBlocks.size()); i++) {
                    final Block block = filterBlocks.get(i);
                    int local = i - first;
                    int bx = left + (local % 2) * (half + 8);
                    int by = top + 140 + (local / 2) * 22;
                    addDrawableChild(ButtonWidget.builder(filterLabel(block), b -> {
                        blockFilters.put(block, !blockFilters.getOrDefault(block, true));
                        b.setMessage(filterLabel(block)); saveConfig(); if (enabled) tickCounter = 39;
                    }).dimensions(bx, by, half, 20).build());
                }
                int navY = top + 140 + 5 * 22 + 2;
                addDrawableChild(ButtonWidget.builder(Text.literal("< Previous"), b -> {
                    filterPage = (filterPage + pages - 1) % pages; init();
                }).dimensions(left, navY, half, 20).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("Next >"), b -> {
                    filterPage = (filterPage + 1) % pages; init();
                }).dimensions(left + half + 8, navY, half, 20).build());
            } else if (activeTab.equals("Blatant")) {
                addDrawableChild(ButtonWidget.builder(Text.literal("Freecam: " + (freecamEnabled ? "ON" : "OFF") + "  [V]"), b -> {
                    toggleFreecam(MinecraftClient.getInstance()); init();
                }).dimensions(left, top, Math.min(360, contentWidth), 28).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("Freecam speed: " + String.format(java.util.Locale.ROOT, "%.2f", freecamSpeed)), b -> {
                    freecamSpeed = freecamSpeed >= 1.0 ? 0.15 : Math.round((freecamSpeed + 0.15) * 100.0) / 100.0;
                    b.setMessage(Text.literal("Freecam speed: " + String.format(java.util.Locale.ROOT, "%.2f", freecamSpeed)));
                }).dimensions(left, top + 34, Math.min(360, contentWidth), 24).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("Ore X-ray: " + (oreXrayEnabled ? "ON" : "OFF")), b -> {
                    oreXrayEnabled = !oreXrayEnabled; saveConfig(); if (enabled) tickCounter = 39; init();
                }).dimensions(left, top + 64, Math.min(360, contentWidth), 28).build());
            } else {
                addDrawableChild(ButtonWidget.builder(Text.literal("Accent: Purple / Blue"), b -> {
                    notifyPlayer(MinecraftClient.getInstance(), "Theme preview uses the Donut-inspired purple and blue palette.");
                }).dimensions(left, top, Math.min(360, contentWidth), 28).build());
            }
            addDrawableChild(ButtonWidget.builder(Text.literal("Done  •  ESC"), b -> close())
                    .dimensions(width - 116, height - 34, 100, 22).build());
        }

        private Text moduleLabel() { return Text.literal("Geode clues: " + (chunkFinderEnabled ? "ON" : "OFF")); }
        private Text sensitivityLabel() { return Text.literal("Sensitivity  " + sensitivity + " / 5"); }
        private Text distanceLabel() { return Text.literal("Simulation distance  " + simulationDistance + " chunks"); }
        private Text filterLabel(Block block) {
            String name = Registries.BLOCK.getId(block).getPath().replace('_', ' ');
            name = name.substring(0, 1).toUpperCase() + name.substring(1);
            return Text.literal((blockFilters.getOrDefault(block, true) ? "●  " : "○  ") + name);
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            context.fill(0, 0, width, height, 0xE9090C14);
            int side = Math.max(155, Math.min(195, width / 5));
            context.fill(3, 3, side + 8, height - 3, 0xF01A1428);
            context.fill(3, 3, side + 8, 5, 0xFFB64DFF);
            context.fill(side + 14, 3, width - 3, 45, 0xF011151E);
            context.fill(side + 14, 46, width - 3, height - 3, 0xF010131B);
            context.fill(12, 14, 32, 34, 0xFFB64DFF);
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("●"), 22, 18, 0xFFFFFFFF);
            context.drawTextWithShadow(textRenderer, Text.literal("DONUT"), 42, 15, 0xFFF6F3FF);
            context.drawTextWithShadow(textRenderer, Text.literal("CLIENT"), 42, 29, 0xFF9FA6B8);
            context.fill(14, 48, side - 4, 49, 0xFF383044);
            context.drawTextWithShadow(textRenderer, Text.literal("MODULES"), 20, 65, 0xFF9A91AC);
            context.drawTextWithShadow(textRenderer, Text.literal("GENERAL"), 20, 205, 0xFF9A91AC);
            context.fill(12, height - 50, side - 4, height - 12, 0xFF0B0D14);
            context.fill(18, height - 34, 26, height - 26, 0xFFB64DFF);
            context.drawTextWithShadow(textRenderer, Text.literal("Donut Client"), 34, height - 40, 0xFFF6F3FF);
            context.drawTextWithShadow(textRenderer, Text.literal((enabled ? "1" : "0") + " / 2 active"), 34, height - 26, 0xFF9FA6B8);
            context.drawTextWithShadow(textRenderer, Text.literal(activeTab + " modules"), side + 28, 17, 0xFFF1F2F7);
            context.drawTextWithShadow(textRenderer, Text.literal("Search modules..."), width - 175, 17, 0xFF7E8799);
            context.drawTextWithShadow(textRenderer, Text.literal("Left click: toggle  /  module cards: settings"), side + 18, height - 18, 0xFF9DA5B5);
            super.render(context, mouseX, mouseY, delta);
        }

        @Override
        public void close() { if (client != null) client.setScreen(null); }
    }

    private record FoundBlock(BlockPos pos, String name, double distance) { }
}
