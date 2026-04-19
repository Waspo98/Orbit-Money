@echo off
setlocal

set "ROOT=%~dp0.."
set "NODE_DIR=%ROOT%\.tools\node-v20.20.2-win-x64"
set "PATH=%NODE_DIR%;%PATH%"

cd /d "%ROOT%\frontend"
npm run build
