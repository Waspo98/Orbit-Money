# ==============================================================================
# Orbit Money — Multi-stage Dockerfile (KMP wasmJs PWA + Express SQLite Backend)
# ==============================================================================

# ---- Stage 1: Build Compose Multiplatform wasmJs PWA ----
FROM eclipse-temurin:21-jdk AS build-wasm
WORKDIR /workspace

# Copy Gradle wrapper and build configuration files
COPY gradlew gradlew.bat settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle/ ./gradle/
COPY composeApp/build.gradle.kts ./composeApp/
COPY composeApp/src/ ./composeApp/src/

RUN chmod +x ./gradlew && ./gradlew :composeApp:wasmJsBrowserDistribution --no-daemon

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
