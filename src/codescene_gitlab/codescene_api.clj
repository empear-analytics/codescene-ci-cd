(ns codescene-gitlab.codescene-api
  (:require [clojure.string :as string]
            [clj-http.client :as http]))

(defn run-delta-analysis-on-commits [config commits listener]
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
        (#(doto % clojure.pprint/pprint))
        )))