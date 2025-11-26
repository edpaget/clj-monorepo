# bashketball-editor-ui

ClojureScript frontend for the Bashketball card editor.

## Prerequisites

- Node.js (v18+)
- npm

## Setup

Install npm dependencies:

```bash
npm install
```

## Development

### Start the App

Run shadow-cljs watch for the browser build:

```bash
npm run dev
# or
npx shadow-cljs watch app
```

This starts:
- Development server at http://localhost:3001
- Hot reloading
- Browser devtools preloads

The frontend expects the backend API at http://localhost:3000/graphql.

### Start Tailwind CSS (separate terminal)

```bash
npm run css:watch
```

### Connect CIDER to shadow-cljs REPL

1. Start shadow-cljs watch (if not already running):
   ```bash
   npx shadow-cljs watch app
   ```

2. In Emacs, run `M-x cider-connect-cljs`

3. Select connection type: `shadow-cljs`

4. Enter host: `localhost`

5. Enter port: `9630` (shadow-cljs nREPL default) or check the port in shadow-cljs output

6. Select build: `app`

7. Open the app in browser at http://localhost:3001 to establish the JS runtime connection

The REPL will now be connected to your browser session.

### Alternative: Start from Emacs

1. Open any `.cljs` file in the project
2. Run `M-x cider-jack-in-cljs`
3. Select `shadow-cljs`
4. Select build: `app`
5. Open http://localhost:3001 in browser

## Running Tests

Run tests once:

```bash
npm test
# or
npx shadow-cljs compile test
```

Watch mode for TDD:

```bash
npm run test:watch
# or
npx shadow-cljs watch test
```

## Production Build

```bash
npm run build
npm run css:build
```

## Project Structure

```
bashketball-editor-ui/
├── src/bashketball_editor_ui/
│   ├── core.cljs           # App entry point
│   ├── config.cljs         # Configuration
│   ├── components/ui/      # UI components (button, input)
│   ├── graphql/            # Apollo client & queries
│   ├── schemas/            # Malli card schemas
│   └── views/              # Page views
├── test/bashketball_editor_ui/
├── public/                 # Static assets & index.html
├── shadow-cljs.edn         # shadow-cljs configuration
├── deps.edn                # Clojure dependencies
└── package.json            # npm dependencies
```

## npm Scripts

| Script | Description |
|--------|-------------|
| `npm run dev` | Start shadow-cljs watch for app |
| `npm test` | Run tests once |
| `npm run test:watch` | Watch tests |
| `npm run build` | Production build |
| `npm run css:watch` | Watch Tailwind CSS |
| `npm run css:build` | Build Tailwind CSS for production |
