# Bank Transaction Ingestion — Research Notes

*Date: 2026-08-28. Status: research only, nothing built. Output of a brainstorming spike on dynamically ingesting bank transactions instead of manual CSV imports.*

## Goal

Automatically ingest new financial records from personal bank accounts into the funds system, replacing/complementing the manual CSV-based import flow. Target accounts: **Revolut, Salt Bank, Banca Transilvania (BT), ING Romania**. Daily sync freshness is sufficient; near-real-time is a nice-to-have.

## Key findings

### No true "streams" exist for individuals

Banks do not offer transaction streams to individuals. What exists:

- **PSD2 account-information APIs**: every EU bank must expose one, but only to licensed TPPs (eIDAS certificate, AISP license). Individuals access them via **aggregators** that hold the license. The model is consent-based *pull* (polling), not push.
- **Webhooks**: only on business products (e.g. Revolut Business API). Revolut's personal Open Banking API is TPP-only.
- **Unofficial near-real-time routes**: Android `NotificationListenerService` intercepting bank push notifications, or email/statement parsing. Free and instant but fragile (per-app/per-locale parsing, no IBANs or stable IDs, breaks silently when notification wording changes). Usable as a *fast provisional signal*, never as source of truth.

### Aggregator landscape (as of Aug 2026)

- **GoCardless Bank Account Data (ex-Nordigen)** — the classic free option — is **closed to new signups**.
- **Enable Banking** — the recommended replacement. Confirmed coverage: Revolut (~30 countries incl. RO), BT (redirect + BT Pay app switching), ING RO (redirect + HomeBank app switching). All Romanian bank flows are redirect-based SCA.
- **Salt Edge** — good RO coverage (Moldova-based, strong regional focus); usage-based pricing; historically had a free personal tier (used by Firefly III importer). Most likely aggregator to integrate Salt Bank first.
- **open-banking.io** — indie-focused, self-serve, free tier + ~€3/mo, no eIDAS needed; younger/less proven.
- **Tink, Plaid, TrueLayer, Yapily** — enterprise-oriented (contracts, minimums, eIDAS); not viable for personal use.

### Per-bank reachability

| Bank | Status |
|---|---|
| Revolut | ✅ via Enable Banking (and most aggregators); no personal direct API |
| BT | ✅ via Enable Banking / Salt Edge |
| ING RO | ✅ via Enable Banking / Salt Edge |
| Salt Bank | ⚠️ has a PSD2 TPP portal (eIDAS-only, not usable directly); not yet verified in any aggregator directory (2024 launch) — fall back to CSV import until coverage appears |

### Enable Banking cost: €0 for personal use

Enable Banking **"restricted mode"**: register an account + application without a contract; the app runs against production but can only access bank accounts *you yourself linked* in their portal. App shows "Restricted" but "Active" — this is the sanctioned personal-use path, documented by Firefly III as their GoCardless replacement. Commercial tier (custom quote, per-connected-account, monthly minimum) only matters if third parties would connect their banks.

Caveats: best-effort/no SLA; PSD2 bank-side limit of ~4 unattended calls per account per day (daily sync fits easily); SCA consent renewal every ~180 days per bank; free terms could change (Nordigen precedent) — mitigate with a provider-agnostic connector interface.

## Recommendation

**Enable Banking (restricted mode, free) as single aggregator for Revolut + BT + ING; Salt Bank via existing CSV import until aggregator coverage catches up.** Don't split across two aggregators for one bank.

Proposed shape in this codebase:

- A `bank-sync` capability in **import-service** with a provider-agnostic connector interface and an Enable Banking implementation behind it.
- Daily scheduled pull per connected account → normalize to the existing import model → dedupe on the provider transaction ID → feed the existing import → fund-service flow (Kafka).
- Consent UX (initial bank connect + ~180-day SCA renewal, clear "consent expired" surfacing) in the web client.
- Optional later phase: Android notification-listener as a real-time *provisional* record stream, confirmed/replaced by the daily aggregator sync.

## Next step (feasibility probe, before any design)

1. Create an Enable Banking account; confirm restricted mode works for an individual.
2. Link real Revolut/BT/ING accounts in their portal; inspect actual transaction payloads (IDs, booking vs value dates, counterparty data quality).
3. Ask Enable Banking whether Salt Bank is on their roadmap; ask Salt Edge whether they cover Salt Bank.

## Sources

- [Enable Banking Romania market docs](https://enablebanking.com/docs/markets/ro/)
- [Enable Banking Revolut availability](https://enablebanking.com/open-banking-apis/LT304580906)
- [Enable Banking FAQ (restricted mode, pricing)](https://enablebanking.com/docs/faq/)
- [Firefly III: Import from Enable Banking](https://docs.firefly-iii.org/tutorials/data-importer/eb/)
- [GoCardless BAD alternatives (dev.to)](https://dev.to/johnfrandsen/gocardless-bank-account-data-alternatives-what-to-use-when-signups-are-disabled-326d)
- [Salt Bank developer portal (TPP-only)](https://developer.api-oba.dev.salt.bank/perry/developer/documentation?resource=enhub-salt-bg-portal&document=docs/20-getting-started.md)
- [Salt Edge Romania coverage](https://www.saltedge.com/products/account_information/coverage/ro)
- [Fiskil Romania open banking tracker](https://www.fiskil.com/open-finance-tracker/romania)
- [Revolut Open Banking API (TPP-only)](https://developer.revolut.com/docs/api/open-banking)
