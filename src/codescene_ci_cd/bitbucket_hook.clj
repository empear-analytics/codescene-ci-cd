(ns codescene-ci-cd.bitbucket-hook
  (:require [ring.util.response :refer [response]]
            [taoensso.timbre :as log]
            [codescene-ci-cd.utils :as utils]
            [codescene-ci-cd.delta-analysis :as delta-analysis]
            [codescene-ci-cd.results :as results]))

(defn on-pull-request [request]
  (let [{:keys [body]} request
        repo (get-in body [:repository :name])
        source-branch (get-in body [:pullrequest :source :branch :name])
        target-branch (get-in body [:pullrequest :destination :branch :name])
        source-commit (get-in body [:pullrequest :source :commit :hash])
        comment-url (get-in body [:pullrequest :links :comments :href])]
    (log/debugf "Handle PR for commit %s on branch %s to branch %s in %s"
                source-commit source-branch target-branch repo)
    (response "Ok")))

(defn- handle-commit [repo project-id commit]
  (let [hash (get-in commit [:hash])
        comment-url (get-in commit [:links :comments :href])]
    (log/debugf "Handle push for commit %s in %s"
                hash repo)
    )
  (let [commit-id (get-in commit [:hash])]
    (log/infof "Handle push for commit %s in %s" commit-id repo)
    (let [config (utils/delta-analysis-config project-id repo)
          results (delta-analysis/run-delta-analysis-on-commit-set config [commit-id])
          markdown (results/as-markdown [results] config)
          comments-url ""                                   ;
          ; (commit-comments-url commits-url commit-id)
          ]
      (log/debugf "Decorate commit with comment using url %s" comments-url)
      (comment (bitbucket-api/create-comment comments-url (api-token) markdown (:http-timeout config))))))

(defn on-push [request]
  (let [{:keys [body]} request
        project-id (utils/project-id request)
        repo (get-in body [:repository :name])
        changes (get-in body [:push :changes])]
    (doseq [change changes]
      (doseq [commit (:commits change)]
        (handle-commit repo project-id commit)))
    (response "Ok")))

(defn on-unhandled [event]
  (log/debugf "Event %s is unhandled" event)
  (response "No action"))

(defn on-hook [request]
  (let [event (get-in request [:headers "x-event-key"])
        repo (get-in request [:body :repository :name] "<unknown>")]
    (try
      (log/debugf "Handle BitBucket event %s in %s" event repo)
      (case event
        "pullrequest:created" (on-pull-request request)
        "pullrequest:updated" (on-pull-request request)
        "repo:push" (on-push request)
        (on-unhandled event))
      (catch Exception e
        (log/error "CodeScene CI/CD failed to do delta analysis:" (utils/ex->str e))
        {:status  400
         :headers {"Content-Type" "text/plain"}
         :body    "CodeScene CI/CD failed to do delta analysis."}))))
