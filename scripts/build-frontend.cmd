@echo off
setlocal

set "ROOT=%~dp0.."
set "NODE_DIR=%ROOT%\.tools\node-v20.20.2-win-x64"

cd /d "%ROOT%\frontend"

where npm >nul 2>nul
if not errorlevel 1 (
  npm run build
  if errorlevel 1 exit /b 1
  exit /b 0
)

if exist "%NODE_DIR%\npm.cmd" (
  call "%NODE_DIR%\npm.cmd" run build
  if errorlevel 1 exit /b 1
  exit /b 0
)

if exist "%NODE_DIR%\node.exe" if exist "node_modules\vite\bin\vite.js" (
  "%NODE_DIR%\node.exe" node_modules\vite\bin\vite.js build
  if errorlevel 1 exit /b 1
  exit /b 0
)

echo npm was not found. Install Node.js 20+ or provide a private portable runtime at:
echo   %NODE_DIR%
exit /b 1
