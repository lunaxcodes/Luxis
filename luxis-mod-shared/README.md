# Luxis Mod 1.5.1 — Camera, keybinds, and HUD

## Install

Close Minecraft and replace only the old Luxis Mod JAR in your profile's `mods` folder with the
1.5.1 JAR that matches your Minecraft version (1.21.11 or 26.3). Keep the matching Fabric API
installed and leave `config/luxis` in place to keep your preferences and profiles.

## Camera and HUD

- **Zoom:** hold **C** by default, and scroll while zoomed to adjust magnification.
- **Freelook:** enable the module, then hold **V** by default. It moves the camera without turning
  your player. Use it and Fullbright only where the server allows them.
- **Fullbright:** enable it under Visual and adjust **Brightness**. It brightens the local world
  lightmap without changing saved Minecraft brightness or world light levels.
- **Keybind mode:** click a module card and choose **Hold** or **Toggle**. For Zoom and Freelook,
  the module switch arms the feature and the key controls the camera. For other modules, Hold
  temporarily enables the module and Toggle changes its saved enabled state.
- **HUD:** create a **Custom label**, choose **Theme**, **Module**, or **Custom** text color, and
  adjust the panel opacity or outline from each widget's options. New HUD defaults use a themed
  background and border; existing positions and panel preferences are retained.

## Themes

In game, press **Right Shift** and open **Settings → Themes**. The palette now colors the window,
cards, tabs, controls, option dialogs, Waypoints, and the HUD editor. Use **Settings → Look → Theme
intensity** to switch between Subtle, Balanced, and Rich. Existing profiles and theme names are kept.

## What's new

All 29 new modules start off. Click a module card to open its options, or click its switch to
enable it. PvP readouts are display-only and never press keys or send extra packets.

| Category | Modules |
| --- | --- |
| Visual · World | **Color Grading**, Lens Vignette, Film Grain, Palette Steps, Color Fringe, Pixel Lens, Clarity, Highlight Bloom |
| Visual · Framing | Letterbox, Composition Grid, Safe Area, Camera Level |
| Visual · Interface | Clean Boss Bar, Clean Scoreboard, Quiet Titles, Quiet Action Bar, Quiet Held Item, Quiet Potion Icons, Quiet Autosave Icon, Chat Layout |
| HUD | World Clock, Current Biome, Inventory Space |
| Performance | Biome Blend Budget, Cloud Distance, Menu Blur Budget |
| PvP | Bow Draw, XP Bottle Stock, Shield Durability |

**Color Grading:** Open **Mods → Visual → Color Grading**. Choose a base look (Neutral, Cinema,
Vintage, Black & White, or Cool) and tune blend, brightness, saturation, contrast, exposure, gamma,
warmth, hue, green/magenta tint, shadow lift, and RGB gains. The effect changes the world image,
not the Luxis menu or Minecraft HUD. Settings are saved to your active Luxis mod profile.

The eight world effects share one post-process chain. They're skipped with Iris to avoid
conflicting with shader packs. Screen-space framing guides and interface modules do not need it.

## Build and verify

Build the 1.21.11 and 26.3 projects separately with their Gradle wrappers. In either project,
run the opt-in client smoke test with:

```powershell
.\gradlew.bat runClientGameTest -PluxisSmoke --no-configuration-cache
```

This starts a local test client with a disposable singleplayer world under `build/run/clientGameTest`.
It checks the module catalog and uses in-game screenshots to verify desaturation and theme recoloring.
