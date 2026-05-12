#Requires -RunAsAdministrator

[CmdletBinding()]
param(
  [string]$RunnerRoot = 'C:\actions-runner',
  [string]$ServiceName = 'actions.runner.Waspo98-Orbit-Money.orbit-money-server',
  [string]$DisplayName = 'GitHub Actions Runner (Waspo98-Orbit-Money.orbit-money-server)'
)

$ErrorActionPreference = 'Stop'

$serviceExe = Join-Path $RunnerRoot 'bin\RunnerService.exe'
if (-not (Test-Path -LiteralPath $serviceExe)) {
  throw "Runner service host was not found at $serviceExe"
}

$existingService = Get-Service -Name $ServiceName -ErrorAction SilentlyContinue
if (-not $existingService) {
  New-Service `
    -Name $ServiceName `
    -BinaryPathName $serviceExe `
    -DisplayName $DisplayName `
    -StartupType Automatic `
    -Description 'GitHub Actions self-hosted runner for Orbit Money deploy workflows.' | Out-Null
}

Set-Content -LiteralPath (Join-Path $RunnerRoot '.service') -Value $ServiceName

& sc.exe failure $ServiceName reset= 60 actions= restart/5000/restart/30000/restart/60000 | Out-Null

Get-Process -Name Runner.Listener -ErrorAction SilentlyContinue | Stop-Process -Force

Start-Service -Name $ServiceName
Get-Service -Name $ServiceName | Select-Object Name, DisplayName, Status, StartType
