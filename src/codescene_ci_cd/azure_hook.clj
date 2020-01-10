(ns codescene-ci-cd.azure-hook
  (:require [ring.util.response :refer [response]]
            [taoensso.timbre :as log]
            [codescene-ci-cd.utils :as utils]
            [codescene-ci-cd.delta-analysis :as delta-analysis]
            [codescene-ci-cd.results :as results]
            [codescene-ci-cd.azure-api :as azure-api]))

(defn- api-token []
  (utils/getenv-str "CODESCENE_CI_CD_AZURE_TOKEN" "<not-set>"))

(defn- create-comment [threads-url api-token markdown timeout]
  (let [comments (azure-api/get-comments threads-url api-token timeout)
        comment-urls (utils/comments-and-urls->codescene-comment-urls comments)
        text (utils/with-codescene-identifier markdown)]
    (doseq [comment-url comment-urls]
      (log/debugf "Remove old Azure Comment with url %s..." comment-url)
      (let [comment-url (str comment-url "?api-version=5.1")]
        (azure-api/delete-comment comment-url api-token timeout)))
    (log/debug "Create new Azure Comment...")
    (azure-api/create-comment threads-url api-token text timeout)))

(defn on-pull-request [request]
  (let [{:keys [body]} request
        project-id (utils/project-id request)
        repo (get-in body [:resource :repository :name])
        pr-id (get-in body [:resource :pullRequestId])
        source-branch (get-in body [:resource :sourceRefName])
        target-branch (get-in body [:resource :targetRefName])
        pull-request-url (get-in body [:resource :url])
        commits-url (str pull-request-url "/commits")
        threads-url (str pull-request-url "/threads?api-version=5.1")]
    (log/infof "Handle PR %s from branch %s to branch %s in %s with project id %s"
               pr-id source-branch target-branch repo project-id)
    (let [config (utils/delta-analysis-config project-id repo)
          token (api-token)
          timeout (:http-timeout config)
          commits (azure-api/get-commit-ids commits-url token timeout)
          results (delta-analysis/run-delta-analysis-on-commit-set config commits)
          markdown (results/as-markdown [results] config)]
      (log/debugf "Decorate PR with comment using url %s" threads-url)
      (create-comment threads-url token markdown timeout)
      (log/infof "Done with PR"))))

(defn on-unhandled [event]
  (log/debugf "Event %s is unhandled" event)
  (response "No action"))

(defn- handle-event [request]
  (let [event (get-in request [:body :eventType])
        repo (get-in request [:body :resource :repository :name] "<unknown>")]
    (try
      (log/debugf "Handle Azure event %s in %s" event repo)
      (case event
        "git.pullrequest.created" (on-pull-request request)
        "git.pullrequest.updated" (on-pull-request request)
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
  ;; TODO: Validation of the request?
  ;; (custom authentication, project id?)
  (log/debug "Handle event async!")
  (handle-event-async request)
  (response "Ok"))

