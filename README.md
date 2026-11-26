# Lunax

A next-generation, multi-themed Minecraft: Java Edition launcher for Windows. Obsidian-glass UI with glowing orchid accents, Fabric-first modloader support, a live Modrinth browser, a skin & cape wardrobe, multi-account management, isolated instances, and a real launch pipeline.

> Lunax is an independent project. It is not affiliated with Mojang, Microsoft, Prism Launcher, or Modrinth.

## Download

Grab the latest installer from the [Releases page](https://github.com/lunaxcodes/Lunax/releases). The current release is **v1.1.0**. The installer is unsigned, so Windows SmartScreen may warn on first run.

## Features

- **Microsoft sign-in** — device-code or browser (PKCE) sign-in, Xbox Live/XSTS token exchange, DPAPI-encrypted token storage. Tokens never reach the UI.
- **Real launching** — downloads and verifies Mojang versions, assets, libraries and natives; merges Fabric/Quilt loader profiles; detects Java 8/17/21/25; streams launch progress and logs.
- **Modrinth Hub** — live v2 search for mods, resource packs and shaders with facets, pagination, version history, dependency resolution, SHA-512 verified installs, and Iris prompts.
- **Modpacks** — imports real `.mrpack` archives with hash-verified downloads and `overrides/`.
- **Wardrobe** — 2D Minecraft skin/cape preview with layer toggles, local uploads, Mojang texture import and slim/classic models.
- **Instances** — isolated profiles with mod management, drag-and-drop `.jar` installation, RAM/JVM/window settings, and launch diagnostics.
- **Edition switch** — choose Java or Bedrock next to the logo. Bedrock detection and launcher hand-off for Minecraft for Windows.
- **Theming & updates** — six presets, custom accents, particles, large icon navigation, and a SHA-256-verified self-updater.
- **v1.1 fixes** — mod installs target the profile you're viewing, popups stay open when clicked, real `.mrpack` import, launch diagnostics, and stable popups/modals.

## Build from source

Requirements: Windows, .NET 10 SDK, and Inno Setup 6 (only for the installer).

```powershell
# Build the self-contained executable and the installer in one step
.\build-release.ps1
```

Outputs: `publish/Lunax.exe`, `installer/Lunax-Setup-X.Y.Z.exe`, `release/version.json`.

To build just the app:

```powershell
dotnet publish src\Lunax.csproj -c Release -r win-x64 --self-contained true -p:PublishSingleFile=true -p:IncludeNativeLibrariesForSelfExtract=true -p:DebugType=none -o publish
```

## Releasing

1. Bump `version.txt`.
2. Run `build-release.ps1` — the exe, installer name/version and update manifest all use that number.
3. Upload `installer/Lunax-Setup-X.Y.Z.exe` to a GitHub Release (the CI workflow does this automatically when you push a `v*` tag).
4. Host `release/version.json` and `release/Lunax.exe` at a public URL and paste that manifest URL into Lunax → Settings → Updates.

## Privacy

Lunax stores accounts, tokens, instances, skins and settings **locally** under `%LOCALAPPDATA%\Lunax`. It ships no user data. Tokens are encrypted with Windows DPAPI. Telemetry: none.

## License

MIT — see [LICENSE](LICENSE).
