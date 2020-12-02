(ns codescene-ci-cd.codescene-api
  "Wraps the http API to CodeScene delta analysis"
  (:require [clj-http.client :as http]))

(defn run-delta-analysis-on-commits [delta-analysis-url user password repository external-review-id
                                     coupling-threshold-percent commits timeout]
  (-> (http/post delta-analysis-url
                 {:basic-auth     [user password]
                  :content-type   :json
                  :form-params    {:commits                    commits
                                   :repository                 repository
                                   :external_review_id         external-review-id
                                   :coupling_threshold_percent coupling-threshold-percent
                                   :use_biomarkers             true}
                  :as             :json
                  :socket-timeout timeout
                  :conn-timeout   timeout})
      :body))

(comment
  (def delta-analysis-url "http://localhost:3003/projects/6/delta-analysis")
  (def user "bot")
  (def password "")
  (def repository "analysis-target")
  (def external-review-id "SomeId")
  (def coupling-threshold-percent 45)
  (def commits ["c258fa5"])
  (def timeout 1000)
  (run-delta-analysis-on-commits delta-analysis-url user password repository external-review-id
                                 coupling-threshold-percent commits timeout))
