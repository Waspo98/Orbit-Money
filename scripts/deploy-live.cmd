@echo off
setlocal

cd /d "%~dp0\.."

git diff --quiet
if errorlevel 1 (
  echo Working tree has unstaged changes. Commit or stash them before deploying live.
  exit /b 1
)

git diff --cached --quiet
if errorlevel 1 (
  echo Working tree has staged changes. Commit or unstage them before deploying live.
  exit /b 1
)

for /f "tokens=*" %%b in ('git branch --show-current 2^>nul') do set CURRENT_BRANCH=%%b
echo Current branch: %CURRENT_BRANCH%

git fetch origin main
if errorlevel 1 exit /b 1

git checkout main
if errorlevel 1 exit /b 1

git pull --ff-only origin main
if errorlevel 1 exit /b 1

docker compose -p orbitmoney pull
if errorlevel 1 exit /b 1

docker compose -p orbitmoney up -d
if errorlevel 1 exit /b 1

docker ps --filter "name=orbit-money" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

echo.
echo Live should be available at http://localhost:5008
echo Point your production hostname to this service/port if using a reverse proxy or tunnel.
