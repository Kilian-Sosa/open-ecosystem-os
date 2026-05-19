# Open Ledger — Product Logic

Open Ledger is a privacy-first, self-hosted finance tracker inside Open Ecosystem OS. It is not a bank-connected product. The initial model is manual entry plus receipt/OCR-assisted entry.

## Product purpose

Open Ledger helps a person, couple, or household understand:

- where money is going
- who spent or earned it
- which habits or budget rules are being followed or broken
- which shops are cheaper for recurring products
- which receipts still need review
- how monthly income, expenses, savings, and recurring costs evolve over time

## Non-goals

Open Ledger should not include bank connections in the initial roadmap.

Avoid for MVP and early post-MVP:

- PSD2/open banking integration
- automatic bank account synchronization
- investment portfolio management
- tax-filing automation
- credit scoring
- financial advice that implies regulated advisory services

## Core sections

### Dashboard

Purpose: give an immediate overview of income, expenses, net balance, savings rate, recent transactions, active rules, receipt review queue, product-price insights, and alerts.

Primary widgets:

- monthly income
- monthly expenses
- net balance
- savings rate
- spending by category
- recent transactions
- budget rules/habits
- receipt review queue
- products and prices
- insights and alerts

### Transactions

Purpose: list, filter, add, edit, split, categorize, and export expenses and income.

Transaction fields:

- type: expense, income, transfer later
- amount
- item/title
- shop/source
- person/household member
- category
- payment method
- date/time
- status: draft, cleared, needs review, ignored
- tags
- notes
- linked receipt/file

Expense examples:

- Dinner at La Tagliatella
- Groceries at Mercadona
- Taxi to office
- Spotify Premium

Income examples:

- Monthly paycheck
- Freelance project
- Interest earned
- Asset income

### Receipts

Purpose: scan, upload, OCR, review, and confirm receipts before they become transactions.

Receipt lifecycle:

1. Receipt uploaded or scanned
2. OCR extracts raw text
3. AI/parser suggests merchant, date, total, category, person, and line items
4. User reviews extracted fields and confidence warnings
5. User confirms and creates transaction
6. Product prices and reports are updated

Statuses:

- uploaded
- processing
- needs review
- confirmed
- failed
- ignored

The system must not silently save low-confidence extractions without review.

### Budgets & Rules

Purpose: define spending limits, saving goals, and personal/household habits.

Rule types:

- amount limit: groceries under 400 EUR/month
- frequency limit: eat out no more than once per week
- percentage goal: save at least 20% of income
- merchant limit: Amazon under 100 EUR/month
- subscription limit: subscriptions under 80 EUR/month
- product-price alert: notify when a tracked item is cheaper elsewhere

Rule statuses:

- on track
- near limit
- exceeded
- at risk
- completed

### Products & Prices

Purpose: track recurring products extracted from receipts, normalize aliases, compare unit prices, and find cheaper stores.

Key ideas:

- receipt line items are stored separately from transactions
- products can have aliases because receipts use inconsistent names
- unit normalization is required before comparing prices
- comparisons should use price per roll, liter, kilogram, unit, etc.
- user can manually merge product aliases

Example:

- Product: Toilet paper
- Aliases: PAP HIGIEN, PAPEL WC, HIGIENICO 12R
- Unit: roll
- Cheapest observed store: Lidl
- Last purchase: Mercadona
- Insight: Lidl is 18% cheaper based on recent receipts

### Reports

Purpose: visualize monthly trends, categories, merchants, people, recurring vs variable expenses, product price trends, and AI-generated summaries.

Reports:

- monthly report
- category report
- store/merchant report
- product price intelligence report
- household split report
- export data as CSV/PDF

### Settings

Purpose: configure household members, categories, payment methods, recurring transactions, OCR/AI parsing, budgets/rule defaults, notifications, privacy, and localization.

Settings groups:

- household and people
- categories and tags
- payment methods/accounts
- recurring income and expenses
- receipt OCR and AI parsing
- budget/rule defaults
- notifications
- privacy and data export/delete
- localization: currency, locale, first day of week, date format

## Data privacy

Finance data is sensitive. Open Ledger must preserve the ecosystem's privacy-first principles.

Rules:

- no bank connection by default
- no external processing unless explicitly configured
- receipts, transactions, and AI parsing data stay in the user's environment by default
- local OCR/local AI should be supported when available
- user can export all finance data
- user can delete all finance data
- shared household data must be protected by permissions and audit logs

## Ecosystem integrations

Open Ledger should reuse existing platform capabilities instead of becoming a silo.

- Drive: stores receipts, invoices, and exports
- Media/OCR: extracts text from receipts and invoices
- AI Assistant: categorizes receipts, explains spending, and generates summaries
- Open Ecosystem Flows: automates receipt processing and budget/rule alerts
- Notifications: alerts for rules, review queues, and price opportunities
- Global Search: searches transactions, merchants, receipt text, products, and reports
- Activity/Audit Logs: tracks edits, confirmations, deletions, exports, and shared-household actions
- Open Pages: can receive monthly summaries or finance notes

## Suggested MVP

Open Ledger is post-MVP for the overall ecosystem unless explicitly reprioritized. Its own MVP should include:

1. manual expense creation
2. manual income creation
3. people/household members
4. categories and tags
5. payment methods
6. dashboard metrics
7. simple budgets/rules
8. receipt upload/scan entry point
9. OCR/AI extraction draft
10. review-before-save flow
11. basic reports
12. export CSV/PDF

## Post-MVP differentiation

- product alias normalization
- unit-price comparison
- store ranking by recurring products
- rule recommendations
- recurring transactions/subscription detection
- household split/reimbursements
- AI monthly finance summary
- Open Ecosystem Flows templates for receipt automation

## Events

Open Ledger should emit domain events for automation, notifications, audit, and search indexing.

Key events:

- FinanceTransactionCreated
- FinanceTransactionUpdated
- FinanceTransactionDeleted
- ReceiptUploaded
- ReceiptOcrCompleted
- ReceiptParsingSuggested
- ReceiptConfirmed
- ReceiptRejected
- BudgetCreated
- BudgetUpdated
- BudgetExceeded
- FinanceRuleCreated
- FinanceRuleEvaluated
- FinanceRuleViolated
- ProductPriceObserved
- ProductAliasMerged
- FinanceReportGenerated
- FinanceDataExported
- FinanceDataDeleted
