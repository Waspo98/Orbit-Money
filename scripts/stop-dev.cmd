@echo off
setlocal

for %%P in (5018 5173) do (
  for /f "tokens=5" %%A in ('netstat -ano ^| findstr ":%%P" ^| findstr "LISTENING"') do (
    echo Stopping process %%A on port %%P
    taskkill /PID %%A /F >nul 2>nul
  )
)

echo Local dev servers stopped.
