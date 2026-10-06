package com.synapmc.library;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.synapmc.SynapMcShared;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Base64;
import java.util.UUID;

public final class FScreenshot {

    private static final Gson GSON = new Gson();

    private FScreenshot() {}

    public static boolean send(String name, String resolution, Integer x, Integer y, Integer width, Integer height) {
        return send(null, name, resolution, x, y, width, height);
    }

    public static boolean send(String target, String name, String resolution, Integer x, Integer y, Integer width, Integer height) {
        if (SynapMcShared.server == null || !SynapMcShared.server.hasClients()) return false;
        if (target != null && !target.trim().isEmpty() && !SynapMcShared.server.hasClient(target)) return false;
        File screenshotFile = null;
        try {
            if (SynapMcShared.core == null) return false;
            screenshotFile = SynapMcShared.core.captureMinecraftScreenshot(tempScreenshotName(name));
            BufferedImage image = ImageIO.read(screenshotFile);
            if (image == null) {
                SynapMcShared.error("[SynapMc] screenshot failed: Minecraft screenshot file could not be read.");
                return false;
            }
            image = crop(image, x, y, width, height);
            int[] targetSize = parseResolution(resolution);
            if (targetSize != null) {
                image = resize(image, targetSize[0], targetSize[1]);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!ImageIO.write(image, "png", out)) return false;

            JsonObject obj = new JsonObject();
            obj.addProperty("type", "screenshot");
            obj.addProperty("name", name != null && !name.isEmpty() ? name : "screenshot");
            obj.addProperty("mime", "image/png");
            obj.addProperty("width", image.getWidth());
            obj.addProperty("height", image.getHeight());
            obj.addProperty("data", Base64.getEncoder().encodeToString(out.toByteArray()));
            String json = GSON.toJson(obj);
            if (target == null || target.trim().isEmpty()) {
                return SynapMcShared.server.broadcastChecked(json);
            }
            return SynapMcShared.server.sendTo(target, json);
        } catch (Exception e) {
            SynapMcShared.error("[SynapMc] screenshot failed: " + e.getMessage());
            return false;
        } finally {
            if (screenshotFile != null && screenshotFile.exists() && !screenshotFile.delete()) {
                screenshotFile.deleteOnExit();
            }
        }
    }

    private static BufferedImage crop(BufferedImage source, Integer x, Integer y, Integer width, Integer height) {
        if (x == null || y == null || width == null || height == null || width <= 0 || height <= 0) {
            return source;
        }
        Rectangle image = new Rectangle(0, 0, source.getWidth(), source.getHeight());
        Rectangle requested = new Rectangle(x, y, width, height);
        Rectangle clipped = requested.intersection(image);
        return clipped.isEmpty() ? source : source.getSubimage(clipped.x, clipped.y, clipped.width, clipped.height);
    }

    private static String tempScreenshotName(String name) {
        String base = name != null && !name.trim().isEmpty() ? name.trim() : "screenshot";
        base = base.replaceAll("[^a-zA-Z0-9_.-]", "_");
        if (!base.toLowerCase().endsWith(".png")) base += ".png";
        int dot = base.toLowerCase().lastIndexOf(".png");
        return base.substring(0, dot) + "-" + UUID.randomUUID() + base.substring(dot);
    }

    private static int[] parseResolution(String resolution) {
        if (resolution == null || resolution.trim().isEmpty()) return null;
        String[] parts = resolution.toLowerCase().replace(" ", "").split("x");
        if (parts.length != 2) return null;
        try {
            int w = Integer.parseInt(parts[0]);
            int h = Integer.parseInt(parts[1]);
            return w > 0 && h > 0 ? new int[] { w, h } : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static BufferedImage resize(BufferedImage source, int width, int height) {
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        try {
            g.drawImage(source, 0, 0, width, height, null);
        } finally {
            g.dispose();
        }
        return out;
    }
}
