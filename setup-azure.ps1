<#
  Luxis Microsoft sign-in setup (browser version)
  Azure PowerShell refuses personal Microsoft accounts, so this script uses
  the Azure portal in your browser instead. It then writes the client ID and
  audience into Luxis's settings for you.
#>
$ErrorActionPreference = 'Stop'
$SettingsPath = Join-Path $env:LOCALAPPDATA 'Luxis\settings.json'
$PortalUrl = 'https://portal.azure.com/#view/Microsoft_AAD_RegisteredApps/ApplicationsListBlade'

Write-Host '== Luxis Azure sign-in setup ==' -ForegroundColor Cyan
Write-Host ''
Write-Host 'A browser will open the Azure portal. Sign in with your PERSONAL Microsoft account.' -ForegroundColor Yellow
Write-Host ''
Write-Host 'In the portal:' -ForegroundColor Cyan
Write-Host ' 1. Click "New registration".'
Write-Host ' 2. Name: Luxis'
Write-Host ' 3. Supported account types: choose "Personal Microsoft accounts only"'
Write-Host '    (or "Accounts in any organizational directory and personal Microsoft accounts").'
Write-Host '    Do NOT leave it on "Accounts in this organizational directory only" - that is'
Write-Host '    what caused the "account does not exist in tenant" error.'
Write-Host ' 4. Redirect URI: Public client/native = https://login.microsoftonline.com/common/oauth2/nativeclient'
Write-Host ' 5. Register, then Authentication -> Advanced settings -> Allow public client flows = Yes.'
Write-Host ' 6. On the Overview page, copy the Application (client) ID.'
Write-Host ''
Start-Process $PortalUrl

$clientId = Read-Host 'Paste the Application (client) ID here'
$clientId = $clientId.Trim()
if ($clientId -notmatch '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$') {
  Write-Host 'That does not look like a client ID. Please copy it from the Azure app Overview page.' -ForegroundColor Red
  exit 1
}

$audience = Read-Host 'Audience - type 1 for personal accounts (consumers), 2 for personal + work/school (common) [1]'
if ([string]::IsNullOrWhiteSpace($audience)) { $audience = '1' }
$tenant = if ($audience -eq '2') { 'common' } else { 'consumers' }

if (Test-Path $SettingsPath) { $settings = Get-Content $SettingsPath -Raw | ConvertFrom-Json }
else { $settings = [pscustomobject]@{} }
$settings | Add-Member -NotePropertyName 'msClientId' -NotePropertyValue $clientId -Force
$settings | Add-Member -NotePropertyName 'tenant' -NotePropertyValue $tenant -Force
[System.IO.File]::WriteAllText($SettingsPath, ($settings | ConvertTo-Json -Compress))

Write-Host ''
Write-Host 'DONE.' -ForegroundColor Green
Write-Host "Client ID saved: $clientId"
Write-Host "Audience saved:  $tenant"
Write-Host "Settings:        $SettingsPath"
Write-Host ''
Write-Host 'Restart Luxis.exe and click "Sign in with Microsoft". The page will say Luxis.' -ForegroundColor Cyan
