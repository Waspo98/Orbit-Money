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

for /f "tokens=*" %%s in ('git rev-parse HEAD') do set "DEPLOY_SHA=%%s"
for /f "tokens=*" %%v in ('powershell -NoProfile -Command "(Get-Content backend/package.json | ConvertFrom-Json).version"') do set "EXPECTED_VERSION=%%v"

if "%DEPLOY_SHA%"=="" (
  echo Could not determine deploy commit SHA.
  exit /b 1
)

if "%EXPECTED_VERSION%"=="" (
  echo Could not determine expected app version.
  exit /b 1
)

set "IMAGE=ghcr.io/waspo98/orbit-money"
set "COMPOSE_CMD=docker compose -p orbitmoney"

if not "%GITHUB_SHA%"=="" set "DEPLOY_SHA=%GITHUB_SHA%"

if not "%GITHUB_TOKEN%"=="" (
  if "%GITHUB_ACTOR%"=="" set "GITHUB_ACTOR=github-actions"
  echo Logging in to ghcr.io as %GITHUB_ACTOR%...
  echo %GITHUB_TOKEN% | docker login ghcr.io -u "%GITHUB_ACTOR%" --password-stdin
  if errorlevel 1 exit /b 1
) else (
  echo GITHUB_TOKEN is not set; docker push will use existing Docker registry credentials.
)

echo Building live Docker image for %DEPLOY_SHA%...
docker build -t %IMAGE%:latest -t %IMAGE%:main -t %IMAGE%:main-%DEPLOY_SHA% .
if errorlevel 1 exit /b 1

echo Publishing live Docker image to ghcr.io/waspo98/orbit-money...
docker push %IMAGE%:latest
if errorlevel 1 exit /b 1

docker push %IMAGE%:main
if errorlevel 1 exit /b 1

docker push %IMAGE%:main-%DEPLOY_SHA%
if errorlevel 1 exit /b 1

echo Building local Compose image from the checked-out main branch...
%COMPOSE_CMD% build orbit-money
if errorlevel 1 exit /b 1

%COMPOSE_CMD% up -d --no-deps orbit-money
if errorlevel 1 exit /b 1

docker ps --filter "name=orbit-money" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

for /f "tokens=*" %%v in ('docker exec orbit-money node -p "require('/app/package.json').version" 2^>nul') do set "RUNNING_VERSION=%%v"
if not "%RUNNING_VERSION%"=="%EXPECTED_VERSION%" (
  echo.
  echo Version check failed: expected %EXPECTED_VERSION%, but orbit-money is running %RUNNING_VERSION%.
  exit /b 1
)

echo.
echo Live should be available at http://localhost:5008
echo Running version: %RUNNING_VERSION% from %DEPLOY_SHA%
echo Point your production hostname to this service/port if using a reverse proxy or tunnel.
