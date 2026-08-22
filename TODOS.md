# TODOS

## Instrument recommendation acceptance rate

**What:** Once a client exists for the description-recommendation feature (`docs/designs/transaction-description-recommendation.md`), add telemetry for whether the user actually taps/selects a recommended description, not just whether it appeared in the backend's top-5.

**Why:** The backend backtest only proves the algorithm's top-5 hit rate against historical data — it cannot prove the stated product goal ("학습이 눈에 보이는 것" / fewer taps, less typing) actually holds for a real user in a real UI. Backend hit-rate and real-world usefulness are different things; only client-side acceptance telemetry closes that gap.

**Pros:** Gives a real signal for whether the recommendation feature is worth the complexity it adds, and a concrete metric to optimize against instead of only backtest hit rate.

**Cons:** Requires client-side work (out of scope for the backend-only design that produced this TODO) and event-pipeline plumbing that doesn't exist yet.

**Context:** Raised by the outside-voice (Codex) pass during `/plan-eng-review` of the recommendation API design (2026-08-23). The reviewer's point: "backend-only work cannot demonstrate fewer taps, visible learning, or recommendation usefulness" — the backtest's top-5 hit rate is necessary but not sufficient evidence.

**Depends on / blocked by:** A client implementation of the recommendation feature must exist first (this repo is backend-only).
