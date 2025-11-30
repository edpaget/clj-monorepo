# syntax=docker/dockerfile:1
FROM clojure:temurin-21-tools-deps-alpine AS builder

WORKDIR /app

# Copy dependency files first for layer caching
COPY deps.edn ./
COPY db/deps.edn db/
COPY graphql-server/deps.edn graphql-server/
COPY authn/deps.edn authn/
COPY oidc/deps.edn oidc/
COPY oidc-google/deps.edn oidc-google/
COPY exclusive-initializer/deps.edn exclusive-initializer/
COPY bashketball-schemas/deps.edn bashketball-schemas/
COPY bashketball-game/deps.edn bashketball-game/
COPY bashketball-game-api/deps.edn bashketball-game-api/

# Download dependencies (mount GitHub token for private packages)
RUN --mount=type=secret,id=gh_packages_token \
    mkdir -p ~/.m2 && \
    echo "<settings><servers><server><id>github</id><username>token</username><password>$(cat /run/secrets/gh_packages_token)</password></server></servers></settings>" > ~/.m2/settings.xml && \
    clojure -P -M:bashketball-game-api && \
    rm -f ~/.m2/settings.xml

# Copy source files
COPY db db/
COPY graphql-server graphql-server/
COPY authn authn/
COPY oidc oidc/
COPY oidc-google oidc-google/
COPY exclusive-initializer exclusive-initializer/
COPY bashketball-schemas bashketball-schemas/
COPY bashketball-game bashketball-game/
COPY bashketball-game-api bashketball-game-api/
COPY build build/

# Build uberjar (mount GitHub token for private packages)
RUN --mount=type=secret,id=gh_packages_token \
    mkdir -p ~/.m2 && \
    echo "<settings><servers><server><id>github</id><username>token</username><password>$(cat /run/secrets/gh_packages_token)</password></server></servers></settings>" > ~/.m2/settings.xml && \
    clojure -T:build uberjar :project :bashketball-game-api && \
    rm -f ~/.m2/settings.xml

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY --from=builder /app/bashketball-game-api/target/bashketball-game-api.jar ./app.jar

# Create non-root user
RUN addgroup -g 1000 app && adduser -u 1000 -G app -D app
USER app

EXPOSE 3000

HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
  CMD wget -q --spider http://localhost:3000/health || exit 1

# JVM flags for faster startup:
# -XX:+TieredCompilation -XX:TieredStopAtLevel=1: Use only C1 compiler (faster startup, slower peak perf)
# -Xms256m -Xmx512m: Set heap size to avoid resizing
ENTRYPOINT ["java", "-XX:+TieredCompilation", "-XX:TieredStopAtLevel=1", "-Xms256m", "-Xmx512m", "-jar", "app.jar"]
CMD ["prod"]
