package net.arm.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public class BlockOutlineRenderer {
    private static final float R_WHITE = 1.0f, G_WHITE = 1.0f, B_WHITE = 1.0f;
    private static final float R_GREEN = 0.0f, G_GREEN = 1.0f, B_GREEN = 0.0f;
    private static final float LINE_ALPHA = 1.0f;
    private static final int CIRCLE_SEGMENTS = 64;

    // ТОЛЩИНА ЛИНИИ (в блоках). Настройте по своему вкусу
    private static final float LINE_THICKNESS = 0.04f;

    private static OutlineConfig config;

    public static void init() {
        config = OutlineConfig.getInstance();
        WorldRenderEvents.LAST.register(context -> {
            if (config != null && AreaClient.getInstance().isAreaActive()) {
                render(context);
            }
        });
    }

    private static void render(WorldRenderContext context) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || !config.globalOutlineEnabled) return;

        ItemStack mainHand = mc.player.getMainHandStack();
        if (mainHand.isEmpty()) return;

        OutlineConfig.OutlineEntry entry = config.getEntryFor(mainHand.getItem());
        if (entry == null || (entry.outlineEnabled != null && !entry.outlineEnabled)) return;

        double centerX = Math.floor(mc.player.getX());
        double centerZ = Math.floor(mc.player.getZ());
        double feetY = Math.floor(mc.player.getY());
        double renderY = feetY + 1.005;

        boolean otherPlayersInRange = hasOtherPlayersInRange(mc, feetY, centerX, centerZ, entry.radius, entry.shape);
        float r = otherPlayersInRange ? R_GREEN : R_WHITE;
        float g = otherPlayersInRange ? G_GREEN : G_WHITE;
        float b = otherPlayersInRange ? B_GREEN : B_WHITE;

        Vec3d cameraPos = context.camera().getPos();

        setupRenderSystem();

        if ("wall".equals(entry.shape)) {
            renderWall(context, cameraPos, mc.player, entry.radius, entry.radius, r, g, b);
        } else if ("circle".equals(entry.shape)) {
            renderFlatCircle(context, cameraPos, centerX + 0.5, renderY, centerZ + 0.5, entry.radius, r, g, b);
        } else {
            renderFlatSquare(context, cameraPos, centerX, renderY, centerZ, entry.radius, r, g, b);
        }
        cleanupRenderSystem();
    }

    private static void renderFlatCircle(WorldRenderContext context, Vec3d cameraPos, double cx, double cy, double cz, int rad, float r, float g, float b) {
        // Используем getDebugQuads() — он использует формат POSITION_COLOR и рендерит полигоны в мире
        VertexConsumer vc = context.consumers().getBuffer(RenderLayer.getDebugQuads());
        Matrix4f mat = context.matrixStack().peek().getPositionMatrix();

        double xC = cx - cameraPos.x;
        double y = cy - cameraPos.y;
        double zC = cz - cameraPos.z;

        for (int i = 0; i < CIRCLE_SEGMENTS; i++) {
            float angle1 = (float) (i * Math.PI * 2 / CIRCLE_SEGMENTS);
            float angle2 = (float) ((i + 1) * Math.PI * 2 / CIRCLE_SEGMENTS);

            double x1 = xC + Math.cos(angle1) * rad;
            double z1 = zC + Math.sin(angle1) * rad;
            double x2 = xC + Math.cos(angle2) * rad;
            double z2 = zC + Math.sin(angle2) * rad;

            drawFatLine(vc, mat, x1, y, z1, x2, y, z2, r, g, b);
        }
    }

    private static void renderFlatSquare(WorldRenderContext context, Vec3d cameraPos, double cx, double cy, double cz, int rad, float r, float g, float b) {
        VertexConsumer vc = context.consumers().getBuffer(RenderLayer.getDebugQuads());
        Matrix4f mat = context.matrixStack().peek().getPositionMatrix();

        double x1 = cx - rad - cameraPos.x;
        double x2 = cx + rad + 1 - cameraPos.x;
        double z1 = cz - rad - cameraPos.z;
        double z2 = cz + rad + 1 - cameraPos.z;
        double y = cy - cameraPos.y;

        drawFatLine(vc, mat, x1, y, z1, x2, y, z1, r, g, b);
        drawFatLine(vc, mat, x2, y, z1, x2, y, z2, r, g, b);
        drawFatLine(vc, mat, x2, y, z2, x1, y, z2, r, g, b);
        drawFatLine(vc, mat, x1, y, z2, x1, y, z1, r, g, b);
    }

    private static void renderWall(WorldRenderContext context, Vec3d cameraPos, PlayerEntity player, int radius, int distance, float r, float g, float b) {
        float pitch = player.getPitch(); //
        float yaw = (player.getYaw() % 360 + 360) % 360; //
        double px = Math.floor(player.getX()); //
        double pz = Math.floor(player.getZ()); //

        // Если игрок смотрит строго вверх или вниз
        if (pitch > 45 || pitch < -45) { //
            double py = Math.floor(player.getY()) + 1; //
            double yLevel = (pitch > 0) ? py - distance : py + distance; //
            renderBox(context, cameraPos, new Box(px - radius, yLevel, pz - radius, px + radius + 1, yLevel + 1, pz + radius + 1), r, g, b); //
            return; //
        }

        double wallBaseY = Math.floor(player.getEyeY()) - radius; //
        int direction = Math.round(yaw / 45f) % 8; //

        // Вычисляем дискретное смещение координат в зависимости от направления взгляда
        double cx = px;
        double cz = pz;

        switch (direction) {
            case 0 -> { // Юг (+Z)
                cz = pz + distance;
            }
            case 1 -> { // Юго-запад (-X, +Z)
                cx = px - distance;
                cz = pz + distance;
            }
            case 2 -> { // Запад (-X)
                cx = px - distance;
            }
            case 3 -> { // Северо-запад (-X, -Z)
                cx = px - distance;
                cz = pz - distance;
            }
            case 4 -> { // Север (-Z)
                cz = pz - distance;
            }
            case 5 -> { // Северо-восток (+X, -Z)
                cx = px + distance;
                cz = pz - distance;
            }
            case 6 -> { // Восток (+X)
                cx = px + distance;
            }
            case 7 -> { // Юго-восток (+X, +Z)
                cx = px + distance;
                cz = pz + distance;
            }
        }

        int wallSizeY = (radius * 2) + 1; //

        switch (direction) {
            case 0, 4 -> renderBox(context, cameraPos, new Box(cx - radius, wallBaseY, cz, cx + radius + 1, wallBaseY + wallSizeY, cz + 1), r, g, b); //
            case 2, 6 -> renderBox(context, cameraPos, new Box(cx, wallBaseY, cz - radius, cx + 1, wallBaseY + wallSizeY, cz + radius + 1), r, g, b); //
            case 1, 5 -> renderDiagonal(context, cameraPos, cx, cz, wallBaseY, radius, true, r, g, b); //
            case 3, 7 -> renderDiagonal(context, cameraPos, cx, cz, wallBaseY, radius, false, r, g, b); //
        }
    }

    private static void renderDiagonal(WorldRenderContext context, Vec3d cameraPos, double cx, double cz, double baseY, int radius, boolean flip, float r, float g, float b) {
        int wallHeight = radius * 2 + 1;
        for (int i = -radius; i <= radius; i++) {
            double x = cx + i;
            double z = cz + (flip ? i : -i);
            renderBox(context, cameraPos, new Box(x, baseY, z, x + 1, baseY + wallHeight, z + 1), r, g, b);
        }
    }

    private static void renderBox(WorldRenderContext context, Vec3d cameraPos, Box box, float r, float g, float b) {
        VertexConsumer vc = context.consumers().getBuffer(RenderLayer.getDebugQuads());
        Matrix4f mat = context.matrixStack().peek().getPositionMatrix();

        double x1 = box.minX - cameraPos.x, y1 = box.minY - cameraPos.y, z1 = box.minZ - cameraPos.z;
        double x2 = box.maxX - cameraPos.x, y2 = box.maxY - cameraPos.y, z2 = box.maxZ - cameraPos.z;

        drawFatLine(vc, mat, x1, y1, z1, x2, y1, z1, r, g, b); drawFatLine(vc, mat, x2, y1, z1, x2, y1, z2, r, g, b);
        drawFatLine(vc, mat, x2, y1, z2, x1, y1, z2, r, g, b); drawFatLine(vc, mat, x1, y1, z2, x1, y1, z1, r, g, b);
        drawFatLine(vc, mat, x1, y2, z1, x2, y2, z1, r, g, b); drawFatLine(vc, mat, x2, y2, z1, x2, y2, z2, r, g, b);
        drawFatLine(vc, mat, x2, y2, z2, x1, y2, z2, r, g, b); drawFatLine(vc, mat, x1, y2, z2, x1, y2, z1, r, g, b);
        drawFatLine(vc, mat, x1, y1, z1, x1, y2, z1, r, g, b); drawFatLine(vc, mat, x2, y1, z1, x2, y2, z1, r, g, b);
        drawFatLine(vc, mat, x1, y1, z2, x1, y2, z2, r, g, b); drawFatLine(vc, mat, x2, y1, z2, x2, y2, z2, r, g, b);
    }

    private static void drawFatLine(VertexConsumer vc, Matrix4f mat, double p1x, double p1y, double p1z, double p2x, double p2y, double p2z, float r, float g, float b) {
        Vector3f p1 = new Vector3f((float) p1x, (float) p1y, (float) p1z);
        Vector3f p2 = new Vector3f((float) p2x, (float) p2y, (float) p2z);

        Vector3f lineDir = new Vector3f(p2).sub(p1).normalize();
        Vector3f midPoint = new Vector3f(p1).add(p2).mul(0.5f);
        Vector3f camDir = new Vector3f(midPoint).normalize();

        // Вычисляем перпендикулярный вектор для создания ширины (билбординг)
        Vector3f offsetDir = new Vector3f(lineDir).cross(camDir).normalize();
        offsetDir.mul(LINE_THICKNESS / 2.0f);

        Vector3f v1 = new Vector3f(p1).add(offsetDir);
        Vector3f v2 = new Vector3f(p1).sub(offsetDir);
        Vector3f v3 = new Vector3f(p2).sub(offsetDir);
        Vector3f v4 = new Vector3f(p2).add(offsetDir);

        // Используем строго POSITION_COLOR без вызова лишних методов текстур/света
        vc.vertex(mat, v1.x, v1.y, v1.z).color(r, g, b, LINE_ALPHA);
        vc.vertex(mat, v2.x, v2.y, v2.z).color(r, g, b, LINE_ALPHA);
        vc.vertex(mat, v3.x, v3.y, v3.z).color(r, g, b, LINE_ALPHA);
        vc.vertex(mat, v4.x, v4.y, v4.z).color(r, g, b, LINE_ALPHA);
    }

    private static boolean hasOtherPlayersInRange(MinecraftClient mc, double feetY, double cX, double cZ, int r, String shape) {
        if (mc.world == null) return false;
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p.getUuid().equals(mc.player.getUuid())) continue;
            if (Math.abs(p.getY() - feetY) > 3.0) continue;
            double dx = p.getX() - (cX + 0.5);
            double dz = p.getZ() - (cZ + 0.5);
            if ("circle".equals(shape)) {
                if ((dx * dx + dz * dz) <= (double) (r * r)) return true;
            } else {
                if (Math.abs(dx) <= r + 0.5 && Math.abs(dz) <= r + 0.5) return true;
            }
        }
        return false;
    }

    private static void setupRenderSystem() {
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
    }

    private static void cleanupRenderSystem() {
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
    }
}