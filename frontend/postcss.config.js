// postcss.config.js — Tailwind v4 (CSS-first) PostCSS pipeline. D-381.
//
// Rendered by: /new-project P5 (frontend-skeleton) → frontend/postcss.config.js
//
// Tailwind v4 ships its engine as a PostCSS plugin (@tailwindcss/postcss).
// There is NO classic tailwind.config.js — the design tokens live in the
// `@theme` block of theme.css (consumed via styles.css). See
// standards/design-system-tokens.md.
module.exports = {
  plugins: {
    '@tailwindcss/postcss': {},
  },
};
