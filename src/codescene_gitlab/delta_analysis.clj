(ns codescene-gitlab.delta-analysis
  (:require [clojure.java.io :as io]
            [clj-http.client :as http]
            [clojure.string :as string]
            [clojure.java.shell :as shell]))

(defn- commit-range [from-commit to-commit listener]
  [from-commit to-commit]
  (listener (format "Get commits from %s to %s..." from-commit to-commit))
  (->> (shell/sh "git" "log" "--pretty='%H'" (format "%s..%s" from-commit to-commit))
       :out
       (#(string/split % #"\n"))
       (map #(string/replace % #"['\"]" ""))))

(defn- run-delta-analyses-on-commits [config commits listener]
  (let [{:keys [delta-analysis-url user password repository coupling-threshold-percent]} config]
    (listener (format "Running delta analysis on commits (%s) in repository %s." (string/join "," commits) repository))
    (-> (http/post delta-analysis-url
                   {:basic-auth   [user password]
                    :content-type :json
                    :form-params  {:commits                    commits
                                   :repository                 repository
                                   :coupling_threshold_percent coupling-threshold-percent}
                    :as           :json})
        :body
        (assoc :title (first commits)))))

(defn- run-delta-analyses-on-individual-commits [config commits listener]
  (listener (format "Starting delta analysis on %d commit(s)..." (count commits)))
  (mapv #(run-delta-analyses-on-commits config [%] listener) commits))

(defn- run-delta-analyses-on-branch-diff [config commits branch listener]
  (listener (format "Running delta analysis on branch %s." branch))
  [(run-delta-analyses-on-commits config commits listener)])

(defn- url-parts [url]
  (let [java-url (io/as-url url)]
    {:protocol (.getProtocol java-url)
     :host     (.getHost java-url)
     :port     (.getPort java-url)}))

(defn- print-result [entries config title show-commits listener]
  (listener (format "\n%s" title))
  (let [{:keys [risk-threshold delta-analysis-url]} config
        {:keys [protocol host port]} (url-parts delta-analysis-url)]
    (doseq [entry entries]
      (let [{:keys [title view result commits]} entry
            {:keys [risk description warnings quality-gates]} result
            {:keys [degrades-in-code-health violates-goal]} quality-gates
            view-url (format "%s//%s:%d%s" protocol host port view)]
        (listener (format "Risk: %d, %s (%s)" risk title view-url))
        (when (>= risk risk-threshold)
          (listener (format "This delta hit the configured risk threshold of %s" risk-threshold)))
        (when show-commits
          (doseq [commit commits]
            (listener commit)))
        (doseq [warning warnings]
          (let [{:keys [category details]} warning]
            (listener category)
            (listener details)))))))

(defn- mark-as-unstable [entry]
  (assoc entry :unstable true))

(defn- mark-as-unstable-when-failed-goal [entry config listener]
  (let [{:keys [fail-on-failed-goal]} config
        violates-goal (get-in entry [:result :quality-gates :violates-goal])]
    (if (and fail-on-failed-goal violates-goal)
      (do
        (listener "Failed Quality Gate : the analysis detects a failed goal. Marking build as unstable.")
        (mark-as-unstable entry))
      entry)))

(defn- mark-as-unstable-when-code-health-declines [entry config listener]
  (let [{:keys [fail-on-declining-code-health]} config
        degrades-in-code-health (get-in entry [:result :quality-gates :degrades-in-code-health])]
    (if (and fail-on-declining-code-health degrades-in-code-health)
      (do
        (listener "Failed Quality Gate : the analysis detects a decline in Code Health. Marking build as unstable.")
        (mark-as-unstable entry))
      entry)))

(defn- mark-as-unstable-when-at-risk-threshold [entry config listener]
  (let [{:keys [fail-on-high-risk risk-threshold]} config
        risk (get-in entry [:result :risk])]
    (if (and fail-on-high-risk (>= risk risk-threshold))
      (do
        (listener (format "Delta analysis result with risk %d: hits the risk threshold (%d). Marking build as unstable."
                          risk risk-threshold))
        (mark-as-unstable entry))
      entry)))

(defn analyze-latest-individual-commit-for [config listener]
  (let [{:keys [previous-commit
                 current-commit
                 base-revision
                 branch]} config
         commits (commit-range previous-commit current-commit listener)]
    (if (seq commits)
      (->> (run-delta-analyses-on-individual-commits config commits listener)
          (map #(mark-as-unstable-when-failed-goal % config listener))
          (map #(mark-as-unstable-when-code-health-declines % config listener))
          (map #(mark-as-unstable-when-at-risk-threshold % config listener)))
      (do
        (listener "No new commits to analyze individually for this build.")
        []))))

(defn analyze-work-on-branch-for
  [{:keys [current-commit
           base-revision
           branch] :as config}
   listener]
  (let [commits (commit-range base-revision current-commit listener)]
    (if (seq commits)
      (-> (run-delta-analyses-on-branch-diff config commits branch listener)
          (map #(mark-as-unstable-when-failed-goal % config listener))
          (map #(mark-as-unstable-when-code-health-declines % config listener))
          (map #(mark-as-unstable-when-at-risk-threshold % config listener)))
      [])))


(comment
  (gitlab/get-merge-request-notes "http://localhost:8082/api/v4" "Q9nE8fxxs5xymf-koUD-" 4 1)
  (gitlab/create-merge-request-note "http://localhost:8082/api/v4" "Q9nE8fxxs5xymf-koUD-" 4 1 "Bla bla bla")
  (gitlab/delete-merge-request-note "http://localhost:8082/api/v4" "Q9nE8fxxs5xymf-koUD-" 4 1 10)
  (http/post "http://localhost:8082/api/v4/projects/4/merge_requests/1/notes"
             {:headers {"PRIVATE-TOKEN" "Q9nE8fxxs5xymf-koUD-"}
              :query-params {:body "ANote"}})
  (http/put "http://localhost:8082/api/v4/projects/4/merge_requests/1/notes/5"
            {:headers      {"PRIVATE-TOKEN" "Q9nE8fxxs5xymf-koUD-"}
             :query-params {:body "A Note"}})
  (http/delete "http://localhost:8082/api/v4/projects/4/merge_requests/1/notes/4"
               {:headers {"PRIVATE-TOKEN" "Q9nE8fxxs5xymf-koUD-"}}))