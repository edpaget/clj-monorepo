FROM node:20-alpine AS builder

WORKDIR /app

# Copy package files for dependency caching
COPY bashketball-editor-ui/package*.json ./

# Install dependencies
RUN npm ci

# Copy shadow-cljs config and source
COPY bashketball-editor-ui/shadow-cljs.edn ./
COPY bashketball-editor-ui/src ./src
COPY bashketball-editor-ui/resources ./resources
COPY bashketball-editor-ui/public ./public

# Build production assets
RUN npm run build
RUN npm run css:build

# Inject production API config (empty string = relative URLs)
RUN sed -i 's|<div id="app"></div>|<div id="app"></div><script>window.API_BASE_URL="";</script>|' public/index.html

FROM nginx:alpine

# Copy built assets
COPY --from=builder /app/public /usr/share/nginx/html

# Custom nginx config for SPA routing
COPY bashketball-editor-ui/nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD wget -q --spider http://localhost:80/ || exit 1

CMD ["nginx", "-g", "daemon off;"]
