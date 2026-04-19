@echo off
setlocal

set "ROOT=%~dp0.."
set "NODE_DIR=%ROOT%\.tools\node-v20.20.2-win-x64"
set "PATH=%NODE_DIR%;%PATH%"
set "VITE_API_PROXY_TARGET=http://localhost:5018"

cd /d "%ROOT%\frontend"
npm run dev
