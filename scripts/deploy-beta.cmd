@echo off
setlocal

set "REPO_ROOT=%~dp0.."
pushd "%REPO_ROOT%"
if errorlevel 1 (
  echo Could not switch to repo root: %REPO_ROOT%
  exit /b 1
)

call deploy\beta\deploy-beta.cmd
set "EXIT_CODE=%errorlevel%"
popd
exit /b %EXIT_CODE%
