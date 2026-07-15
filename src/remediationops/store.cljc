(ns remediationops.store
  "SSoT for the ISIC-3900 remediation-activities-and-other-waste-
  management-services OPERATIONS-COORDINATION actor, behind a `Store`
  protocol so the backend is a swap, not a rewrite -- the same seam
  every `cloud-itonami-isic-*` actor in this fleet uses.

  This actor coordinates the BACK OFFICE of a contaminated-site
  remediation program (soil excavation/treatment, groundwater
  pump-and-treat, monitoring wells, site-closure verification
  support): excavation/treatment-volume and contaminant-level
  sampling-data logging, excavation/treatment/monitoring-well
  scheduling, contaminant-spread/exposure-risk concern flagging, and
  outbound treated-material/contaminated-soil disposal-site
  coordination. It never touches excavation/treatment-equipment
  control (direct actuation) or any regulatory site-closure-
  certification decision -- see `remediationops.governor`'s
  `scope-exclusion-violations`, a HARD, permanent, un-overridable
  block.

  `MemStore` -- atom of EDN. The deterministic default for dev/tests/
  demo (no deps). A `sites` directory keyed by `:site-id` STRING
  (never a keyword -- keyed consistently on the string, the same
  'string key, always' discipline every sibling actor's store uses).

  A registered/verified remediation-site record (the site under which
  every excavation/treatment/monitoring-well operation it processes is
  tracked) must exist before ANY proposal for it may ever commit or
  escalate -- `remediationops.governor`'s `site-unverified-violations`
  re-derives this from the site's own `:registered?`/`:verified?`
  fields, never from a proposal's own self-reported site claim, the
  SAME 'ground truth, not self-report' discipline every sibling
  actor's own governor uses.

  The ledger stays append-only: which site a proposal targeted, which
  operation, on what basis, committed/held/escalated and approved by
  whom is always a query over an immutable log.")

(defprotocol Store
  (site [s site-id] "Registered contaminated-site remediation program
    record, or nil.
    Site map: {:site-id .. :name .. :registered? bool :verified? bool}.")
  (all-sites [s])
  (ledger [s] "the append-only immutable decision-fact log")
  (coordination-log [s] "the append-only committed coordination-proposal history")
  (commit-record! [s record] "apply a committed proposal's record to the SSoT")
  (append-ledger! [s fact] "append one immutable decision fact")
  (with-sites [s sites] "replace/seed the site directory (map site-id->site)"))

;; ----------------------------- demo data -----------------------------

(defn demo-data
  "A small, self-contained site directory covering both the happy path
  and the governor's own hard checks, so the actor + tests run
  offline."
  []
  {:sites
   {"brownfield-site-1" {:site-id "brownfield-site-1" :name "Riverside Brownfield Remediation Site"
                          :registered? true :verified? true}
    "groundwater-site-2" {:site-id "groundwater-site-2" :name "Northgate Groundwater Pump-and-Treat Site"
                           :registered? true :verified? true}
    "closure-site-3" {:site-id "closure-site-3" :name "Old Millworks Site (closure verification lapsed)"
                       :registered? true :verified? false}}})

;; ----------------------------- MemStore (default) -----------------------------

(defrecord MemStore [a]
  Store
  (site [_ site-id] (get-in @a [:sites site-id]))
  (all-sites [_] (sort-by :site-id (vals (:sites @a))))
  (ledger [_] (:ledger @a))
  (coordination-log [_] (:coordination-log @a))
  (commit-record! [_ record]
    (swap! a update :coordination-log conj record)
    record)
  (append-ledger! [_ fact] (swap! a update :ledger conj fact) fact)
  (with-sites [s sites] (when (seq sites) (swap! a assoc :sites sites)) s))

(defn seed-db
  "A MemStore seeded with the demo site directory. The deterministic
  default."
  []
  (->MemStore (atom (assoc (demo-data) :ledger [] :coordination-log []))))

(defn mem-store
  "A MemStore seeded with an explicit `sites` map (site-id string ->
  site map) -- the primary test/dev entry point. `sites` may be empty
  (an unregistered-everywhere store)."
  [sites]
  (->MemStore (atom {:sites (or sites {}) :ledger [] :coordination-log []})))
