# Luxis for macOS

This folder contains the native macOS port of Luxis. It reuses the existing HTML/JS interface and most C# services.

## Install

1. Download the build for your Mac:
   - Apple Silicon (M1/M2/M3/M4): `Luxis-Mac-arm64.app.zip`
   - Intel: `Luxis-Mac-x64.app.zip`
   - Not sure? Apple menu → About This Mac. Apple Silicon shows a chip name; Intel shows an Intel processor.
2. Double-click the `.zip` to extract `Luxis.app`.
3. Drag `Luxis.app` into your `Applications` folder.
4. First launch: **right-click `Luxis.app` → Open → Open**. macOS may also ask you to approve it under System Settings → Privacy & Security.
5. If macOS blocks it, open Terminal and run:
   ```bash
   xattr -cr /Applications/Luxis.app
   ```
   then launch it again.

After the first install, use **Updates** inside Luxis for future versions. The app picks the correct Apple Silicon or Intel manifest automatically.

## Build

From the workspace root on Windows or macOS:

```powershell
powershell -ExecutionPolicy Bypass -File .\mac\build-mac.ps1
```

Outputs in `dist/mac-release`:

- `Luxis-arm64.app.zip` — Apple Silicon
- `Luxis-x64.app.zip` — Intel
- `Luxis-Mac-arm64`, `Luxis-Mac-x64` — raw executables used by the updater
- `version-mac-arm64.json`, `version-mac-x64.json` — update manifests

## How it works

- Native shell: **Photino.NET** (`WKWebView` on macOS, WebView2 on Windows for testing)
- The UI is served from a loopback-only HTTP server so the synchronous `native()` bridge keeps working
- A generated JavaScript shim exposes all bridge methods on `window.__luxis` / `window.__luxisSync`
- Refresh tokens are stored in the macOS Keychain instead of DPAPI
- Data lives in `~/Library/Application Support/Luxis`
- Java detection includes macOS JVM layouts and Adoptium `.tar.gz` runtimes

Bedrock-specific controls are disabled on macOS because Minecraft Bedrock and Windows Update do not exist there.

## Known limitations

- The first release is ad-hoc signed only. macOS may require **Right-click → Open** (or System Settings → Privacy & Security → Open Anyway) on the first launch.
- Minecraft Java launch on macOS uses the same version/asset pipeline; platform-specific tweaks may still be needed for some launcher configurations.
- A `.dmg` needs `hdiutil` on a Mac; the Windows build produces `.app.zip` bundles.
