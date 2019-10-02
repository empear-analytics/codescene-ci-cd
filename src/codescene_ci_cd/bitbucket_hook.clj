(ns codescene-ci-cd.bitbucket-hook
  (:require [ring.util.response :refer [response]]
            [taoensso.timbre :as log]
            [codescene-ci-cd.utils :as utils]
            [codescene-ci-cd.delta-analysis :as delta-analysis]
            [codescene-ci-cd.results :as results]
            [codescene-ci-cd.bitbucket-api :as bitbucket-api]))

(defn- user []
  (utils/getenv-str "CODESCENE_CI_CD_BITBUCKET_USER" "<not-set>"))

(defn- app-password []
  (utils/getenv-str "CODESCENE_CI_CD_BITBUCKET_APP_PASSWORD" "<not-set>"))

(defn- create-comment [comments-url user password markdown timeout]
  (let [comments (bitbucket-api/get-comments-and-urls comments-url user password timeout)
        comment-urls (utils/comments-and-urls->codescene-comment-urls comments)
        text (utils/with-codescene-identifier markdown)]
    (doseq [comment-url comment-urls]
      (log/debugf "Remove old Bitbucket Comment with url %s..." comment-url)
      (bitbucket-api/delete-comment comment-url user password timeout))
    (log/debug "Create new Bitbucket Comment...")
    (bitbucket-api/create-comment comments-url user password text timeout)))

(defn on-pull-request [request]
  (let [{:keys [body]} request
        project-id (utils/project-id request)
        repo (get-in body [:repository :name])
        pr-id (get-in body [:pullrequest :id])
        source-branch (get-in body [:pullrequest :source :branch :name])
        target-branch (get-in body [:pullrequest :destination :branch :name])
        comments-url (get-in body [:pullrequest :links :comments :href])
        commits-url (get-in body [:pullrequest :links :commits :href])]
    (log/infof "Handle PR %s from branch %s to branch %s in %s with project id %s" pr-id source-branch target-branch repo project-id)
    (let [config (utils/delta-analysis-config project-id repo)
          user (user)
          password (app-password)
          timeout (:http-timeout config)
          commits (bitbucket-api/get-commit-ids commits-url user password timeout)
          results (delta-analysis/run-delta-analysis-on-commit-set config commits)
          markdown (results/as-markdown [results] config)]
      (log/debugf "Decorate PR with comment using url %s" comments-url)
      (create-comment comments-url user password markdown timeout)
      (log/infof "Done with PR %s" pr-id))))

(defn- handle-commit [repo project-id commit]
  (let [commit-id (get-in commit [:hash])
        comments-url (get-in commit [:links :comments :href])]
    (log/debugf "Handle push for commit %s in %s" hash repo)
    (let [config (utils/delta-analysis-config project-id repo)
          user (user)
          password (app-password)
          timeout (:http-timeout config)
          results (delta-analysis/run-delta-analysis-on-commit-set config [commit-id])
          markdown (results/as-markdown [results] config)]
      (log/debugf "Decorate commit with comment using url %s" comments-url)
      (bitbucket-api/create-comment comments-url user password markdown timeout)
      (log/infof "Done with commit %s" commit-id))))

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

(defn- handle-event [request]
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

(defn- handle-event-async [request]
  (future
    (handle-event request)))

(defn on-hook [request]
  ;; TODO: Add some validation of the request?
  (log/debug "Handle event async!")
  (handle-event-async request)
  (response "Ok"))
