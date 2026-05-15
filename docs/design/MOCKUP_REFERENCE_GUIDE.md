# Mockup Reference Guide

The mockups in `docs/design/mockups/` are visual references for hierarchy, layout, density, and responsive behavior.

## Source of truth order

Must follow this priority order:

1. `DESIGN.md` for visual tokens, layout principles, typography, radius, color, and spacing.
2. `AGENTS.md` for implementation rules and agent behavior.
3. `docs/development/SCREEN_SPECS.md` for screen requirements.
4. `docs/design/SCREEN_CATALOG.md` for mockup references and priority.
5. Mockup images for visual direction only.

## What must be inferred from mockups

- Overall layout structure.
- Desktop vs mobile responsive pattern.
- Main content hierarchy.
- Component relationships.
- The presence of side panels, bottom sheets, tables, cards, tabs, and status chips.

## What must not be inferred from mockups

- New colors outside the design tokens.
- New component variants without updating the component inventory.
- Hardcoded pixel-perfect dimensions.
- Features outside the MVP scope unless explicitly requested.
- Unapproved navigation structure.

## Required behavior

For every implemented screen:

- Reuse the existing app shells.
- Support desktop and mobile.
- Include normal, loading, empty, and error states where data-driven.
- Use semantic HTML and accessible labels.
- Use status labels, not color alone.
- Keep the interface calm, minimal, and information-rich.

## Filename rules

- Use lowercase kebab-case.
- Include the screen name and `desktop-mobile` when the mockup contains both.
- Do not overwrite a mockup without updating `SCREEN_CATALOG.md`.
- Do not commit temporary names like `imagegen.png`.
