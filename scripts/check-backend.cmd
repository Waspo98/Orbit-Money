@echo off
setlocal

set "ROOT=%~dp0.."
set "NODE_DIR=%ROOT%\.tools\node-v20.20.2-win-x64"
set "NODE_BIN="

cd /d "%ROOT%\backend"

if exist "%NODE_DIR%\node.exe" (
  set "PATH=%NODE_DIR%;%PATH%"
  set "NODE_BIN=%NODE_DIR%\node.exe"
)

if not defined NODE_BIN (
  where node >nul 2>nul
  if errorlevel 1 (
    echo node was not found. Install Node.js 20+ or provide a private portable runtime at:
    echo   %NODE_DIR%
    exit /b 1
  )
  set "NODE_BIN=node"
)

"%NODE_BIN%" --check src\server.js
if errorlevel 1 exit /b 1

for %%f in (test\*.test.js) do (
  "%NODE_BIN%" "%%f"
  if errorlevel 1 exit /b 1
)

exit /b 0
