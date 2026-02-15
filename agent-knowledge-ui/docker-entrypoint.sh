#!/bin/sh
set -e

# Railway provides PORT environment variable
# Default to 80 if not set
PORT=${PORT:-80}

# API URLs for proxying (use environment variables or defaults)
TELEMETRY_API_URL=${TELEMETRY_API_URL:-https://agent-telemetry-service-production.up.railway.app}
KNOWLEDGE_API_URL=${KNOWLEDGE_API_URL:-https://agent-knowledge-production.up.railway.app}

# Create nginx config from template with the correct port
cat > /etc/nginx/conf.d/default.conf << EOF
server {
    listen ${PORT};
    server_name localhost;
    root /usr/share/nginx/html;
    index index.html;

    # DNS resolver for dynamic upstream resolution (Google + Cloudflare DNS)
    resolver 8.8.8.8 1.1.1.1 valid=300s;
    resolver_timeout 5s;

    # Gzip compression
    gzip on;
    gzip_vary on;
    gzip_min_length 1024;
    gzip_proxied any;
    gzip_types text/plain text/css text/xml text/javascript application/javascript application/json application/xml;

    # Security headers
    add_header X-Frame-Options "SAMEORIGIN" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header X-XSS-Protection "1; mode=block" always;

    # Cache static assets
    location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg|woff|woff2|ttf|eot)\$ {
        expires 1y;
        add_header Cache-Control "public, immutable";
    }

    # Telemetry API proxy
    location /telemetry-api/ {
        set \$upstream_telemetry ${TELEMETRY_API_URL};
        rewrite ^/telemetry-api/(.*)\$ /api/\$1 break;
        proxy_pass \$upstream_telemetry;
        proxy_http_version 1.1;
        proxy_ssl_server_name on;
        proxy_set_header Host agent-telemetry-service-production.up.railway.app;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto https;
        proxy_connect_timeout 30s;
        proxy_send_timeout 60s;
        proxy_read_timeout 60s;
    }

    # Knowledge API proxy
    location /api/ {
        set \$upstream_knowledge ${KNOWLEDGE_API_URL};
        proxy_pass \$upstream_knowledge\$request_uri;
        proxy_http_version 1.1;
        proxy_ssl_server_name on;
        proxy_set_header Host agent-knowledge-production.up.railway.app;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto https;
        proxy_connect_timeout 30s;
        proxy_send_timeout 60s;
        proxy_read_timeout 60s;
    }

    # SPA fallback - serve index.html for all other routes
    location / {
        try_files \$uri \$uri/ /index.html;
    }

    # Health check endpoint
    location /health {
        access_log off;
        return 200 "healthy\n";
        add_header Content-Type text/plain;
    }
}
EOF

echo "Starting nginx on port ${PORT}..."
echo "Telemetry API: ${TELEMETRY_API_URL}"
echo "Knowledge API: ${KNOWLEDGE_API_URL}"

# Start nginx
exec nginx -g "daemon off;"
