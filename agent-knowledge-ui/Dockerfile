# =====================================================
# Multi-stage Dockerfile for agent-knowledge-ui (React)
# Build: docker build -f agent-knowledge-ui/Dockerfile -t agent-knowledge-ui .
# Run from parent directory (ai-agent-platform)
# =====================================================

# ---------- Build stage ----------
FROM node:20-alpine AS builder

WORKDIR /app

# Copy package files first for better layer caching
COPY agent-knowledge-ui/package*.json ./

# Install dependencies
RUN npm ci --silent

# Copy source code (includes .env.production for Vite)
COPY agent-knowledge-ui/ ./

# Set NODE_ENV for Vite to use .env.production
ENV NODE_ENV=production

# Build the application (explicitly use production mode to load .env.production)
RUN npm run build -- --mode production

# ---------- Runtime stage (nginx) ----------
FROM nginx:alpine

# Remove default nginx config
RUN rm /etc/nginx/conf.d/default.conf

# Copy custom nginx config (for local/docker-compose use)
COPY agent-knowledge-ui/nginx.conf /etc/nginx/conf.d/default.conf

# Copy entrypoint script for Railway (uses PORT env var)
COPY agent-knowledge-ui/docker-entrypoint.sh /docker-entrypoint.sh
RUN chmod +x /docker-entrypoint.sh

# Copy built static files
COPY --from=builder /app/build /usr/share/nginx/html

# Expose port (Railway will set PORT env var)
EXPOSE 80

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=10s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:${PORT:-80}/ || exit 1

# Use entrypoint that handles PORT env var
ENTRYPOINT ["/docker-entrypoint.sh"]
