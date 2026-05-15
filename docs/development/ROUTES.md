# Route Map

This is the Next.js route structure. Route priorities follow the design catalog:

- `P0`: first vertical slice / foundation
- `P1`: MVP
- `P2`: post-MVP
- `P3`: long-term/showcase

Do not scaffold or implement `P2`/`P3` routes unless a task explicitly requests them.

## Public product / portfolio routes

```txt
/                              P1  Public landing page
/about                         P2  About page
/apps                          P2  Apps overview
/architecture                   P1  Architecture overview
/architecture/infrastructure    P1  Architecture and infrastructure blueprint
/design-system                  P1  Design system
/self-hosting                   P1  Self-hosting installation
/roadmap                        P2  Roadmap
/changelog                      P2  Changelog
/case-studies/flows             P2  Case Study: Open Ecosystem Flows
/case-studies/open-pages        P2  Case Study: Open Pages
/case-studies/media-ocr         P2  Case Study: Media/OCR Pipeline
/docs                           P2  Documentation index
```

## Authentication / onboarding routes

```txt
/login                          P1  Login
/register                       P2  Optional registration
/onboarding                     P0  First-time setup / onboarding
```

## Workspace routes

```txt
/app/dashboard                  P0  Workspace dashboard
/app/drive                      P0  Drive / file manager
/app/media                      P0  Media / OCR
/app/flows                      P0  Open Ecosystem Flows
/app/notifications              P0  Notification center
/app/settings                   P1  User/workspace settings
/app/search                     P1  Global search results
/app/pdf                        P1  PDF editor
/app/pages                      P1  Open Pages
/app/forms                      P2  Forms and approvals
/app/kanban                     P2  Kanban board
```

## Admin/platform routes

```txt
/admin                          P2  Admin dashboard
/admin/system-status            P1  System status
/admin/security                 P1  Security settings
/admin/audit                    P0  Activity / audit logs
/admin/backups                  P1  Backup and restore
/admin/integrations             P2  Integrations
/admin/apps                     P2  App management
/admin/analytics                P2  Advanced admin analytics
/admin/import-export            P2  Import / export and migration
```

## Developer/open-source routes

```txt
/developer/api                  P2  API explorer
/developer/events               P2  Event catalog
/developer/adrs                 P2  ADR / technical decisions
/developer/marketplace          P3  Marketplace
/developer/plugins              P3  Plugin developer portal
/developer/plugin-review        P3  Plugin review/admin flow
/community                      P3  Community forum
```

## Later/showcase routes

```txt
/app/music                      P3  Music player
/app/video                      P3  Video player
/billing                        P3  Billing / subscriptions
```

## Next.js grouping recommendation

```txt
apps/web/src/app/
  (public)/
  (auth)/
  (workspace)/app/
  (admin)/admin/
  (developer)/developer/
```
