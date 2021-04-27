(ns codescene-ci-cd.delta-analysis
  (:require [codescene-ci-cd.codescene-api :as codescene]
            [codescene-ci-cd.git-api :as git]
            [clojure.string :as string]
            [taoensso.timbre :as log]))

(defn- commit-range [from-commit to-commit]
  (log/info (format "Get commits from %s to %s..." from-commit to-commit))
  (git/commit-range from-commit to-commit))

(defn- fail [entry & fail-keys]
  (apply assoc entry (mapcat (fn [x] [x true]) fail-keys)))

(defn- fail-when-failed-goal [entry config]
  (let [{:keys [fail-on-failed-goal]} config
        violates-goal (get-in entry [:result :quality-gates :violates-goal])]
    (if (and fail-on-failed-goal violates-goal)
      (do
        (log/info "Failed Quality Gate: The analysis detects a failed goal.")
        (fail entry :unstable :goal-has-failed))
      entry)))

(defn- fail-when-code-health-declines [entry config]
  (let [{:keys [fail-on-declining-code-health]} config
        degrades-in-code-health (get-in entry [:result :quality-gates :degrades-in-code-health])]
    (if (and fail-on-declining-code-health degrades-in-code-health)
      (do
        (log/info "Failed Quality Gate: The analysis detects a decline in Code Health.")
        (fail entry :unstable :code-health-declined))
      entry)))

(defn- fail-when-at-risk-threshold [entry config]
  (let [{:keys [fail-on-high-risk risk-threshold]} config
        risk (get-in entry [:result :risk])]
    (if (and fail-on-high-risk (>= risk risk-threshold))
      (do
        (log/info (format "Delta analysis result with risk %d: hits the risk threshold (%d)."
                          risk risk-threshold))
        (fail entry :unstable :hits-risk-threshold))
      entry)))

(defn- run-delta-analysis-and-attach-info [config commits]
  (let [{:keys [codescene-delta-analysis-url codescene-user codescene-password codescene-repository external-review-id
                codescene-coupling-threshold-percent http-timeout current-commit]} config]
    (log/info (format "Running delta analysis on commits (%s) in repository %s." (string/join "," commits) codescene-repository))
    (-> (codescene/run-delta-analysis-on-commits codescene-delta-analysis-url codescene-user codescene-password codescene-repository external-review-id
                                                 codescene-coupling-threshold-percent commits http-timeout current-commit)
        (assoc :title (first commits) :commits commits))))

(defn- analyze-results [config analysis-results]
  (-> analysis-results
      (fail-when-failed-goal config)
      (fail-when-code-health-declines config)
      (fail-when-at-risk-threshold config)))

(defn run-delta-analysis-on-commit-set [config commits]
  (log/info (format "Starting delta analysis on set with %d commits..." (count commits)))
  (->> commits
       (run-delta-analysis-and-attach-info config)
       (analyze-results config)))

(defn run-delta-analysis-on-commit-sets [config commit-sets]
  (log/info (format "Starting delta analysis on %d commit sets..." (count commit-sets)))
  (mapv #(run-delta-analysis-on-commit-set config %) commit-sets))

(defn analyze-individual-commits-for [config]
  (let [{:keys [previous-commit current-commit base-revision]} config
        from-commit (or previous-commit base-revision)
        commits (commit-range from-commit current-commit)
        ;; Each commit as a separate "set"
        commit-sets (map #(list %) commits)]
    (log/info (format "Starting delta analysis on %d individual commit(s)..." (count commits)))
    (run-delta-analysis-on-commit-sets config commit-sets)))

(defn analyze-work-on-branch-for [config]
  (let [{:keys [current-commit base-revision]} config
        commits (commit-range base-revision current-commit)
        ;; Just a single "set" with all commits
        commit-sets (if (seq commits) [commits] [])]
    (log/info (format "Starting delta analysis on branch with %d commit(s)..." (count commits)))
    (run-delta-analysis-on-commit-sets config commit-sets)))