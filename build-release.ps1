<#
  Builds a clean, versioned public release:
    - publish\Lunax.exe               (self-contained app)
    - release\Lunax.exe               (same exe, for portable use / updater)
    - release\version.json            (update manifest with SHA-256)
    - installer\Lunax-Setup-X.Y.Z.exe (Inno Setup installer)
  No local game data, accounts or settings are ever included.
#>
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

$version = (Get-Content 'version.txt' -Raw).Trim()
Write-Host "== Building Lunax $version ==" -ForegroundColor Cyan

# 1. Publish a fresh self-contained executable.
dotnet publish src\Lunax.csproj -c Release -r win-x64 --self-contained true `
  -p:PublishSingleFile=true -p:IncludeNativeLibrariesForSelfExtract=true `
  -p:DebugType=none -o publish -v quiet -clp:ErrorsOnly

New-Item -ItemType Directory -Force -Path 'release','installer' | Out-Null
Copy-Item 'publish\Lunax.exe' 'Lunax.exe' -Force
Copy-Item 'publish\Lunax.exe' 'release\Lunax.exe' -Force
Copy-Item 'version.txt' 'release\version.txt' -Force

# 2. Update manifest used by the in-app updater.
$hash = (Get-FileHash 'release\Lunax.exe' -Algorithm SHA256).Hash.ToLowerInvariant()
$size = (Get-Item 'release\Lunax.exe').Length
$manifest = [ordered]@{
  version    = $version
  executable = 'Lunax.exe'
  url        = 'https://github.com/lunaxcodes/Lunax/releases/latest/download/Lunax.exe'
  sha256     = $hash
  size       = $size
  released   = (Get-Date).ToUniversalTime().ToString('o')
} | ConvertTo-Json -Depth 3
[System.IO.File]::WriteAllText((Join-Path (Resolve-Path 'release').Path 'version.json'), $manifest)

# 3. Compile the installer with the same version number.
$isc = @(
  'C:\Program\ISCC.exe',
  'C:\Program Files (x86)\Inno Setup 6\ISCC.exe',
  'C:\Program Files\Inno Setup 6\ISCC.exe'
) | Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $isc) { throw 'Inno Setup compiler not found. Install Inno Setup 6.' }
& $isc "/DMyAppVersion=$version" 'installer.iss' | Out-Host
if ($LASTEXITCODE -ne 0) { throw "Inno Setup failed with exit code $LASTEXITCODE" }

Write-Host ''
Write-Host 'DONE.' -ForegroundColor Green
Write-Host "Exe:       $((Resolve-Path 'Lunax.exe').Path)"
Write-Host "Manifest:  $((Resolve-Path 'release\version.json').Path)"
Write-Host "Installer: $((Resolve-Path "installer\Lunax-Setup-$version.exe").Path)"
