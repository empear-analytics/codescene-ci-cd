(ns codescene-gitlab.core
  (:gen-class)
  (:require [clj-http.client :as http]
            [clojure.stacktrace :as stacktrace]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.java.shell :as shell]))

(defn- commit-range [from-commit to-commit]
  [from-commit to-commit]
  (->> (shell/sh "git" "log" "--pretty='%H'" (format "%s..%s" from-commit to-commit))
       :out
       (#(str/split % #"\n"))
       (map #(str/replace % #"['\"]" ""))))

(defn- run-delta-analyses-on-commits [config commits listener]
  (let [{:keys [url user password repository coupling-threshold-percent]} config]
    (listener (format "Running delta analysis on commits (%s) in repository %s." (str/join "," commits) repository))
    (-> (http/post url
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

(defn- print-result [entries config title show-commits listener]
  (listener title)
  (doseq [entry entries]
    (listener entry)))

(defn- url-parts [url]
  (let [java-url (io/as-url url)]
    {:protocol (.getProtocol java-url)
     :host (.getHost java-url)
     :port (.getPort java-url)}))

(defn- mark-as-unstable-when-at-risk-threshold [entries config listener]
  (let [{:keys [risk-threshold url]} config
        {:keys [protocol host port]} (url-parts url)]
    (doseq [entry entries]
     (let [view-url(format "%s//%s:%d%s" protocol host port (:view entry))
           risk (get-in entry [:result :risk])]
       (when (>= risk risk-threshold)
         (listener (format "Delta analysis result with risk %d hits the risk threshold (%d). Marking build as unstable (%s)."
                           risk risk-threshold view-url)))))))

(defn -main
  [& args]
  (let [config {:url                        "http://localhost:3005/projects/1/delta-analysis"
                :user                       "bot"
                :password                   "0bca8fd9-c137-47c7-9c2b-98f6fbc2cd1c"
                :repository                 "cmake-project-template"
                :coupling-threshold-percent 75
                :risk-threshold 9}
        previous-commit "aa39b593"
        current-commit "c8c5f971"
        branch "master"
        analyze-latest-individually true
        analyze-branch-diff false
        base-revision "aa39b593"
        listener clojure.pprint/pprint]
    (try
      (cond
        (and analyze-latest-individually (some? previous-commit))
        (let [commits (commit-range previous-commit current-commit)]
          (if (seq commits)
            (-> (run-delta-analyses-on-individual-commits config commits listener)
                (#(doto % (print-result config "Delta - Individual Commits" true listener)))
                (mark-as-unstable-when-at-risk-threshold config listener))
            (listener "No new commits to analyze individually for this build.")))
        (and analyze-branch-diff (some? base-revision))
        (let [commits (commit-range base-revision current-commit)]
          (when (seq commits)
            (-> (run-delta-analyses-on-branch-diff config commits branch listener)
                (#(doto % (print-result config "Delta - By Branch" false listener)))
                (mark-as-unstable-when-at-risk-threshold config listener))))
        :else (listener "No delta analysis configured."))
      (catch Exception e
        (listener "Failed to run delta analysis:")
        (listener e)))))



