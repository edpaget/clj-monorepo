#!/usr/bin/env node

/**
 * Updates index.html with hashed JS filenames for cache busting.
 *
 * Reads:
 * - public/js/libs-meta.json (esbuild metafile) to find libs-[hash].js
 * - public/js/main.*.js to find shadow-cljs hashed output
 *
 * Updates public/index.html with the actual hashed filenames.
 */

const fs = require('fs');
const path = require('path');

const publicDir = path.join(__dirname, '..', 'public');
const jsDir = path.join(publicDir, 'js');
const indexPath = path.join(publicDir, 'index.html');

// Get libs hash from esbuild metafile
const metaPath = path.join(jsDir, 'libs-meta.json');
if (!fs.existsSync(metaPath)) {
  console.error('Error: libs-meta.json not found. Run npm run js:build:prod first.');
  process.exit(1);
}

const meta = JSON.parse(fs.readFileSync(metaPath, 'utf8'));
const libsOutput = Object.keys(meta.outputs).find(k => k.includes('index-') && k.endsWith('.js'));
if (!libsOutput) {
  console.error('Error: Could not find libs hash in metafile');
  process.exit(1);
}
const libsFilename = path.basename(libsOutput);
console.log(`Found libs: ${libsFilename}`);

// Find shadow-cljs hashed main file
const jsFiles = fs.readdirSync(jsDir);
const mainFile = jsFiles.find(f => f.match(/^main\.[A-Z0-9]+\.js$/));
if (!mainFile) {
  console.error('Error: Could not find hashed main.*.js file');
  process.exit(1);
}
console.log(`Found main: ${mainFile}`);

// Read and update index.html
let html = fs.readFileSync(indexPath, 'utf8');

// Replace libs.js reference with hashed version
html = html.replace(
  /<script src="\/js\/libs\.js"><\/script>/,
  `<script src="/js/${libsFilename}"></script>`
);

// Replace main.js reference with hashed version
html = html.replace(
  /<script src="\/js\/main\.js"><\/script>/,
  `<script src="/js/${mainFile}"></script>`
);

fs.writeFileSync(indexPath, html);
console.log('Updated index.html with hashed filenames');

// Clean up old unhashed files if they exist
const oldLibs = path.join(jsDir, 'libs.js');
if (fs.existsSync(oldLibs)) {
  fs.unlinkSync(oldLibs);
  console.log('Removed old libs.js');
}

const oldMain = path.join(jsDir, 'main.js');
if (fs.existsSync(oldMain)) {
  fs.unlinkSync(oldMain);
  console.log('Removed old main.js');
}
