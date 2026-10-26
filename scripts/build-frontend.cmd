@echo off
setlocal

set "ROOT=%~dp0.."
cd /d "%ROOT%"

echo ==> Building Web PWA bundle via Compose Multiplatform (wasmJs)...
call ".\gradlew.bat" :composeApp:wasmJsBrowserDistribution
if errorlevel 1 (
    echo [ERROR] WasmJs browser distribution build failed.
    exit /b 1
)

echo ==> WasmJs distribution build succeeded:
echo     composeApp\build\dist\wasmJs\productionExecutable
exit /b 0
