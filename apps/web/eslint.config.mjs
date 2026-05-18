import nextVitals from "eslint-config-next/core-web-vitals";
import nextTypescript from "eslint-config-next/typescript";

const eslintConfig = [
  {
    ignores: [
      ".next/**",
      "node_modules/**",
      "coverage/**",
      "test-results/**",
      "next-env.d.ts",
      "vitest.config.mts",
    ],
  },
  ...nextVitals,
  ...nextTypescript,
];

export default eslintConfig;
