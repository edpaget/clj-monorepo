/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [
    "./src/**/*.{cljs,cljc}",
    "./public/index.html",
    "../bashketball-ui/src/**/*.{cljs,cljc}"
  ],
  theme: {
    extend: {},
  },
  plugins: [],
}
