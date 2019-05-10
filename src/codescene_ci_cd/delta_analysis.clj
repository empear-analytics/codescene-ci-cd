(ns codescene-ci-cd.delta-analysis
  (:require [codescene-ci-cd.codescene-api :as codescene]
            [codescene-ci-cd.git-api :as git]
            [clojure.string :as string]))

(defn- commit-range [from-commit to-commit log-fn]
  (log-fn (format "Get commits from %s to %s..." from-commit to-commit))
  (git/commit-range from-commit to-commit))

(defn- run-delta-analysis-and-attach-info [config commits log-fn]
  (let [{:keys [codescene-delta-analysis-url codescene-user codescene-password codescene-repository
                codescene-coupling-threshold-percent http-timeout]} config]
    (log-fn (format "Running delta analysis on commits (%s) in repository %s." (string/join "," commits) codescene-repository))
    (-> (codescene/run-delta-analysis-on-commits codescene-delta-analysis-url codescene-user codescene-password codescene-repository
                                                 codescene-coupling-threshold-percent commits http-timeout)
       (assoc :title (first commits) :commits commits))))

(defn- run-delta-analysis-on-individual-commits [config commits log-fn]
  (log-fn (format "Starting delta analysis on %d individual commit(s)..." (count commits)))
  (mapv #(run-delta-analysis-and-attach-info config [%] log-fn) commits))

(defn- run-delta-analysis-on-branch-diff [config commits log-fn]
  (log-fn (format "Starting delta analysis on branch with %d commit(s)..." (count commits)))
  [(run-delta-analysis-and-attach-info config commits log-fn)])

(defn- fail [entry & fail-keys]
  (apply assoc entry (mapcat (fn [x] [x true]) fail-keys)))

(defn- fail-when-failed-goal [entry config log-fn]
  (let [{:keys [fail-on-failed-goal]} config
        violates-goal (get-in entry [:result :quality-gates :violates-goal])]
    (if (and fail-on-failed-goal violates-goal)
      (do
        (log-fn "Failed Quality Gate: The analysis detects a failed goal.")
        (fail entry :unstable :goal-has-failed))
      entry)))

(defn- fail-when-code-health-declines [entry config log-fn]
  (let [{:keys [fail-on-declining-code-health]} config
        degrades-in-code-health (get-in entry [:result :quality-gates :degrades-in-code-health])]
    (if (and fail-on-declining-code-health degrades-in-code-health)
      (do
        (log-fn "Failed Quality Gate: The analysis detects a decline in Code Health.")
        (fail entry :unstable :code-health-declined))
      entry)))

(defn- fail-when-at-risk-threshold [entry config log-fn]
  (let [{:keys [fail-on-high-risk risk-threshold]} config
        risk (get-in entry [:result :risk])]
    (if (and fail-on-high-risk (>= risk risk-threshold))
      (do
        (log-fn (format "Delta analysis result with risk %d: hits the risk threshold (%d)."
                          risk risk-threshold))
        (fail entry :unstable :hits-risk-threshold))
      entry)))

(defn- analyze [config from-commit to-commit delta-analysis-fn log-fn]
  (let [commits (commit-range from-commit to-commit log-fn)]
    (if (seq commits)
      (->> (delta-analysis-fn config commits log-fn)
           (map #(fail-when-failed-goal % config log-fn))
           (map #(fail-when-code-health-declines % config log-fn))
           (map #(fail-when-at-risk-threshold % config log-fn)))
      (do
        (log-fn "No new commits to analyze individually for this build.")
        []))))

(defn analyze-individual-commits-for [config log-fn]
  (let [{:keys [previous-commit current-commit base-revision]} config
        from-commit (or previous-commit base-revision)]
    (analyze config from-commit current-commit run-delta-analysis-on-individual-commits log-fn)))

(defn analyze-work-on-branch-for [config log-fn]
  (let [{:keys [current-commit base-revision]} config]
    (analyze config base-revision current-commit run-delta-analysis-on-branch-diff log-fn)))