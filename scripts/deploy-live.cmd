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

if "%ORBIT_PUBLISH_IMAGE%"=="1" (
  if "%GITHUB_TOKEN%"=="" (
    echo GITHUB_TOKEN is required to publish ghcr.io/waspo98/orbit-money from GitHub Actions.
    exit /b 1
  )

  if "%GITHUB_ACTOR%"=="" set "GITHUB_ACTOR=github-actions"
  if "%GITHUB_SHA%"=="" set "GITHUB_SHA=manual"

  echo Publishing live Docker image to ghcr.io/waspo98/orbit-money...
  echo %GITHUB_TOKEN% | docker login ghcr.io -u "%GITHUB_ACTOR%" --password-stdin
  if errorlevel 1 exit /b 1

  docker build -t ghcr.io/waspo98/orbit-money:latest -t ghcr.io/waspo98/orbit-money:main -t ghcr.io/waspo98/orbit-money:main-%GITHUB_SHA% .
  if errorlevel 1 exit /b 1

  docker push ghcr.io/waspo98/orbit-money:latest
  if errorlevel 1 exit /b 1

  docker push ghcr.io/waspo98/orbit-money:main
  if errorlevel 1 exit /b 1

  docker push ghcr.io/waspo98/orbit-money:main-%GITHUB_SHA%
  if errorlevel 1 exit /b 1
)

set "COMPOSE_CMD=docker compose -p orbitmoney"

%COMPOSE_CMD% pull
if errorlevel 1 (
  echo.
  echo Docker image pull failed. Building orbit-money locally from the checked-out main branch instead.
  echo This usually means GHCR denied the pull, often because Docker has stale registry credentials or the package is not publicly readable.
  %COMPOSE_CMD% build orbit-money
  if errorlevel 1 exit /b 1
)

%COMPOSE_CMD% up -d
if errorlevel 1 exit /b 1

docker ps --filter "name=orbit-money" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

echo.
echo Live should be available at http://localhost:5008
echo Point your production hostname to this service/port if using a reverse proxy or tunnel.
