# ADR 0003 — PostgreSQL + JSONB first for flexible data

## Status

Accepted

## Context

The product needs flexible structures such as pages, workflow definitions, form schemas, theme definitions, and plugin manifests. MongoDB is a possible fit, but adding it in the MVP increases operational complexity.

## Decision

Use PostgreSQL as the main data store and JSONB for flexible document-shaped structures in the MVP.

Revisit MongoDB if flexible document workloads become large, independent, or performance-critical.

## Consequences

Positive:

- simpler MVP infrastructure
- transactional consistency
- clear data ownership
- still demonstrates flexible data modeling

Negative:

- less explicit NoSQL exposure in MVP
- may need migration if document workloads grow significantly
