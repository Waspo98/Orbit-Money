@echo off
setlocal

cd /d "%~dp0.."
call deploy\beta\deploy-beta.cmd
exit /b %errorlevel%
