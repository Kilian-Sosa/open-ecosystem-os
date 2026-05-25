# Route Map

This is the proposed Next.js route structure. It is a planning artifact until the frontend app is generated.

## Public product / portfolio routes

```txt
/                              Public landing page
/about                         About page
/apps                          Apps overview
/architecture                  Architecture overview
/architecture/infrastructure   Architecture and infrastructure blueprint
/design-system                  Design system
/self-hosting                   Self-hosting installation
/roadmap                        Roadmap
/changelog                      Changelog
/case-studies/flows             Case Study: Open Ecosystem Flows
/case-studies/open-pages        Case Study: Open Pages
/case-studies/media-ocr         Case Study: Media/OCR Pipeline
/docs                           Documentation index
```

## Authentication / onboarding routes

```txt
/login                          Login
/register                       Optional registration
/onboarding                     First-time setup / onboarding
```

## Workspace routes

```txt
/app/dashboard                  Workspace dashboard
/app/demo/invoice-automation    Seeded flagship invoice automation demo
/app/drive                      Drive / file manager
/app/pdf                        PDF editor
/app/pages                      Open Pages
/app/flows                      Open Ecosystem Flows
/app/media                      Media / OCR
/app/forms                      Forms and approvals
/app/kanban                     Kanban board
/app/search                     Global search results
/app/notifications              Notification center
/app/settings                   User/workspace settings
```

## Admin/platform routes

```txt
/admin                          Admin dashboard
/admin/system-status            System status
/admin/security                 Security settings
/admin/audit                    Activity / audit logs
/admin/backups                  Backup and restore
/admin/integrations             Integrations
/admin/apps                     App management
/admin/analytics                Advanced admin analytics
/admin/import-export            Import / export and migration
```

## Developer/open-source routes

```txt
/developer/api                  API explorer
/developer/events               Event catalog
/developer/adrs                 ADR / technical decisions
/developer/marketplace          Marketplace
/developer/plugins              Plugin developer portal
/developer/plugin-review        Plugin review/admin flow
/community                      Community forum
```

## Later/showcase routes

```txt
/app/music                      Music player
/app/video                      Video player
/billing                        Billing / subscriptions
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

## Open Ledger routes

```txt
/app/ledger                         Open Ledger dashboard
/app/ledger/transactions            Transactions
/app/ledger/receipts                Receipts and receipt review
/app/ledger/budgets-rules           Budgets and rules
/app/ledger/products-prices         Products and prices
/app/ledger/reports                 Finance reports
/app/ledger/settings                Finance settings
```

Open Ledger routes belong under the authenticated workspace route group. They should use the app shell, the Open Ledger sub-navigation, and the mobile bottom navigation pattern.
