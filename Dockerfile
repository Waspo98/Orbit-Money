# syntax=docker/dockerfile:1
# ==============================================================================
# Orbit Money — Multi-stage Dockerfile (KMP wasmJs PWA + Express SQLite Backend)
# ==============================================================================

# ---- Stage 1: Prepare or Build Compose Multiplatform wasmJs PWA ----
FROM eclipse-temurin:21-jdk AS build-wasm
WORKDIR /workspace

# Copy pre-built host distribution if present, plus Gradle sources for fallback CI builds
COPY gradlew gradlew.bat settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle/ ./gradle/
COPY androidApp/build.gradle.kts ./androidApp/
COPY composeApp/build.gradle.kts ./composeApp/
COPY composeApp/src/ ./composeApp/src/
COPY composeApp/build/dist/wasmJs/productionExecutable* ./composeApp/build/dist/wasmJs/productionExecutable/

RUN --mount=type=cache,target=/root/.gradle \
    if [ -f "./composeApp/build/dist/wasmJs/productionExecutable/index.html" ]; then \
        echo "==> Using pre-built wasmJs productionExecutable bundle from host"; \
    else \
        echo "==> No pre-built wasmJs bundle found; building inside container..."; \
        export ANDROID_HOME=/opt/android-sdk && \
        apt-get update && apt-get install -y --no-install-recommends unzip wget ca-certificates && \
        mkdir -p ${ANDROID_HOME}/cmdline-tools && \
        wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O /tmp/cmdtools.zip && \
        unzip -q /tmp/cmdtools.zip -d ${ANDROID_HOME}/cmdline-tools && \
        mv ${ANDROID_HOME}/cmdline-tools/cmdline-tools ${ANDROID_HOME}/cmdline-tools/latest && \
        rm /tmp/cmdtools.zip && \
        yes | ${ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager --licenses >/dev/null && \
        ${ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager "platforms;android-35" "build-tools;35.0.0" >/dev/null && \
        rm -rf /var/lib/apt/lists/* && \
        chmod +x ./gradlew && \
        ./gradlew :composeApp:wasmJsBrowserDistribution -x compileProductionExecutableKotlinWasmJsOptimize --no-daemon; \
    fi

# ---- Stage 2: Production runtime ----
FROM node:20-alpine AS prod
WORKDIR /app

LABEL org.opencontainers.image.source="https://github.com/Waspo98/Orbit-Money"
LABEL org.opencontainers.image.description="Self-hosted personal finance tracker built with Jetpack Compose Multiplatform"

# Install build tools for better-sqlite3 native compilation (removed after install).
RUN apk add --no-cache sqlite wget \
    && apk add --no-cache --virtual .build-deps python3 make g++

# Install backend production deps
COPY backend/package*.json ./
RUN npm install --omit=dev --no-audit --no-fund \
    && apk del .build-deps

# Copy backend source
COPY backend/src ./src

# Copy built wasmJs PWA distribution into the location Express serves from
COPY --from=build-wasm /workspace/composeApp/build/dist/wasmJs/productionExecutable ./public

# Data directory (volume mount target)
RUN mkdir -p /app/data

ENV NODE_ENV=production
EXPOSE 5008

CMD ["node", "src/server.js"]
