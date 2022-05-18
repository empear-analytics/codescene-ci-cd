(ns codescene-ci-cd.codescene-api
  "Wraps the http API to CodeScene delta analysis"
  (:require [clj-http.client :as http]))

(defn run-delta-analysis-on-commits 
  [delta-analysis-url
   {:keys [user password 
           repository external-review-id delta-branch-head target-ref
           coupling-threshold-percent commits timeout] :as _options}]
  (-> (http/post delta-analysis-url
                 {:basic-auth     [user password]
                  :content-type   :json
                  :form-params    {:commits                    commits
                                   :repository                 repository
                                   :external_review_id         external-review-id
                                   :delta_branch_head          delta-branch-head
                                   :target_ref                 target-ref
                                   :coupling_threshold_percent coupling-threshold-percent}
                  :as             :json
                  :socket-timeout timeout
                  :conn-timeout   timeout})
      :body))

(comment
  (def delta-analysis-url "http://localhost:3003/projects/8/delta-analysis")
  (def user "bot")
  (def password "secret")
  (def repository "analysis-target")
  (def external-review-id "SomeId")
  (def coupling-threshold-percent 45)
  (def commits ["5ac08b3ef0cb5a882550d9e04b2d74f959639ca3"])
  (def timeout 10000)
  (run-delta-analysis-on-commits delta-analysis-url 
                                 {:user user
                                  :password password
                                  :repository repository
                                  :external-review-id external-review-id
                                  :coupling-threshold-percent coupling-threshold-percent
                                  :delta-branch-head (last commits)
                                  :target-ref "a033ea6"
                                  :commits commits 
                                  :timeout timeout}))
