# cloud-itonami-isic-3900

Open Business Blueprint for **ISIC Rev.4 3900**: Remediation activities
and other waste management services — an ISIC Wave (waste-management)
operations-coordination actor per ADR-2607121000, mirroring
`cloud-itonami-isic-3821`'s (Treatment and disposal of non-hazardous
waste) back-office regulated-environmental-services coordination
discipline.

**Maturity: `:implemented`** — RemediationOpsAdvisor ⊣
SiteRemediationOpsGovernor as a langgraph-clj StateGraph (`intake →
advise → govern → decide → commit/hold`, human-approval interrupt).
All source `.cljc` (portable to JVM / ClojureScript / GraalVM), no
JVM-only interop.

## CRITICAL: Scope Exclusions

This actor **DOES NOT** and **NEVER WILL**:

- **Direct excavation/treatment-equipment control** — actuating, operating, or otherwise directly controlling excavation, soil-treatment, or groundwater pump-and-treat equipment
- **Regulatory site-closure-certification decisions** — site-closure certification issuance, remediation-completion certification, or regulatory closure approval

This actor **only** coordinates back-office operations for a
contaminated-site remediation program (soil excavation/treatment,
groundwater pump-and-treat, monitoring wells, site-closure
verification support): remediation-record logging (excavation/
treatment-volume and contaminant-level sampling data), remediation-
operation scheduling (excavation/treatment/monitoring-well work),
contamination-concern flagging (contaminant-spread/exposure-risk,
always routed to a human), and outbound treated-material/contaminated-
soil disposal-site coordination. Every proposal the advisor drafts
carries `:effect :propose` — never a direct actuation — and
`remediationops.governor` independently re-scans every proposal's
content for the excluded scope areas above, regardless of op or
confidence.

## Operations

Closed proposal-op allowlist (`remediationops.governor/allowed-ops`), all
`:effect :propose`:

- `:log-remediation-record` — excavation/treatment-volume, contaminant-level sampling data logging
- `:schedule-remediation-operation` — excavation/treatment/monitoring-well scheduling proposal
- `:flag-contamination-concern` — surface a contaminant-spread/exposure-risk concern — **ALWAYS escalates**
- `:coordinate-disposal` — treated-material/contaminated-soil disposal-site coordination

**HARD invariants** (always `:hold`, never human-overridable):

1. **Site unverified** — the target remediation-site record (which
   owns every excavation/treatment/monitoring-well operation tracked
   under it) must exist AND be independently confirmed
   `:registered?`/`:verified?` in the store before any proposal for it
   may commit or even escalate. Never trusts a proposal's own claim
   about the site — re-derived from the site's own store record, the
   same "ground truth, not self-report" discipline every sibling
   actor's governor uses.
2. **Effect not `:propose`** — any proposal whose `:effect` is not
   `:propose` is, by construction, a claim to directly actuate outside
   governance.
3. **Scope exclusion** — any proposal (regardless of op) outside the
   closed allowlist, or whose rationale/summary/citations/value
   touches excavation/treatment-equipment control or regulatory
   site-closure-certification territory, is a permanent,
   un-overridable block. Evaluated unconditionally on every proposal.

**ESCALATE** (always human sign-off, when the governor is otherwise clean):

- `:flag-contamination-concern` — always, regardless of confidence.
- Low advisor confidence (`< 0.6`).

## Rollout phases (`remediationops.phase`)

Phase 0 (read-only) → 1 (remediation-record logging, approval-gated) →
2 (adds remediation-operation scheduling + disposal coordination,
approval-gated) → 3 (supervised auto: remediation-record/remediation-
operation/disposal-coordination may auto-commit when governor-clean
and confident).
`:flag-contamination-concern` is deliberately absent from every
phase's `:auto` set — a permanent structural fact, not a rollout
milestone still to come — matching `remediationops.governor`'s own
`always-escalate-ops` independently.

## Development

```bash
clojure -M:test   # run the full suite
clojure -M:run    # walk the demo scenarios (remediationops.sim)
clojure -M:lint    # clj-kondo
```

AGPL-3.0-or-later, forkable by any qualified operator. Part of cloud-itonami.
