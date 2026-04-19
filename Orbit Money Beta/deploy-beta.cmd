@echo off
setlocal

cd /d "%~dp0\.."

for /f "tokens=*" %%b in ('git branch --show-current 2^>nul') do set CURRENT_BRANCH=%%b
if /I not "%CURRENT_BRANCH%"=="Beta" (
  echo Warning: current branch is "%CURRENT_BRANCH%", not "Beta".
  echo This will deploy whatever is currently checked out.
  echo.
)

docker compose -f "Orbit Money Beta\docker-compose.yml" -p orbitmoney-beta up --build -d
docker ps --filter "name=orbit-money-beta" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

echo.
echo Beta should be available at http://localhost:5019
echo Point orbitbeta.overbay.app to this service/port in Cloudflare Tunnel.
