@echo off
setlocal

set "REPO_ROOT=%~dp0.."
pushd "%REPO_ROOT%"
if errorlevel 1 (
  echo Could not switch to repo root: %REPO_ROOT%
  exit /b 1
)

set "GIT_CONFIG_COUNT=1"
set "GIT_CONFIG_KEY_0=safe.directory"
set "GIT_CONFIG_VALUE_0=%CD%"

git rev-parse --show-toplevel >nul 2>nul
if errorlevel 1 (
  echo Repo root check failed. Current directory is:
  cd
  git rev-parse --show-toplevel
  popd
  exit /b 1
)

git diff --quiet
if errorlevel 1 (
  echo Working tree has unstaged changes. Commit or stash them before deploying live.
  popd
  exit /b 1
)

git diff --cached --quiet
if errorlevel 1 (
  echo Working tree has staged changes. Commit or unstage them before deploying live.
  popd
  exit /b 1
)

for /f "tokens=*" %%b in ('git branch --show-current 2^>nul') do set CURRENT_BRANCH=%%b
echo Current branch: %CURRENT_BRANCH%

git fetch origin main
if errorlevel 1 (
  popd
  exit /b 1
)

git checkout main
if errorlevel 1 (
  popd
  exit /b 1
)

git pull --ff-only origin main
if errorlevel 1 (
  popd
  exit /b 1
)

for /f "tokens=*" %%s in ('git rev-parse HEAD') do set "DEPLOY_SHA=%%s"
for /f "tokens=*" %%v in ('powershell -NoProfile -Command "(Get-Content backend/package.json | ConvertFrom-Json).version"') do set "EXPECTED_VERSION=%%v"

if "%DEPLOY_SHA%"=="" (
  echo Could not determine deploy commit SHA.
  popd
  exit /b 1
)

if "%EXPECTED_VERSION%"=="" (
  echo Could not determine expected app version.
  popd
  exit /b 1
)

set "IMAGE=ghcr.io/waspo98/orbit-money"
set "COMPOSE_CMD=docker compose -p orbitmoney"

if not "%GITHUB_SHA%"=="" set "DEPLOY_SHA=%GITHUB_SHA%"

if not "%GITHUB_TOKEN%"=="" (
  if "%GITHUB_ACTOR%"=="" set "GITHUB_ACTOR=github-actions"
  echo Logging in to ghcr.io as %GITHUB_ACTOR%...
  echo %GITHUB_TOKEN% | docker login ghcr.io -u "%GITHUB_ACTOR%" --password-stdin
  if errorlevel 1 (
    popd
    exit /b 1
  )
) else (
  echo GITHUB_TOKEN is not set; docker push will use existing Docker registry credentials.
)

echo Building live Docker image for %DEPLOY_SHA%...
docker build -t %IMAGE%:latest -t %IMAGE%:main -t %IMAGE%:main-%DEPLOY_SHA% .
if errorlevel 1 (
  popd
  exit /b 1
)

echo Publishing live Docker image to ghcr.io/waspo98/orbit-money...
docker push %IMAGE%:latest
if errorlevel 1 (
  echo WARNING: Could not push latest image to ghcr.io. Continuing with local deployment.
)

docker push %IMAGE%:main
if errorlevel 1 (
  echo WARNING: Could not push main image to ghcr.io. Continuing with local deployment.
)

docker push %IMAGE%:main-%DEPLOY_SHA%
if errorlevel 1 (
  echo WARNING: Could not push main SHA image to ghcr.io. Continuing with local deployment.
)

echo Building local Compose image from the checked-out main branch...
%COMPOSE_CMD% build orbit-money
if errorlevel 1 (
  popd
  exit /b 1
)

%COMPOSE_CMD% up -d --no-deps orbit-money
if errorlevel 1 (
  popd
  exit /b 1
)

docker ps --filter "name=orbit-money" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

for /f "tokens=*" %%v in ('docker exec orbit-money node -p "require('/app/package.json').version" 2^>nul') do set "RUNNING_VERSION=%%v"
if not "%RUNNING_VERSION%"=="%EXPECTED_VERSION%" (
  echo.
  echo Version check failed: expected %EXPECTED_VERSION%, but orbit-money is running %RUNNING_VERSION%.
  popd
  exit /b 1
)

echo.
echo Live should be available at http://localhost:5008
echo Running version: %RUNNING_VERSION% from %DEPLOY_SHA%
echo Point your production hostname to this service/port if using a reverse proxy or tunnel.
popd
