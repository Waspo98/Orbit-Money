@echo off
setlocal

set "ROOT=%~dp0.."
set "NODE_DIR=%ROOT%\.tools\node-v20.20.2-win-x64"
set "NPM_CLI=%NODE_DIR%\node_modules\npm\bin\npm-cli.js"
set "PATH=%NODE_DIR%;%PATH%"

cd /d "%ROOT%\frontend"
"%NODE_DIR%\node.exe" "%NPM_CLI%" run build
