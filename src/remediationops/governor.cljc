(ns remediationops.governor
  "SiteRemediationOpsGovernor -- the independent compliance layer that
  earns the RemediationOpsAdvisor the right to commit. The advisor has
  no notion of whether a remediation site is actually registered and
  verified, whether its own proposed `:effect` secretly claims a
  direct actuation instead of a mere proposal, or whether it has
  silently drifted into a permanently out-of-scope decision area, so
  this MUST be a separate system able to *reject* a proposal and fall
  back to HOLD.

  This actor's scope is deliberately narrow -- OPERATIONS COORDINATION
  only (remediation-record logging, remediation-operation scheduling,
  contamination-concern flagging, outbound disposal coordination). It
  NEVER performs or authorizes:
    - direct excavation/treatment-equipment control (actuation)
    - regulatory site-closure-certification decisions (site-closure
      certification issuance, remediation-completion certification,
      regulatory closure approval)

  Three HARD checks, ALL permanent, un-overridable by any human
  approval:

    1. Site unverified               -- the target remediation site
                                         record (which owns every
                                         excavation/treatment/
                                         monitoring-well operation
                                         tracked under it) must exist
                                         AND be independently confirmed
                                         `:registered?`/`:verified?` in
                                         the store before ANY proposal
                                         for it may commit or even
                                         escalate. Never trusts a
                                         proposal's own claim about the
                                         site -- re-derived from the
                                         site's own store record, the
                                         same 'ground truth, not
                                         self-report' discipline every
                                         sibling actor's governor uses.
    2. Effect not :propose           -- every proposal's `:effect`
                                         MUST be `:propose`. Any other
                                         effect value is, by
                                         construction, a claim to
                                         directly actuate/commit
                                         outside governance -- HARD
                                         block, not merely
                                         low-confidence.
    3. Scope exclusion               -- ANY proposal (regardless of
                                         op) whose op, rationale,
                                         summary, citations or draft
                                         value touches excavation/
                                         treatment-equipment control or
                                         a regulatory site-closure-
                                         certification decision is a
                                         HARD, PERMANENT block -- this
                                         actor's charter excludes that
                                         territory structurally, not as
                                         a rollout milestone.
                                         Evaluated UNCONDITIONALLY on
                                         every proposal, the same
                                         'exercise the failure mode
                                         directly' discipline every
                                         sibling actor's own
                                         unconditional-evaluation
                                         checks establish. An op
                                         outside the closed four-op
                                         allowlist is the SAME failure
                                         mode (an advisor proposing
                                         something it was never
                                         authorized to propose) and is
                                         folded into this same check.

  One ESCALATE (SOFT) gate, always requiring human sign-off regardless
  of how clean the proposal otherwise is:

    - `:flag-contamination-concern` -- ALWAYS escalates, regardless of
      confidence. `remediationops.phase` independently agrees:
      `:flag-contamination-concern` is never a member of any phase's
      `:auto` set either -- two layers, not one.

  Plus the ordinary LLM-confidence-floor escalate every sibling
  actor's governor also applies."
  (:require [clojure.string :as str]
            [remediationops.store :as store]))

(def confidence-floor 0.6)

(def allowed-ops
  "The closed proposal-op allowlist -- an op outside this set is a
  scope violation by construction (see `scope-exclusion-violations`)."
  #{:log-remediation-record :schedule-remediation-operation
    :flag-contamination-concern :coordinate-disposal})

(def always-escalate-ops
  "Ops that ALWAYS require human sign-off, clean or not."
  #{:flag-contamination-concern})

(def scope-excluded-terms
  "Case-insensitive substrings that mark a proposal as touching a
  permanently out-of-scope decision area -- direct excavation/
  treatment-equipment control, or a regulatory site-closure-
  certification decision. Scanned across the proposal's op/summary/
  rationale/cites/value, never trusting the advisor's own framing of
  its intent."
  ["excavation equipment control" "excavation-equipment control" "掘削設備制御"
   "treatment equipment control" "treatment-equipment control" "処理設備制御"
   "excavator control" "掘削機制御"
   "excavator actuation" "excavation actuation" "掘削作動"
   "treatment plant actuation" "treatment-plant actuation" "処理プラント作動"
   "pump-and-treat system actuation" "pump and treat system actuation" "揚水処理システム作動"
   "site closure certification" "site-closure certification" "サイト閉鎖認証"
   "regulatory closure certification" "規制閉鎖認証"
   "remediation completion certification" "浄化完了認証"
   "site closure approval" "site-closure approval" "サイト閉鎖承認"])

;; ----------------------------- checks -----------------------------

(defn- site-unverified-violations
  "The target site must exist AND be independently
  `:registered?`/`:verified?` in the store -- never trust the
  proposal's own `:site-id` claim without a store lookup."
  [{:keys [site-id]} st]
  (let [s (store/site st site-id)]
    (when-not (and s (:registered? s) (:verified? s))
      [{:rule :site-unverified
        :detail (str site-id " は未登録または未検証のsite -- いかなる提案も進められない")}])))

(defn- effect-not-propose-violations
  "`:effect` must ALWAYS be `:propose` -- any other value is a claim
  to directly actuate/commit outside governance."
  [proposal]
  (when (not= :propose (:effect proposal))
    [{:rule :effect-not-propose
      :detail (str ":effect は :propose のみ許可されるが " (pr-str (:effect proposal)) " が提案された")}]))

(defn- text-blob
  "Flatten every advisor-authored field on a proposal into one
  lower-cased blob the scope-exclusion scan checks."
  [proposal]
  (str/lower-case (pr-str (select-keys proposal [:op :summary :rationale :cites :value]))))

(defn- scope-exclusion-violations
  "HARD, PERMANENT block: a proposal outside the closed op allowlist,
  or one whose content touches excavation/treatment-equipment-control
  or regulatory-site-closure-certification territory, regardless of
  confidence or how clean every other check is. Evaluated
  UNCONDITIONALLY on every proposal."
  [proposal]
  (let [op (:op proposal)
        blob (text-blob proposal)]
    (cond
      (not (contains? allowed-ops op))
      [{:rule :op-not-allowed
        :detail (str (pr-str op) " は許可された操作(closed allowlist)に含まれない")}]

      (some #(str/includes? blob %) scope-excluded-terms)
      [{:rule :scope-excluded
        :detail "掘削/処理設備の直接制御、または規制当局のサイト閉鎖認証判断領域に触れる提案は永久に禁止"}])))

(defn check
  "Censors a RemediationOpsAdvisor proposal against the governor
  rules. Returns {:ok? bool :violations [..] :confidence c :escalate?
  bool :high-stakes? bool :hard? bool}."
  [request _context proposal store]
  (let [site-id (or (:site-id proposal) (:site-id request))
        hard (into []
                   (concat (site-unverified-violations {:site-id site-id} store)
                           (effect-not-propose-violations proposal)
                           (scope-exclusion-violations proposal)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        always-escalate? (boolean (always-escalate-ops (:op proposal)))
        stakes? (boolean always-escalate?)
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not stakes?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? stakes?))
     :high-stakes? stakes?}))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t          :governor-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :site-id    (:site-id request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})
