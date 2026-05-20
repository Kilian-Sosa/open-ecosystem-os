# ADR 0001 — Use a monorepo

## Status

Accepted

## Context

Open Ecosystem OS includes frontend, backend, workers, infrastructure, docs, design system, and shared contracts. Starting with multiple repositories would create unnecessary coordination overhead.

## Decision

Start with one monorepo.

Deployables remain separated inside `apps/` and can produce separate Docker images.

## Consequences

Positive:

- easier vertical-slice development
- single portfolio artifact
- shared docs and contracts
- easier local development

Negative:

- requires discipline to keep module boundaries clear
- CI should avoid rebuilding everything unnecessarily
