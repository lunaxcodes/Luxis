# Luxis — Minecraft Launcher

A real Windows launcher: a self-contained native desktop app with a live Modrinth browser, real Microsoft login, and a working Minecraft launch pipeline.

## Run

Double-click **`Luxis.exe`** or the **Luxis** shortcut on your Desktop (created with the Luxis icon). No install, Node, Python, or .NET runtime is needed (the app is self-contained). Requirements: Windows 10/11 with the WebView2 runtime, which ships with current Microsoft Edge.

The window is frameless: drag the top bar to move it, and use `─ □ ×` in the top-right corner. Data lives in `%LOCALAPPDATA%\Luxis`:

| Path | Contents |
|---|---|
| `accounts.json` | Account metadata with DPAPI-encrypted refresh tokens |
| `settings.json` | Microsoft client-ID override and active account |
| `instances\<profile>\` | Real `mods/`, `resourcepacks/`, `shaderpacks/`, `config/`, `saves/`, `natives/`, `logs/` |
| `game\` | Cached Mojang versions, assets and libraries (SHA-1 verified) |
| `WebView2\` | Browser profile containing all UI state |

## What is real now

- **Microsoft sign-in** — device-code flow against Microsoft's v2 endpoint, Xbox Live and XSTS authentication, Minecraft token exchange, entitlement and profile lookup. Tokens never reach the UI and are encrypted at rest with DPAPI. Add your own Azure client ID in *Settings & Themes → Microsoft client ID* if you do not want to use the bundled public one.
- **Minecraft launch** — downloads Mojang's version manifest, version JSON, assets and libraries with hash verification, resolves Fabric/Quilt loader profiles (including Maven-style libraries), picks the right Java runtime (Java 8/17/21/25 detected locally), generates the classpath and game arguments, then starts the JVM and streams its log to the launcher. Fourteen-stage progress is pushed to the launch dock.
- **Modrinth Hub** — live `api.modrinth.com/v2` search with category/loader/version facets, debounced search bar, pagination, version history, and one-click install into the selected profile. Required dependencies are resolved automatically; shader installs can pull in Iris. Files are SHA-512 verified.
- **Minecraft instance art** — profile icons and banners use genuine block textures extracted from the cached Minecraft client JAR (`diamond_block`, `netherite_block`, `amethyst_block`, etc.) with a dark overlay, instead of emoji and flat gradients.
- **Wardrobe** — a skin library grid with large character cards, search, and an `Add Skin` tile; a compact side panel keeps the front/back preview, capes, model type and Mojang import. Cards use authentic 64×64 UV mapping, and signed-in accounts can fetch real Mojang skin/cape textures.
- **Profile Settings** — the gear button next to Play opens a tabbed settings modal (General, Installation, Java & Memory, Window, NRC, Symlinks) with editable name/group, preferred account, Quick Play path, RAM/JVM and window options.
- **Launch diagnostics** — a `!` button on the instance page, and an automatic panel when the game exits early, reads `latest.log` and the newest crash report and explains the likely cause with a fix you can act on.
- **Instance detail view** — opening a profile shows a full instance page: header and chips, last-played/playtime/disk stats, mod search and grid with toggles, a Content/World sidebar with real folder shortcuts, and a direct Play button.
- **Interface customization** — large icon-only sidebar with hover tooltips, ambient particle density, glow intensity, sidebar icon size/height, tooltip toggle, six theme presets and a custom theme studio.
- **Mod installation** — `Add Mods` opens a native file picker that copies selected `.jar` files into the profile's real `mods/` folder. Dropping files on the instance page also works where WebView2 exposes file paths; otherwise Luxis falls back to the picker.
- **Launch feedback** — a full-screen animated launch overlay with progress ring, stage checklist and visible errors, in addition to the dock progress bar. Quick Play flags from modern version manifests are correctly filtered, so 26.x/Fabric instances no longer crash with "Only one quick play option can be specified". If the game exits early, the launcher shows the exit code and the last log lines.
- **Native folder actions** — instance directory buttons open real Explorer windows.
- **Java auto-detection & installer** — finds official Minecraft launcher runtimes plus common vendor JDKs, with a one-click Temurin download if none is found. Fixes Java 21 installs on other machines.
- **Config backups** — every launch backs up `config/` and `options.txt`; each instance page has a Restore button.
- **Updates tab** — a dedicated sidebar page checks GitHub releases on open and on startup, verifies SHA-256, and replaces the exe on restart.
- **Skins & capes** — accurate 64×64 front/back character rendering (including slim arms), real cape artwork in the library, and equip buttons for capes your Microsoft account actually owns. Preset skins are no longer added automatically.
- **Luxis Mod** — the in-game Fabric mod menu is bundled for both Minecraft 1.21.x and 26.x. Add it from the Luxis Mod sidebar section with one click; the launcher installs the matching jar and requires Fabric API. It includes themed rounded UI, 31 fair-play modules, per-module settings, profiles and a free-form HUD layout editor.
- **Profile controls** — delete a profile from its settings (with confirmation), and pick from 115 real Minecraft block textures for its icon.
- **Launch & stop** — the Launch button turns into Stop while the game runs, and the launch overlay closes automatically at 100%.
- **Modrinth matching** — shaders and resource packs fall back from exact version to major version to any version, so packs that omit loader metadata still install.
- **Custom mod icons & Update all** — locally added jars are matched to Modrinth projects for their real icons, and Update all detects outdated mods even when they were not installed through Luxis.

## About the Microsoft sign-in page

The bundled public client ID belongs to Espectral Client (a personal-Microsoft-account app with device-code sign-in enabled), so Microsoft's approval page may show that name. It is not a secret and Luxis does not use anyone else's code. To see your own app name, register a free Azure application (enable "Allow public client flows" / device-code flow) and paste its Application (client) ID into *Settings & Themes → Microsoft client ID* or directly in the sign-in modal. Luxis stores it in local app data.

**Easiest setup:** run `powershell -ExecutionPolicy Bypass -File .\setup-azure.ps1`. It opens the Azure portal in your browser, walks you through creating the `Luxis` app with the right account types and redirect URI, then takes the client ID you paste and writes it (plus the matching audience) straight into Luxis. Personal Microsoft accounts work in the portal — Azure PowerShell is not used because it requires a work/school account.

If Microsoft says the account "does not exist in tenant 'Microsoft Services'" (AADSTS50020), your Azure app was created with "Accounts in this organizational directory only". In Azure → App registration → **Supported account types**, change it to **Personal Microsoft accounts only** (or "any organizational directory and personal"), and make sure Luxis's **Sign-in audience** setting matches (`consumers` for personal, `common` for both).

**Recommended:** click **"Use my own Luxis app"** in the sign-in modal. It walks you through creating a free Azure app registration named Luxis with the correct personal-account and redirect settings. Paste its client ID into Luxis and the Microsoft approval page will say "Luxis" — this also avoids relying on a borrowed public ID.

Two sign-in methods are available: the device code, or **Browser sign-in** (authorization code + PKCE) — approve in the browser, then paste the redirect URL back into Luxis. If a device code expires, click **Get a new code** (it auto-retries). The code box is click-to-copy and you can paste a different client ID directly in the login modal. A successful sign-in adds your real Minecraft username/UUID to the account switcher and keeps it after a restart.

## Browser-only fallback

`index.html` also opens directly in a browser. In that mode, Modrinth results fall back to a curated offline catalog and login/launch are simulated, so the UI can be explored without the exe.

## Files

| File | Purpose |
|---|---|
| `Luxis.exe` | Self-contained Windows launcher (single executable, ~112 MB) |
| `index.html` | Complete UI (HTML + CSS + vanilla JS), embedded into the exe at build time |
| `src/Program.cs` | Native shell, WebView2 host bridge, window controls, event streaming |
| `src/AuthService.cs` | Microsoft/Xbox/Minecraft authentication and secure token storage |
| `src/MinecraftService.cs` | Version/asset/library pipeline, Java detection, JVM launch |
| `src/ModrinthService.cs` | Live Modrinth v2 client, install and dependency resolution |
| `src/Util.cs` | HTTP, hashing, verification and JSON helpers |
| `src/Luxis.csproj` | .NET 10 project (WebView2 NuGet package) |
| `logo.png`, `app.ico`, `make-logo.ps1`, `make-ico.ps1` | Branding, logo and multi-resolution Windows icon generators |
| `ARCHITECTURE.md` | Technical specification and prototype-to-production mapping |

## Public release / installer

Run `powershell -ExecutionPolicy Bypass -File .\build-release.ps1` to build a clean, versioned release. It produces:

| Output | Purpose |
|---|---|
| `installer\Luxis-Setup-X.Y.Z.exe` | Public Inno Setup installer — installs only the executable, icon, README and version file into `%LOCALAPPDATA%\Luxis`. No accounts, skins, mods or settings are bundled. |
| `release\Luxis.exe` | Portable copy of the same build |
| `release\version.json` | Update manifest with version, SHA-256 and size |

Bump `version.txt` before building a new release — the exe, installer filename/version and manifest all use that single number.

Auto-updates are enabled by default: on startup Luxis checks the latest GitHub release's `version.json`, compares versions, and offers **Download & install** in the dedicated **Updates** tab. It verifies SHA-256 and replaces the running exe on restart via a short batch script. You can point it at a different manifest URL in Updates if you self-host releases. New manifests include an explicit `url` for the exe; older ones fall back to `Luxis.exe` beside the manifest.

## Rebuild

```
dotnet publish src\Luxis.csproj -c Release -r win-x64 --self-contained true -p:PublishSingleFile=true -p:IncludeNativeLibrariesForSelfExtract=true -p:DebugType=none -o publish
copy publish\Luxis.exe Luxis.exe
```

The UI is embedded as a resource, so any change to `index.html` requires a rebuild.
