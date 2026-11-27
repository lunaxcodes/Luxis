<#
  Builds self-contained macOS app bundles and per-architecture update manifests.
  Run from the workspace root: powershell -ExecutionPolicy Bypass -File .\mac\build-mac.ps1
#>
$ErrorActionPreference = 'Stop'
$version = (Get-Content 'version.txt' -Raw).Trim()
$out = 'dist/mac-release'
New-Item -ItemType Directory -Force -Path $out | Out-Null

foreach ($rid in @('osx-arm64', 'osx-x64')) {
  $publish = "dist/Luxis-Mac-$rid"
  dotnet publish 'mac/Luxis.Mac.csproj' -c Release -r $rid --self-contained true `
    -p:PublishSingleFile=true -p:IncludeNativeLibrariesForSelfExtract=true -p:DebugType=none `
    -o $publish -v quiet -clp:ErrorsOnly
  if ($LASTEXITCODE -ne 0) { throw "Publish failed for $rid" }

  $binary = Join-Path $publish 'Luxis'
  $arch = if ($rid -eq 'osx-arm64') { 'arm64' } else { 'x64' }
  $app = Join-Path $out "Luxis-$arch.app"
  $macos = Join-Path $app 'Contents/MacOS'
  New-Item -ItemType Directory -Force -Path $macos | Out-Null
  Copy-Item -LiteralPath $binary -Destination (Join-Path $macos 'Luxis') -Force
  @"
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0"><dict>
  <key>CFBundleName</key><string>Luxis</string>
  <key>CFBundleDisplayName</key><string>Luxis</string>
  <key>CFBundleIdentifier</key><string>com.lunaxcodes.luxis</string>
  <key>CFBundleExecutable</key><string>Luxis</string>
  <key>CFBundlePackageType</key><string>APPL</string>
  <key>CFBundleShortVersionString</key><string>$version</string>
  <key>CFBundleVersion</key><string>$version</string>
  <key>LSMinimumSystemVersion</key><string>11.0</string>
  <key>NSHighResolutionCapable</key><true/>
</dict></plist>
"@ | Set-Content -Encoding utf8 (Join-Path $app 'Contents/Info.plist')

  $zip = Join-Path $out "Luxis-Mac-$arch.app.zip"
  if (Test-Path -LiteralPath $zip) { Remove-Item -LiteralPath $zip }
  Compress-Archive -Path $app -DestinationPath $zip

  $target = Join-Path $out "Luxis-Mac-$arch"
  Copy-Item -LiteralPath $binary -Destination $target -Force
  $hash = (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash.ToLowerInvariant()
  $manifest = [ordered]@{
    version = $version
    executable = 'Luxis'
    url = "https://github.com/lunaxcodes/Luxis/releases/latest/download/Luxis-Mac-$arch"
    sha256 = $hash
    size = (Get-Item -LiteralPath $target).Length
    released = (Get-Date).ToUniversalTime().ToString('o')
  } | ConvertTo-Json -Depth 3
  [IO.File]::WriteAllText((Join-Path $out "version-mac-$arch.json"), $manifest)
  Write-Host "Built $arch app bundle and manifest."
}
Write-Host "macOS release staged in $out"
