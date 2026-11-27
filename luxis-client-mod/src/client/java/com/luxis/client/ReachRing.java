package com.luxis.client;

import com.luxis.gui.GuiTheme;
import com.luxis.module.impl.ClientTuning;
import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;

public final class ReachRing {
    private ReachRing() {}

    public static void draw(Minecraft mc) {
        var player = mc.player;
        if (player == null || player.isSpectator() || mc.gui.screen() != null) return;
        float tick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        var feet = player.getPosition(tick).add(0, 0.035, 0);
        float range = (float) (ClientTuning.blockRadius() ? player.blockInteractionRange() : player.entityInteractionRange());
        int color = (Math.round(255 * ClientTuning.radiusOpacity()) << 24) | (GuiTheme.current().accent & 0xFFFFFF);
        if (range > 0 && Float.isFinite(range)) Gizmos.circle(feet, range, GizmoStyle.stroke(color, 2F));
    }
}
