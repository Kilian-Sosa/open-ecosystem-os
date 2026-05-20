# Formatting and Linting

Formatting and linting are quality gates. They are not optional polish.

## Goals

- Keep code consistent across sessions and human edits.
- Prevent style drift across apps.
- Catch basic mistakes before tests run.
- Make code review focus on behavior and architecture.

## Frontend rules

Recommended tools:

- Prettier for formatting.
- ESLint for linting.
- TypeScript strict mode.
- Stylelint only if custom CSS grows beyond Tailwind/design-token usage.

Expected commands after frontend bootstrap:

```bash
cd apps/web
pnpm format:check
pnpm lint
pnpm typecheck
pnpm test
pnpm build
```

Recommended package scripts:

```json
{
  "scripts": {
    "format": "prettier . --write",
    "format:check": "prettier . --check",
    "lint": "eslint .",
    "typecheck": "tsc --noEmit",
    "test": "vitest run",
    "test:watch": "vitest",
    "build": "next build"
  }
}
```

## Backend rules

Recommended tools:

- Maven wrapper.
- Spotless with Google Java Format.
- Maven Surefire for unit tests.
- Maven Failsafe for integration tests, if split.
- Error Prone/SpotBugs later, optional.

Expected commands after backend bootstrap:

```bash
cd apps/api
./mvnw spotless:check
./mvnw spotless:apply
./mvnw -q test
./mvnw -q verify
cd ../worker
./mvnw spotless:check
./mvnw spotless:apply
./mvnw -q test
./mvnw -q verify
```

`./mvnw verify` runs Spotless check for Java sources in the API and worker. Use
`./mvnw spotless:apply` before committing when the check reports formatting
drift.

## Markdown/YAML/JSON rules

Use Prettier where practical.

Recommended:

```bash
prettier "**/*.{md,json,yaml,yml}" --check
```

Do not run Prettier over generated files or vendored code.

## Rules

Must:

- run formatting checks after broad edits
- not reformat unrelated files unless the task is a formatting task
- not introduce one-off style conventions
- not ignore lint warnings casually
- explain any lint rule suppression

## Suppression policy

A suppression must include a short reason.

Allowed example:

```ts
// eslint-disable-next-line @typescript-eslint/no-floating-promises -- Fire-and-forget analytics event; failures are intentionally non-blocking.
```

Bad example:

```ts
// eslint-disable-next-line
```

## References

- Prettier CI docs: https://prettier.io/docs/ci
