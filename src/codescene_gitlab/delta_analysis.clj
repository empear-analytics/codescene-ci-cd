(ns codescene-gitlab.delta-analysis
  (:require [codescene-gitlab.codescene-api :as codescene]
            [codescene-gitlab.git-api :as git]))

(defn- commit-range [from-commit to-commit listener]
  (listener (format "Get commits from %s to %s..." from-commit to-commit))
  (git/commit-range from-commit to-commit))

(defn- run-delta-analysis-and-attach-info [config commits listener]
  (-> (codescene/run-delta-analysis-on-commits config commits listener)
      (assoc :title (first commits) :commits commits)))

(defn- run-delta-analysis-on-individual-commits [config commits _branch listener]
  (listener (format "Starting delta analysis on %d commit(s)..." (count commits)))
  (mapv #(run-delta-analysis-and-attach-info config [%] listener) commits))

(defn- run-delta-analysis-on-branch-diff [config commits branch listener]
  (listener (format "Running delta analysis on branch %s." branch))
  [(run-delta-analysis-and-attach-info config commits listener)])

(defn- fail [entry & fail-keys]
  (apply assoc entry (mapcat (fn [x] [x true]) fail-keys)))

(defn- fail-when-failed-goal [entry config listener]
  (let [{:keys [fail-on-failed-goal]} config
        violates-goal (get-in entry [:result :quality-gates :violates-goal])]
    (if (and fail-on-failed-goal violates-goal)
      (do
        (listener "Failed Quality Gate : the analysis detects a failed goal. Marking build as unstable.")
        (fail entry :unstable :goal-has-failed))
      entry)))

(defn- fail-when-code-health-declines [entry config listener]
  (let [{:keys [fail-on-declining-code-health]} config
        degrades-in-code-health (get-in entry [:result :quality-gates :degrades-in-code-health])]
    (if (and fail-on-declining-code-health degrades-in-code-health)
      (do
        (listener "Failed Quality Gate : the analysis detects a decline in Code Health. Marking build as unstable.")
        (fail entry :unstable :code-health-declined))
      entry)))

(defn- fail-when-at-risk-threshold [entry config listener]
  (let [{:keys [fail-on-high-risk risk-threshold]} config
        risk (get-in entry [:result :risk])]
    (if (and fail-on-high-risk (>= risk risk-threshold))
      (do
        (listener (format "Delta analysis result with risk %d: hits the risk threshold (%d). Marking build as unstable."
                          risk risk-threshold))
        (fail entry :unstable :hits-risk-threshold))
      entry)))

(defn- analyze [config from-commit to-commit branch delta-analysis-fn listener]
  (let [commits (commit-range from-commit to-commit listener)]
    (if (seq commits)
      (->> (delta-analysis-fn config commits branch listener)
           (map #(fail-when-failed-goal % config listener))
           (map #(fail-when-code-health-declines % config listener))
           (map #(fail-when-at-risk-threshold % config listener)))
      (do
        (listener "No new commits to analyze individually for this build.")
        []))))

(defn analyze-individual-commits-for [config listener]
  (let [{:keys [previous-commit current-commit base-revision branch]} config
        from-commit (or previous-commit base-revision)]
    (analyze config from-commit current-commit branch run-delta-analysis-on-individual-commits listener)))

(defn analyze-work-on-branch-for [config listener]
  (let [{:keys [current-commit base-revision branch]} config]
    (analyze config base-revision current-commit branch run-delta-analysis-on-branch-diff listener)))