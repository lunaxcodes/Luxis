# Luxis Client Mod

Fabric client mod for **Minecraft Java 26.3**, built with **Java 25**, **Fabric Loader 0.19.5** and **Fabric API 0.167.0+26.3**.

Windowed mod menu (not a ClickGUI), frame-rate independent animations, live HUD layout editor, per-module settings, profiles and fair-play HUD / performance / visual modules. No cheats, auto-clickers, hitboxes or unfair combat automation.

## Build

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot'
.\gradlew.bat clean build
```

Output: `build\libs\luxis-client-mod-1.5.1.jar`

## In-game UI

Press **Right Shift** to open the menu. The menu and HUD editor use a high-resolution Lato font
and a shared, antialiased glass texture. The UI animates on each render frame and respects the
game’s existing frame-rate and VSync settings. It does not apply a full-screen blur.

Both builds read their font and glass assets from `../luxis-ui-resources/`. To regenerate the
texture after editing its design, run `python tools/generate-luxis-ui.py` from the workspace root.

See [mod features and tests](../luxis-mod-shared/README.md) for the controls and module list.

### Motion update

- **Settings → Look:** set window width and height separately. **Motion** has Glide, Zoom, and Fade
  transitions; animation speed; and optional Drift, Stars, or Comets with count, speed, and opacity.
- **Performance:** Crystal Optimizer, FPS Booster, PvP Optimizer, Explosion Budget, Cloud Budget,
  Weather Budget, Particle Budget, Chunk Scheduling, Entity Draw Budget, Terrain Budget, and
  Background FPS all change client rendering. They start off. Entity/terrain distance caps can
  shorten visibility; no module changes hit registration or server behavior. Frame Time and Memory are HUD modules.
- **Visual:** adjust the hand viewmodel, directional camera-turn blur, reach ring, damage tilt,
  dynamic FOV, glint, screen effects, and block outline contrast. Blur costs two post-process
  passes while turning and is skipped with Iris. Radius illustrates your current vanilla range;
  it does not increase reach or guarantee a clear hit path.

### PvP update

15 new **PvP** HUD readouts: Attack Timing, Shield Status, Pearl Status, Totem Stock, Crystal Stock,
Anchor Supplies, Golden Apple Stock, Arrow Stock, Potion Stock, Armor Warning, Held Durability,
Combat Health, Target Distance, PvP Ping, and Last Damage. All use client-observed state only:
no auto-clicking, no input automation, no extra packets, and no change to reach or combat rules.

### Workbench update

- **Mods:** pin favorites, filter enabled modules, and press **Ctrl+F** to search. The new Frame Time,
  Memory Monitor, Local Clock, Compass, Block Inspector, Chunk Position, Movement Speed, and XP Progress
  overlays are opt-in. Frame Time reports average and peak time between frames, not GPU render time.
- **Settings:** pick from 16 themes, adjust the window, cards, opacity, corners and motion, or save
  your look in one of three layout slots. Use **Files** for locations and targeted resets.
- **Waypoints:** save and copy coordinate notes for the current world or server. Notes are local and
  don’t render in the world or track anything.
- **HUD Layout:** click a widget and use the arrows to nudge it, **Shift+arrows** for larger steps,
  or **Ctrl+Z** to undo. Snap and guide settings are remembered.

GUI settings live in `config/luxis/gui/` (`appearance.json`, `browser.json`, `presets/`).
Waypoints live in `config/luxis/waypoints.json`. Module settings and HUD positions remain in
`config/luxis/profiles/`. The old theme selection is migrated without deleting the legacy file.

## Directory tree

```text
luxis-client-mod/
├── build.gradle
├── gradle.properties
├── settings.gradle
├── gradlew / gradlew.bat
├── src/
│   ├── main/
│   │   └── resources/
│   │       ├── fabric.mod.json
│   │       └── luxis.client.mixins.json
│   └── client/
│       └── java/com/luxis/
│           ├── client/
│           │   └── LuxisClientMod.java
│           ├── config/
│           │   ├── Profile.java
│           │   └── ProfileManager.java
│           ├── gui/
│           │   ├── AnimationUtil.java
│           │   ├── ClickRegion.java
│           │   ├── HudEditorScreen.java
│           │   ├── LuxisModScreen.java
│           │   └── ModuleOptionsOverlay.java
│           ├── mixin/
│           │   ├── MouseHandlerMixin.java
│           │   ├── ParticleEngineMixin.java
│           │   └── EntityRenderDispatcherMixin.java
│           └── module/
│               ├── Module.java
│               ├── HudModule.java
│               ├── ModuleRegistry.java
│               ├── Setting.java
│               └── impl/
│                   ├── FpsHudModule.java
│                   ├── CpsHudModule.java
│                   ├── ToggleSprintSneakModule.java
│                   ├── ArmorStatusModule.java
│                   ├── PotionHudModule.java
│                   ├── CoordinatesPingModule.java
│                   ├── KeystrokesModule.java
│                   ├── ParticleLimiterModule.java
│                   ├── EntityRenderDistanceModule.java
│                   ├── UnfocusedFpsLimiterModule.java
│                   ├── FogAndWeatherOptimizerModule.java
│                   ├── ZoomModule.java
│                   ├── LowFireModule.java
│                   ├── BlockOutlineModule.java
│                   ├── HitColorModule.java
│                   └── CrosshairModule.java
```

## Implementation notes

- Screens use `Minecraft.getInstance().gui.setScreen(...)`.
- Rendering uses `GuiGraphicsExtractor` (the 26.3 replacement for legacy `GuiGraphics`) via `HudElementRegistry`, with no raw OpenGL calls.
- Every module owns typed `Setting` objects (toggle / slider / mode / color) and stores keybind + trigger mode.
- Profiles persist as Gson JSON in `config/luxis/profiles/<profile>.json`; active profile is in `config/luxis/active.json`.
- `RIGHT SHIFT` opens the windowed menu; the HUD editor reuses the same coordinate store with drag, resize, snap and guides.
- Mixins only count mouse input (CPS), cap particle spawns, and skip distant passive entity rendering — no gameplay logic is changed.

A standalone interactive UI prototype is also available at `../luxis-mod-prototype.html`.
