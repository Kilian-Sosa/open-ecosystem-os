# Superpowers documentation

Superpowers-assisted work uses two deliberately separate locations:

- `docs/superpowers/` contains durable, human-facing project documentation that belongs in Git.
- `.superpowers/` is reserved for local/generated process state and is ignored by Git except for
  its ignore policy.

## Repository documentation

`docs/superpowers/` is organized by purpose:

- `specs/` — approved designs, constraints, amendments, and implementation handoffs.
- `plans/` — implementation and remediation plans derived from the approved specs.
- `reviews/` — durable implementation history, technical reviews, verification summaries, and
  evidence that is useful for future maintainers.

Reviews should preserve conclusions, important technical decisions, relevant commit identities,
and reproducible verification commands. Raw scanner output, transient logs, recovery snapshots,
temporary worktree state, patches, archives, and generated command output do not belong here.

## Local process state

`.superpowers/` may be used by local tooling for temporary state such as:

- recovery snapshots and backups;
- generated scanner/audit output;
- scratch task reports;
- temporary handoffs and worktree state.

That material is intentionally disposable and should not be referenced as authoritative project
documentation. If it contains knowledge worth preserving, consolidate that knowledge into the
appropriate spec, plan, or review under `docs/superpowers/`.
