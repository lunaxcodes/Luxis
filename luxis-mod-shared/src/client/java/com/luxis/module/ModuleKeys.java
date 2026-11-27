package com.luxis.module;

import com.luxis.client.LuxisClientMod;
import com.luxis.module.impl.ClientTuning;
import com.luxis.module.impl.ViewControls;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.player.LocalPlayer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Toggle writes to the profile; held changes are temporary and cleared on focus loss. */
public final class ModuleKeys {
    private static final Set<InputConstants.Key> DOWN = new HashSet<>();
    private static final Map<Module, InputConstants.Key> HELD = new HashMap<>();
    private ModuleKeys() {}

    public static void handle(InputConstants.Key key, int action, LocalPlayer player, boolean inGame) {
        if (action == 0) {
            if (!DOWN.remove(key)) return;
            HELD.entrySet().removeIf(entry -> {
                if (!entry.getValue().equals(key)) return false;
                setEnabled(entry.getKey(), false);
                return true;
            });
            for (Module m : ModuleRegistry.all()) {
                if (ViewControls.isViewKey(m) && key.equals(m.key)) ViewControls.key(m, false, 0, 0);
            }
            return;
        }
        if (action != 1 || !inGame || player == null || key.equals(InputConstants.UNKNOWN) || !DOWN.add(key)) return;
        boolean save = false;
        for (Module m : ModuleRegistry.all()) {
            if (!key.equals(m.key)) continue;
            if (ViewControls.isViewKey(m)) ViewControls.key(m, true, player.getYRot(), player.getXRot());
            else if (m.trigger == Module.Trigger.TOGGLE) { setEnabled(m, !m.enabled); save = true; }
            else if (!m.enabled) { HELD.put(m, key); setEnabled(m, true); }
        }
        if (save) LuxisClientMod.save();
    }

    public static void reset() {
        DOWN.clear();
        HELD.keySet().forEach(m -> setEnabled(m, false));
        HELD.clear();
        ViewControls.reset();
    }
    public static boolean persistentEnabled(Module module) { return module.enabled && !HELD.containsKey(module); }

    public static void setEnabled(Module m, boolean enabled) {
        m.enabled = enabled;
        if (enabled) m.onEnable(); else m.onDisable();
        ClientTuning.changed();
    }
}
