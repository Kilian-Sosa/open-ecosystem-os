# Design Reference

This folder contains the implementation-facing design reference for Open Ecosystem OS.

The images under `mockups/` are visual references. They are not the source of truth for implementation details.
Contributors must read the written contracts first:

1. `/DESIGN.md`
2. `/AGENTS.md`
3. `/docs/development/SCREEN_SPECS.md`
4. `/docs/design/SCREEN_CATALOG.md`
5. `/docs/design/COMPONENT_INVENTORY.md`
6. the relevant mockup listed in the screen catalog

## Important rule

Do not implement screens by copying a mockup pixel by pixel. Implement them through reusable components, semantic tokens, accessibility rules, and responsive layouts.

## Folder structure

```txt
mockups/
  public/        Public site, docs, architecture, roadmap, case studies
  workspace/     Shared authenticated workspace surfaces
  apps/          End-user applications
  platform/      Admin, security, backup, system, billing, migration
  developer/     API, events, plugins, marketplace, community
  case-studies/  Public engineering case-study pages
  flows/         Journey and system flow diagrams
```
