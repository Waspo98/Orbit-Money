@echo off
setlocal

cd /d "%~dp0\..\.."

git diff --quiet
if errorlevel 1 (
  echo Working tree has unstaged changes. Commit or stash them before deploying beta.
  exit /b 1
)

git diff --cached --quiet
if errorlevel 1 (
  echo Working tree has staged changes. Commit or unstage them before deploying beta.
  exit /b 1
)

for /f "tokens=*" %%b in ('git branch --show-current 2^>nul') do set CURRENT_BRANCH=%%b
echo Current branch: %CURRENT_BRANCH%

git fetch origin Beta
if errorlevel 1 exit /b 1

git checkout Beta
if errorlevel 1 exit /b 1

git pull --ff-only origin Beta
if errorlevel 1 exit /b 1

docker compose -f "deploy\beta\docker-compose.yml" -p orbitmoney-beta up --build -d
if errorlevel 1 exit /b 1

docker ps --filter "name=orbit-money-beta" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

echo.
echo Beta should be available at http://localhost:5019
echo Point orbitbeta.overbay.app to this service/port in Cloudflare Tunnel.
