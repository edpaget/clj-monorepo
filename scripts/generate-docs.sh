#!/usr/bin/env bash
set -e

echo "Generating documentation for all packages..."

# Create output directory
mkdir -p docs-output

# Generate docs for exclusive-initializer
echo "Generating docs for exclusive-initializer..."
clojure -M:docs \
  --name "exclusive-initializer" \
  --version "0.1.0" \
  --source-paths exclusive-initializer/src \
  --output-path docs-output/exclusive-initializer \
  --metadata ':doc/format :markdown'

# Generate docs for db
echo "Generating docs for db..."
clojure -M:docs \
  --name "db" \
  --version "0.1.0" \
  --source-paths db/src \
  --output-path docs-output/db \
  --metadata ':doc/format :markdown'

# Generate docs for polix
echo "Generating docs for polix..."
clojure -M:docs \
  --name "polix" \
  --version "0.1.0" \
  --source-paths polix/src \
  --output-path docs-output/polix \
  --metadata ':doc/format :markdown'

# Create index page
echo "Creating index page..."
cat > docs-output/index.html << 'EOF'
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Clojure Monorepo Documentation</title>
  <style>
    body {
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, sans-serif;
      max-width: 800px;
      margin: 0 auto;
      padding: 40px 20px;
      line-height: 1.6;
      color: #333;
    }
    h1 {
      color: #2c3e50;
      border-bottom: 2px solid #3498db;
      padding-bottom: 10px;
    }
    .package-list {
      list-style: none;
      padding: 0;
    }
    .package-item {
      margin: 20px 0;
      padding: 20px;
      border: 1px solid #ddd;
      border-radius: 5px;
      background: #f9f9f9;
    }
    .package-item h2 {
      margin-top: 0;
      color: #2980b9;
    }
    .package-item a {
      color: #3498db;
      text-decoration: none;
      font-weight: bold;
    }
    .package-item a:hover {
      text-decoration: underline;
    }
    .package-description {
      color: #666;
      margin-top: 10px;
    }
  </style>
</head>
<body>
  <h1>Clojure Monorepo Documentation</h1>
  <p>Welcome to the API documentation for all packages in this monorepo.</p>

  <ul class="package-list">
    <li class="package-item">
      <h2><a href="exclusive-initializer/">exclusive-initializer</a></h2>
      <p class="package-description">Exclusive initialization utilities for Clojure applications.</p>
    </li>

    <li class="package-item">
      <h2><a href="db/">db</a></h2>
      <p class="package-description">Database utilities and helpers.</p>
    </li>

    <li class="package-item">
      <h2><a href="polix/">polix</a></h2>
      <p class="package-description">A DSL for writing declarative policies with document accessors and URI support.</p>
    </li>
  </ul>
</body>
</html>
EOF

# Create .nojekyll file
touch docs-output/.nojekyll

echo "✓ Documentation generated successfully in docs-output/"
echo "  To view locally, run: python3 -m http.server 8000 -d docs-output"
