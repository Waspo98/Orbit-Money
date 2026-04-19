@echo off
setlocal

set "ROOT=%~dp0.."
set "NODE_DIR=%ROOT%\.tools\node-v20.20.2-win-x64"
set "NPM_CLI=%NODE_DIR%\node_modules\npm\bin\npm-cli.js"

if not exist "%NODE_DIR%\node.exe" (
  echo Portable Node.js was not found at:
  echo   %NODE_DIR%
  echo.
  echo Ask Codex to set up the local non-Docker dev environment again.
  pause
  exit /b 1
)

if not exist "%ROOT%\backend\.env" (
  echo Local backend env file was not found:
  echo   %ROOT%\backend\.env
  echo.
  echo Ask Codex to recreate the local non-Docker dev env file.
  pause
  exit /b 1
)

set "PATH=%NODE_DIR%;%PATH%"

if not exist "%ROOT%\backend\node_modules" (
  echo Installing backend dependencies...
  cd /d "%ROOT%\backend"
  call "%NODE_DIR%\node.exe" "%NPM_CLI%" ci --cache "%ROOT%\.tools\npm-cache" --no-audit --no-fund
  if errorlevel 1 (
    echo Backend dependency install failed.
    pause
    exit /b 1
  )
)

if not exist "%ROOT%\frontend\node_modules" (
  echo Installing frontend dependencies...
  cd /d "%ROOT%\frontend"
  call "%NODE_DIR%\node.exe" "%NPM_CLI%" ci --cache "%ROOT%\.tools\npm-cache" --no-audit --no-fund
  if errorlevel 1 (
    echo Frontend dependency install failed.
    pause
    exit /b 1
  )
)

echo Stopping any existing local dev servers on ports 5018 and 5173...
call "%ROOT%\scripts\stop-dev.cmd"

echo Starting Orbit Money local backend on http://localhost:5018 ...
start "Orbit Money Backend" cmd /k ""%ROOT%\scripts\dev-backend.cmd""

echo Starting Orbit Money local frontend on http://localhost:5173 ...
start "Orbit Money Frontend" cmd /k ""%ROOT%\scripts\dev-frontend.cmd""

echo Waiting for Vite to start...
timeout /t 4 /nobreak >nul

echo Opening http://localhost:5173 ...
start "" "http://localhost:5173"

echo.
echo Local beta is starting.
echo.
echo Use this URL:
echo   http://localhost:5173
echo.
echo Do not use http://localhost:5018 in the browser; that is only the API.
echo.
echo To stop local beta later, run:
echo   scripts\stop-dev.cmd
echo.
endlocal
