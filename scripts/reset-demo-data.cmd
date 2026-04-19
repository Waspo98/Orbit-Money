@echo off
setlocal

set "ROOT=%~dp0.."
set "DATA_DIR=%ROOT%\data"

echo This will delete the local non-Docker demo database only:
echo   %DATA_DIR%
echo.
echo It will not touch the Docker volume or live deployment data.
echo.
set /p CONFIRM=Type RESET to continue: 

if /i not "%CONFIRM%"=="RESET" (
  echo Canceled.
  exit /b 0
)

call "%ROOT%\scripts\stop-dev.cmd"

if not exist "%DATA_DIR%" (
  mkdir "%DATA_DIR%"
)

del /f /q "%DATA_DIR%\budget.db" 2>nul
del /f /q "%DATA_DIR%\budget.db-journal" 2>nul
del /f /q "%DATA_DIR%\budget.db-wal" 2>nul
del /f /q "%DATA_DIR%\budget.db-shm" 2>nul
del /f /q "%DATA_DIR%\sessions.db" 2>nul
del /f /q "%DATA_DIR%\sessions.db-journal" 2>nul
del /f /q "%DATA_DIR%\sessions.db-wal" 2>nul
del /f /q "%DATA_DIR%\sessions.db-shm" 2>nul

echo Local demo data reset. Starting beta again...
call "%ROOT%\scripts\start-beta.cmd"
