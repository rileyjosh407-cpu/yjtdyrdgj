package dev.basefinder;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Client-side Base Finder for Minecraft 1.21.11 + Fabric. */
public final class BaseFinderClient implements ClientModInitializer {
    private static KeyBinding toggleKey;
    private static KeyBinding rescanKey;
    private static KeyBinding increaseRadiusKey;
    private static KeyBinding decreaseRadiusKey;

    private static boolean enabled = false;
    private static int tickCounter = 0;
    private static int radius = 24;
    private static final int MIN_RADIUS = 8;
    private static final int MAX_RADIUS = 64;
    private static final int VERTICAL_RADIUS = 12;
    private static final int MAX_RESULTS = 12;
    private static final List<FoundBlock> found = new ArrayList<>();
    private static String scanStatus = "Press G to enable";
    private static final KeyBinding.Category CATEGORY =
            KeyBinding.Category.create(Identifier.of("basefinder", "controls"));

    @Override
    public void onInitializeClient() {
        toggleKey = registerKey("key.basefinder.toggle", GLFW.GLFW_KEY_G);
        rescanKey = registerKey("key.basefinder.rescan", GLFW.GLFW_KEY_R);
        increaseRadiusKey = registerKey("key.basefinder.radius_increase", GLFW.GLFW_KEY_RIGHT_BRACKET);
        decreaseRadiusKey = registerKey("key.basefinder.radius_decrease", GLFW.GLFW_KEY_LEFT_BRACKET);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleKey.wasPressed()) {
                enabled = !enabled;
                if (!enabled) {
                    found.clear();
                    scanStatus = "Disabled";
                } else {
                    scanStatus = "Scanning nearby loaded blocks...";
                    tickCounter = 39;
                }
                notifyPlayer(client, "Base Finder: " + (enabled ? "ON" : "OFF"));
            }
            while (rescanKey.wasPressed()) {
                if (enabled && client.player != null && client.world != null) {
                    scan(client);
                    notifyPlayer(client, "Base Finder: scan complete (" + found.size() + " displayed)");
                } else {
                    notifyPlayer(client, "Base Finder is off. Press G first.");
                }
            }
            while (increaseRadiusKey.wasPressed()) {
                radius = Math.min(MAX_RADIUS, radius + 8);
                notifyPlayer(client, "Base Finder radius: " + radius + " blocks");
                if (enabled) tickCounter = 39;
            }
            while (decreaseRadiusKey.wasPressed()) {
                radius = Math.max(MIN_RADIUS, radius - 8);
                notifyPlayer(client, "Base Finder radius: " + radius + " blocks");
                if (enabled) tickCounter = 39;
            }
            if (enabled && client.player != null && client.world != null && ++tickCounter >= 40) {
                tickCounter = 0;
                scan(client);
            }
        });

        HudRenderCallback.EVENT.register((drawContext, tickCounter) ->
                renderHud(MinecraftClient.getInstance(), drawContext));
    }

    private static KeyBinding registerKey(String translationKey, int keyCode) {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding(
                translationKey, InputUtil.Type.KEYSYM, keyCode, CATEGORY));
    }

    private static void notifyPlayer(MinecraftClient client, String message) {
        if (client.player != null) client.player.sendMessage(Text.literal(message), true);
    }

    private static void scan(MinecraftClient client) {
        if (client.player == null || client.world == null) return;
        BlockPos center = client.player.getBlockPos();
        Vec3d playerPos = new Vec3d(client.player.getX(), client.player.getY(), client.player.getZ());
        List<FoundBlock> results = new ArrayList<>();

        for (int x = -radius; x <= radius; x++) {
            for (int y = -VERTICAL_RADIUS; y <= VERTICAL_RADIUS; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = center.add(x, y, z);
                    if (!client.world.isChunkLoaded(pos)) continue;
                    Block block = client.world.getBlockState(pos).getBlock();
                    if (isBaseClue(block)) {
                        double distance = Math.sqrt(pos.getSquaredDistance(playerPos));
                        results.add(new FoundBlock(pos.toImmutable(),
                                Registries.BLOCK.getId(block).getPath(), distance));
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
        return block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST || block == Blocks.BARREL
                || block == Blocks.ENDER_CHEST || block == Blocks.SHULKER_BOX
                || block == Blocks.HOPPER || block == Blocks.DROPPER || block == Blocks.DISPENSER
                || block == Blocks.FURNACE || block == Blocks.BLAST_FURNACE || block == Blocks.SMOKER
                || block == Blocks.CRAFTING_TABLE || block == Blocks.ENCHANTING_TABLE
                || block == Blocks.ANVIL || block == Blocks.CHIPPED_ANVIL || block == Blocks.DAMAGED_ANVIL
                || block == Blocks.BREWING_STAND || block == Blocks.SMITHING_TABLE
                || block == Blocks.GRINDSTONE || block == Blocks.STONECUTTER || block == Blocks.LECTERN
                || block == Blocks.LOOM || block == Blocks.CARTOGRAPHY_TABLE || block == Blocks.JUKEBOX
                || block == Blocks.BEEHIVE || block == Blocks.BEE_NEST;
    }

    private static void renderHud(MinecraftClient client, DrawContext context) {
        if (!enabled || client.player == null || client.options.hudHidden) return;
        int x = 8, y = 8;
        context.fill(x - 4, y - 4, x + 330, y + 50 + found.size() * 11, 0xB51B1028);
        context.drawTextWithShadow(client.textRenderer, Text.literal("BASE FINDER"), x, y, 0xFFD36BFF);
        y += 13;
        context.drawTextWithShadow(client.textRenderer,
                Text.literal("Radius " + radius + "  [ ] adjust  [R] rescan  [G] toggle"), x, y, 0xFFE4C9FF);
        y += 13;
        context.drawTextWithShadow(client.textRenderer, Text.literal(scanStatus), x, y, 0xFFFFFFFF);
        y += 13;
        if (found.isEmpty()) {
            context.drawTextWithShadow(client.textRenderer,
                    Text.literal("No clues in loaded blocks nearby"), x, y, 0xFFB7B0C2);
            return;
        }
        for (FoundBlock item : found) {
            BlockPos p = item.pos();
            String line = String.format("%s  %d %d %d  (%.0fm)",
                    item.name(), p.getX(), p.getY(), p.getZ(), item.distance());
            context.drawTextWithShadow(client.textRenderer, Text.literal(line), x, y, 0xFFFFFFFF);
            y += 11;
        }
    }

    private record FoundBlock(BlockPos pos, String name, double distance) { }
}
