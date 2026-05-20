# ADR 0002 — Modular monolith first

## Status

Accepted

## Context

The ecosystem contains many possible modules, but premature microservices would slow delivery and increase operational complexity.

## Decision

Start with a modular monolith plus async workers. Extract services later when boundaries, lifecycle, load, or security justify it.

## Consequences

Positive:

- faster MVP
- easier debugging
- clearer domain discovery
- still compatible with future service extraction

Negative:

- must enforce module boundaries deliberately
- some scaling concerns may require later extraction
