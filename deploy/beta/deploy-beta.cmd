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

if "%ORBIT_PUBLISH_IMAGE%"=="1" (
  if "%GITHUB_TOKEN%"=="" (
    echo GITHUB_TOKEN is required to publish ghcr.io/waspo98/orbit-money from GitHub Actions.
    exit /b 1
  )

  if "%GITHUB_ACTOR%"=="" set "GITHUB_ACTOR=github-actions"
  if "%GITHUB_SHA%"=="" set "GITHUB_SHA=manual"

  echo Publishing beta Docker image to ghcr.io/waspo98/orbit-money...
  echo %GITHUB_TOKEN% | docker login ghcr.io -u "%GITHUB_ACTOR%" --password-stdin
  if errorlevel 1 exit /b 1

  docker build -t ghcr.io/waspo98/orbit-money:beta -t ghcr.io/waspo98/orbit-money:beta-%GITHUB_SHA% .
  if errorlevel 1 exit /b 1

  docker push ghcr.io/waspo98/orbit-money:beta
  if errorlevel 1 exit /b 1

  docker push ghcr.io/waspo98/orbit-money:beta-%GITHUB_SHA%
  if errorlevel 1 exit /b 1
)

docker compose -f "deploy\beta\docker-compose.yml" -p orbitmoney-beta pull
if errorlevel 1 exit /b 1

docker compose -f "deploy\beta\docker-compose.yml" -p orbitmoney-beta up -d
if errorlevel 1 exit /b 1

docker ps --filter "name=orbit-money-beta" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

echo.
echo Beta should be available at http://localhost:5019
echo Point your beta hostname to this service/port if using a reverse proxy or tunnel.
