# Contributing to cloud-itonami-isic-3900

Contributions should preserve the actor's scope: back-office coordination only,
with CRITICAL exclusions of direct excavation/treatment-equipment control and
regulatory site-closure-certification decisions (see README.md).

- All code must be .cljc (portable Clojure, no JVM-only constructs).
- Tests must pass: kbb -M:test
- Commit messages should link to relevant ADRs or issues.

**This actor does NOT:**
- Direct excavation/treatment-equipment control (actuation).
- Regulatory site-closure-certification decisions (site-closure certification
  issuance, remediation-completion certification, regulatory closure approval).

Contributions that cross these boundaries will be rejected.
