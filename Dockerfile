# ==============================================================================
# Budget Tracker — Multi-stage Dockerfile
# ==============================================================================
# Build stage uses node:20-slim (Debian) because Rollup/musl hangs on Docker
# Desktop + WSL2 with Alpine. Prod stage uses node:20-alpine for smaller image.
# Same pattern as Lawn Tracker.
# ==============================================================================

# ---- Stage 1: Build frontend ----
FROM node:20-slim AS build
WORKDIR /app/frontend

COPY frontend/package*.json ./
RUN npm install --no-audit --no-fund

COPY frontend/ ./
RUN npm run build


# ---- Stage 2: Production runtime ----
FROM node:20-alpine AS prod
WORKDIR /app

# Install build tools for better-sqlite3 native compilation (removed after install).
# sqlite runtime libs stay for the lifetime of the image.
RUN apk add --no-cache sqlite wget \
    && apk add --no-cache --virtual .build-deps python3 make g++

# Install backend production deps
COPY backend/package*.json ./
RUN npm install --omit=dev --no-audit --no-fund \
    && apk del .build-deps

# Copy backend source
COPY backend/src ./src

# Copy built frontend into the location Express serves from
COPY --from=build /app/frontend/dist ./public

# Data directory (volume mount target)
RUN mkdir -p /app/data

ENV NODE_ENV=production
EXPOSE 5008

CMD ["node", "src/server.js"]
