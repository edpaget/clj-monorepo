FROM node:20-alpine AS builder

# Install Java and Clojure for shadow-cljs deps mode
RUN apk add --no-cache openjdk21-jre curl bash
RUN curl -L -O https://github.com/clojure/brew-install/releases/latest/download/linux-install.sh && \
    chmod +x linux-install.sh && \
    ./linux-install.sh && \
    rm linux-install.sh

WORKDIR /app/bashketball-editor-ui

# Copy package files for dependency caching
COPY bashketball-editor-ui/package*.json ./

# Install npm dependencies
RUN npm ci

# Copy deps.edn for Clojure dependency resolution (shadow-cljs needs root deps.edn)
COPY deps.edn /app/
COPY bashketball-editor-ui/deps.edn ./

# Copy shadow-cljs config and source
COPY bashketball-editor-ui/shadow-cljs.edn ./
COPY bashketball-editor-ui/src ./src
COPY bashketball-editor-ui/resources ./resources
COPY bashketball-editor-ui/public ./public
COPY bashketball-editor-ui/tailwind.config.js ./

# Create production shadow-cljs config (without test alias that requires local deps)
RUN sed -i 's/:deps {:aliases \[:test\]}/:deps true/' shadow-cljs.edn

# Build production assets
RUN npm run build
RUN npm run css:build

# Inject production API config (empty string = relative URLs)
RUN sed -i 's|<div id="app"></div>|<div id="app"></div><script>window.API_BASE_URL="";</script>|' public/index.html

FROM nginx:alpine

# Copy built assets
COPY --from=builder /app/bashketball-editor-ui/public /usr/share/nginx/html

# Fix permissions for nginx
RUN chmod -R 755 /usr/share/nginx/html

# Custom nginx config for SPA routing
COPY bashketball-editor-ui/nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD wget -q --spider http://localhost:80/ || exit 1

CMD ["nginx", "-g", "daemon off;"]
