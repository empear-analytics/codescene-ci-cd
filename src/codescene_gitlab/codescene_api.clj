(ns codescene-gitlab.codescene-api
  "Wraps the http API to CodeScene delta analysis"
  (:require [clojure.string :as string]
            [clj-http.client :as http]))

(defn run-delta-analysis-on-commits [config commits log-fn]
  (let [{:keys [delta-analysis-url user password repository coupling-threshold-percent http-timeout]} config]
    (log-fn (format "Running delta analysis on commits (%s) in repository %s." (string/join "," commits) repository))
    (-> (http/post delta-analysis-url
                   {:basic-auth   [user password]
                    :content-type :json
                    :form-params  {:commits                    commits
                                   :repository                 repository
                                   :coupling_threshold_percent coupling-threshold-percent}
                    :as           :json
                    :socket-timeout http-timeout
                    :conn-timeout http-timeout})
        :body)))

(comment
  (def config
   {:use-biomarkers                true,
    :repository                    "codescene-plugin",
    :password                      "0bca8fd9-c137-47c7-9c2b-98f6fbc2cd1c",
    :delta-analysis-url            "http://localhost:3003/projects/3/delta-analysis",
    :coupling-threshold-percent    45,
    :user                          "bot"
    :http-timeout 1000})
  (run-delta-analysis-on-commits config ["9e3c2fbs"] println))
