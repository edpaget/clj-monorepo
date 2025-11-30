/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [
    "./src/**/*.{cljs,cljc}",
    "./public/index.html",
    // Shared UI components (relative path works for both local dev and Docker)
    "../bashketball-ui/src/**/*.{cljs,cljc}"
  ],
  theme: {
    extend: {},
  },
  plugins: [],
}
