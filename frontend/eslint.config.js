import js from '@eslint/js'
import globals from 'globals'
import jsxA11y from 'eslint-plugin-jsx-a11y'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'
import tseslint from 'typescript-eslint'
import { defineConfig, globalIgnores } from 'eslint/config'

export default defineConfig([
  // `mockServiceWorker.js` é gerado pelo MSW e já traz `/* eslint-disable */`;
  // ignoramos o arquivo inteiro para não acusar a diretiva ociosa.
  globalIgnores(['dist', 'public/mockServiceWorker.js']),
  {
    files: ['**/*.{ts,tsx}'],
    extends: [
      js.configs.recommended,
      tseslint.configs.recommended,
      reactHooks.configs.flat.recommended,
      reactRefresh.configs.vite,
      // Acessibilidade estática no JSX (issue #113): rótulos, roles, alt,
      // handlers de teclado. O axe no E2E (`e2e/a11y.spec.ts`) cobre o que só
      // aparece renderizado, como contraste.
      jsxA11y.flatConfigs.recommended,
    ],
    languageOptions: {
      globals: globals.browser,
    },
  },
])
