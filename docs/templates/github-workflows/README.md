# GitHub Workflow Templates

These files are templates. Do not copy them into `.github/workflows/` until the commands they call exist and pass locally.

Activation process:

1. Bootstrap the relevant app skeleton.
2. Verify local commands from `docs/development/TEST_COMMANDS.md`.
3. Copy the matching workflow template into `.github/workflows/`.
4. Open a PR that activates only that workflow.
5. Fix the workflow until it passes.
6. Make it a required check only after it is stable.

Why templates instead of active workflows?

The starter repository contains placeholders. Active workflows would fail before `apps/web/package.json`, backend Maven wrappers, lockfiles, or actual test commands exist. Storybook and Playwright workflows remain deferred until their configuration and smoke suites are added.
