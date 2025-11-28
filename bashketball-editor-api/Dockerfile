FROM clojure:temurin-21-tools-deps-alpine AS builder

WORKDIR /app

# Copy dependency files first for layer caching
COPY deps.edn ./
COPY db/deps.edn db/
COPY graphql-server/deps.edn graphql-server/
COPY authn/deps.edn authn/
COPY oidc/deps.edn oidc/
COPY oidc-github/deps.edn oidc-github/
COPY exclusive-initializer/deps.edn exclusive-initializer/
COPY bashketball-editor-api/deps.edn bashketball-editor-api/

# Download dependencies
RUN clojure -P -M:bashketball-editor-api

# Copy source files
COPY db db/
COPY graphql-server graphql-server/
COPY authn authn/
COPY oidc oidc/
COPY oidc-github oidc-github/
COPY exclusive-initializer exclusive-initializer/
COPY bashketball-editor-api bashketball-editor-api/
COPY build build/

# Build uberjar
RUN clojure -T:build uberjar :project :bashketball-editor-api

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Install git for JGit operations
RUN apk add --no-cache git

COPY --from=builder /app/bashketball-editor-api/target/bashketball-editor-api.jar ./app.jar

# Create non-root user
RUN addgroup -g 1000 app && adduser -u 1000 -G app -D app
RUN mkdir -p /data/bashketball-cards && chown -R app:app /data
USER app

EXPOSE 3000

HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
  CMD wget -q --spider http://localhost:3000/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
CMD ["prod"]
