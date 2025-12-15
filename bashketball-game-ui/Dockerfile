FROM node:20-alpine AS builder

# Install Java and Clojure for shadow-cljs deps mode
RUN apk add --no-cache openjdk21-jre curl bash
RUN curl -L -O https://github.com/clojure/brew-install/releases/latest/download/linux-install.sh && \
    chmod +x linux-install.sh && \
    ./linux-install.sh && \
    rm linux-install.sh

WORKDIR /app/bashketball-game-ui

# Copy package files for dependency caching
COPY bashketball-game-ui/package*.json ./

# Install npm dependencies
RUN npm ci

# Copy deps.edn for Clojure dependency resolution (shadow-cljs needs root deps.edn)
COPY deps.edn /app/

# Copy local dependency deps.edn files
COPY bashketball-schemas/deps.edn /app/bashketball-schemas/
COPY bashketball-game/deps.edn /app/bashketball-game/
COPY bashketball-ui/deps.edn /app/bashketball-ui/
COPY cljs-tlr/deps.edn /app/cljs-tlr/
COPY bashketball-game-ui/deps.edn ./

# Copy local dependency source files
COPY bashketball-schemas/src /app/bashketball-schemas/src
COPY bashketball-game/src /app/bashketball-game/src
COPY bashketball-ui/src /app/bashketball-ui/src
COPY cljs-tlr/src /app/cljs-tlr/src

# Copy shadow-cljs config and source
COPY bashketball-game-ui/shadow-cljs.edn ./
COPY bashketball-game-ui/src ./src
COPY bashketball-game-ui/resources ./resources
COPY bashketball-game-ui/content ./content
COPY bashketball-game-ui/public ./public
COPY bashketball-game-ui/scripts ./scripts
COPY bashketball-game-ui/tailwind.config.js ./
COPY bashketball-game-ui/postcss.config.js ./

# Build production assets with hashed filenames for cache busting
RUN npm run build:prod

# Clean up build artifacts not needed in production
RUN rm -f public/js/libs-meta.json

# Inject production API config in <head> so it runs before any JS loads
# Empty string = relative URLs (UI and API on same origin)
RUN sed -i 's|</head>|<script>window.API_BASE_URL="";</script></head>|' public/index.html

FROM nginx:alpine

# Copy built assets
COPY --from=builder /app/bashketball-game-ui/public /usr/share/nginx/html

# Fix permissions for nginx
RUN chmod -R 755 /usr/share/nginx/html

# Custom nginx config for SPA routing
COPY bashketball-game-ui/nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD wget -q --spider http://localhost:80/ || exit 1

CMD ["nginx", "-g", "daemon off;"]
