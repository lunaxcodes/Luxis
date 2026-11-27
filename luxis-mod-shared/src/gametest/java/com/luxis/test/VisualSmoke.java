package com.luxis.test;

import com.luxis.config.GuiConfig;
import com.luxis.gui.GuiTheme;
import com.luxis.gui.LuxisModScreen;
import com.luxis.module.Module;
import com.luxis.module.ModuleRegistry;
import com.luxis.module.Setting;
import com.luxis.module.impl.ClientTuning;
import com.luxis.module.impl.VisualModules;
import com.luxis.module.impl.ViewControls;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.List;

public final class VisualSmoke implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext test) {
        test.runOnClient(client -> checkCatalog());
        try (var world = test.worldBuilder().setUseConsistentSettings(true).create()) {
            test.waitFor(mc -> mc.player != null && mc.level != null, 800);
            test.waitTicks(35);
            test.runOnClient(mc -> {
                mc.player.setXRot(32F);
                for (String id : List.of("worldclock", "biomename", "inventoryspace", "bowdraw", "xpstock", "shieldwear")) {
                    ModuleRegistry.byId(id).enabled = true;
                }
            });
            test.waitTicks(15);
            test.takeScreenshot("luxis-hud-panels");
            test.runOnClient(mc -> ModuleRegistry.hudModules().forEach(module -> module.enabled = false));
            checkControls(test);
            var before = test.takeScreenshot("luxis-neutral");

            test.runOnClient(mc -> {
                VisualModules.GRADE.enabled = true;
                ((Setting.SliderSetting) VisualModules.GRADE.setting("saturation")).value = 0D;
            });
            test.waitTicks(15);
            var graded = test.takeScreenshot("luxis-desaturated");
            try {
                double originalChroma = chroma(ImageIO.read(before.toFile()));
                double gradedChroma = chroma(ImageIO.read(graded.toFile()));
                check(originalChroma > 8 && gradedChroma < originalChroma * 0.20,
                        "Color Grading did not desaturate the terrain: " + originalChroma + " → " + gradedChroma);
            } catch (IOException e) {
                throw new AssertionError("Could not read lens test screenshots", e);
            }
            test.runOnClient(mc -> VisualModules.GRADE.enabled = false);
            world.getServer().runCommand("time set midnight");
            test.waitTicks(15);
            var dark = test.takeScreenshot("luxis-night");
            var fullbright = ModuleRegistry.byId("fullbright");
            test.runOnClient(mc -> fullbright.enabled = true);
            test.waitTicks(15);
            var bright = test.takeScreenshot("luxis-fullbright");
            try {
                double night = light(ImageIO.read(dark.toFile())), lit = light(ImageIO.read(bright.toFile()));
                check(lit > night + 20, "Fullbright did not brighten the terrain: " + night + " → " + lit);
            } catch (IOException e) {
                throw new AssertionError("Could not read Fullbright screenshots", e);
            }
            test.runOnClient(mc -> fullbright.enabled = false);

            test.runOnClient(mc -> {
                for (var module : VisualModules.modules()) module.enabled = true;
                for (String id : List.of("quietsaveicon", "chatlayout", "biomeblendbudget", "clouddistancebudget", "menublurbudget")) {
                    ModuleRegistry.byId(id).enabled = true;
                }
                ClientTuning.changed();
            });
            test.waitTicks(15);
            test.takeScreenshot("luxis-all-visuals");

            test.runOnClient(mc -> GuiConfig.setTheme("Emerald"));
            test.setScreen(LuxisModScreen::new);
            test.waitTicks(30);
            var emerald = test.takeScreenshot("luxis-menu-emerald");
            test.runOnClient(mc -> GuiConfig.setTheme("Crimson"));
            test.waitTicks(15);
            var crimson = test.takeScreenshot("luxis-menu-crimson");
            try {
                double greenBias = greenBias(ImageIO.read(emerald.toFile()));
                double redBias = greenBias(ImageIO.read(crimson.toFile()));
                check(greenBias - redBias > 20, "Theme did not recolor the menu surfaces: " + greenBias + " vs " + redBias);
            } catch (IOException e) {
                throw new AssertionError("Could not read menu test screenshots", e);
            }
        }
    }

    private static void checkControls(ClientGameTestContext test) {
        var input = test.getInput();
        var zoom = ModuleRegistry.byId("zoom");
        var freelook = ModuleRegistry.byId("freelook");
        var hud = ModuleRegistry.byId("fps");
        var hold = InputConstants.getKey("key.keyboard.n");
        test.runOnClient(mc -> {
            zoom.enabled = true;
            freelook.enabled = true;
            hud.enabled = false;
            hud.key = hold;
            hud.trigger = Module.Trigger.HOLD;
            ((Setting.TextSetting) hud.setting("textPrefix")).set("Frames");
        });
        check(test.computeOnClient(mc -> Boolean.TRUE.equals(hud.get("bgOutline"))
                && "Frames".equals(hud.get("textPrefix"))), "HUD appearance settings are missing");
        input.holdKey(zoom.key);
        test.waitTicks(10);
        check(test.computeOnClient(mc -> ViewControls.isZoomActive() && sceneFov(mc) < 50), "Zoom key did not reduce rendered FOV");
        test.takeScreenshot("luxis-zoom");
        input.releaseKey(zoom.key);
        test.waitTicks(10);
        check(!test.computeOnClient(mc -> ViewControls.isZoomActive()), "Zoom Hold did not release");
        test.runOnClient(mc -> zoom.trigger = Module.Trigger.TOGGLE);
        input.pressKey(zoom.key);
        test.waitTick();
        check(test.computeOnClient(mc -> ViewControls.isZoomActive()), "Zoom Toggle did not stay on");
        input.pressKey(zoom.key);
        test.waitTicks(10);
        check(!test.computeOnClient(mc -> ViewControls.isZoomActive()), "Zoom Toggle did not switch off");
        input.holdKey(freelook.key);
        test.waitTick();
        check(test.computeOnClient(mc -> ViewControls.isFreelookActive()), "Freelook Hold did not activate");
        float before = test.computeOnClient(mc -> mc.player.getYRot());
        boolean moved = test.computeOnClient(mc -> ViewControls.move(35, 0));
        test.waitTicks(4);
        float after = test.computeOnClient(mc -> mc.player.getYRot());
        float cameraYaw = test.computeOnClient(mc -> ViewControls.yaw());
        check(moved && after == before && Math.abs(cameraYaw - before) > .1F,
                "Freelook did not keep the player still and rotate only the camera: player " + before + " -> " + after
                        + ", camera " + cameraYaw);
        input.releaseKey(freelook.key);
        input.holdKey(hold);
        test.waitTick();
        check(test.computeOnClient(mc -> hud.enabled), "Hold did not enable another module");
        input.releaseKey(hold);
        test.waitTick();
        check(!test.computeOnClient(mc -> hud.enabled), "Hold left another module enabled");
    }

    private static Camera camera(Minecraft mc) {
        try {
            var renderer = mc.gameRenderer.getClass();
            try { return (Camera) renderer.getMethod("mainCamera").invoke(mc.gameRenderer); }
            catch (NoSuchMethodException e) { return (Camera) renderer.getMethod("getMainCamera").invoke(mc.gameRenderer); }
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    private static float sceneFov(Minecraft mc) {
        try {
            var camera = camera(mc);
            try { return (float) Camera.class.getMethod("getFov").invoke(camera); }
            catch (NoSuchMethodException e) {
                var method = mc.gameRenderer.getClass().getDeclaredMethod("getFov", Camera.class, float.class, boolean.class);
                method.setAccessible(true);
                return (float) method.invoke(mc.gameRenderer, camera, 1F, true);
            }
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    private static void checkCatalog() {
        var ids = new HashSet<String>();
        for (Module m : ModuleRegistry.all()) check(ids.add(m.id), "Duplicate module " + m.id);
        var visuals = new HashSet<Module>(VisualModules.modules());
        visuals.add(ModuleRegistry.byId("quietsaveicon"));
        visuals.add(ModuleRegistry.byId("chatlayout"));
        check(visuals.size() == 20 && visuals.stream().allMatch(m -> m != null && !m.enabled && m.category == Module.Category.VISUAL),
                "Expected twenty opt-in visual modules");
        var bytes = ByteBuffer.allocate(VisualModules.UNIFORM_BYTES);
        VisualModules.writeUniform(bytes);
        check(bytes.position() == VisualModules.UNIFORM_BYTES, "Shader uniform does not match Java layout");
        check(VisualModules.GRADE.settings.size() == 14, "Color Grading settings are missing");
        for (var palette : GuiTheme.ALL) {
            var rich = palette.paint();
            GuiConfig.appearance().colorDepth = GuiConfig.ColorDepth.Subtle;
            check(palette.paint().window() != rich.window() && palette.paint().card() != rich.card(), "Theme intensity is not applied");
            GuiConfig.appearance().colorDepth = GuiConfig.ColorDepth.Rich;
        }
    }

    private static double chroma(BufferedImage image) {
        return mean(image, .28, .68, .37, .58, rgb -> {
            int r = rgb >> 16 & 255, g = rgb >> 8 & 255, b = rgb & 255;
            return Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b));
        });
    }

    private static double greenBias(BufferedImage image) {
        return mean(image, .14, .85, .33, .72, rgb -> (rgb >> 8 & 255) - (rgb >> 16 & 255));
    }

    private static double light(BufferedImage image) {
        return mean(image, .28, .68, .37, .58, rgb -> ((rgb >> 16 & 255) + (rgb >> 8 & 255) + (rgb & 255)) / 3);
    }

    private static double mean(BufferedImage image, double x0, double x1, double y0, double y1,
                               java.util.function.IntUnaryOperator channel) {
        long sum = 0, count = 0;
        for (int y = (int) (image.getHeight() * y0); y < image.getHeight() * y1; y++) {
            for (int x = (int) (image.getWidth() * x0); x < image.getWidth() * x1; x++) {
                sum += channel.applyAsInt(image.getRGB(x, y));
                count++;
            }
        }
        return (double) sum / count;
    }

    private static void check(boolean condition, String reason) {
        if (!condition) throw new AssertionError(reason);
    }
}
