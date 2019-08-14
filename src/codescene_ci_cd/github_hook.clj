(ns codescene-ci-cd.github-hook
  "Functionality for handling webhooks from GitHook to trigger CodeScene's
  delta analysis and attach the results as comments on commits and PR:s"
  (:require [ring.util.response :refer [content-type response]]
            [taoensso.timbre :as log]
            [codescene-ci-cd.github-validation :as github-validation]
            [codescene-ci-cd.delta-analysis :as delta-analysis]
            [codescene-ci-cd.results :as results]
            [codescene-ci-cd.utils :as utils]
            [codescene-ci-cd.github-api :as github-api]
            [clojure.string :as string]))

(defn- secret []
  (utils/getenv-str "CODESCENE_CI_CD_GITHUB_SECRET" "<not-set>"))

(defn- api-token []
  (utils/getenv-str "CODESCENE_CI_CD_GITHUB_TOKEN" "<not-set>"))

(def handled-pull-request-actions #{"opened" "edited" "synchronize"})

(def default-invalid-response
  {:status  400
   :headers {"Content-Type" "text/plain"}
   :body    "Invalid X-Hub-Signature in request."})

(defn- commit-comments-url [commits-url commit-id]
  "Use a commits-url from github to construct a url for comments attached to the commit, eg:
       'https://api.github.com/repos/knorrest/analysis-target/commits{/sha}' =>
       'https://api.github.com/repos/knorrest/analysis-target/commits/aed111ed4058973a173187ece67c82d9478ea85c/comments'"
  (string/replace commits-url #"\{\/\w+\}" (format "/%s/comments" commit-id)))

(defn- create-comment [comments-url api-token markdown timeout]
  (let [comments (github-api/get-comments comments-url api-token timeout)
        comment-ids (utils/comments->codescene-comment-ids comments)
        text (utils/with-codescene-identifier markdown)]
    (doseq [comment-id comment-ids]
      (log/debugf "Remove old GitHub Comment with id %d..." comment-id)
      (github-api/delete-comment (utils/url-for-comment comment-id comments) api-token timeout))
    (log/debug "Create new GitHub Comment...")
    (github-api/create-comment comments-url api-token text timeout)))

(defn handle-pull-request [request]
  (let [{:keys [body]} request
        project-id (utils/project-id request)
        repo (get-in body [:repository :name])
        pr-number (get-in body [:pull_request :number])
        source-branch (get-in body [:pull_request :head :ref])
        target-branch (get-in body [:pull_request :base :ref])
        comments-url (get-in body [:pull_request :comments_url])
        commits-url (get-in body [:pull_request :commits_url])]
    (log/infof "Handle PR %s from branch %s to branch %s in %s with project id %s" pr-number source-branch target-branch repo project-id)
    (let [config (utils/delta-analysis-config project-id repo)
          token (api-token)
          timeout (:http-timeout config)
          commits (github-api/get-commit-ids commits-url token timeout)
          results (delta-analysis/run-delta-analysis-on-commit-set config commits)
          markdown (results/as-markdown [results] config)]
      (log/debugf "Decorate PR with comment using url %s" comments-url)
      (create-comment comments-url token markdown timeout)
      (log/infof "Done with PR"))))

(defn on-pull-request [request]
  (let [{:keys [body]} request
        action (get-in body [:action])]
    (log/info "Handle GitHub PR action: " action)
    (if (handled-pull-request-actions action)
      (handle-pull-request request))))

(defn- handle-commit [project-id repo commits-url commit]
  (let [commit-id (get-in commit [:id])]
    (log/infof "Handle push for commit %s in %s" commit-id repo)
    (let [config (utils/delta-analysis-config project-id repo)
          token (api-token)
          timeout (:http-timeout config)
          results (delta-analysis/run-delta-analysis-on-commit-set config [commit-id])
          markdown (results/as-markdown [results] config)
          comments-url (commit-comments-url commits-url commit-id)]
      (log/debugf "Decorate commit with comment using url %s" comments-url)
      (create-comment comments-url token markdown timeout)
      (log/infof "Done with commit" commit-id))))

(defn on-push [request]
  (let [{:keys [body]} request
        project-id (utils/project-id request)
        repo (get-in body [:repository :name])
        commits-url (get-in body [:repository :commits_url])]
    (doseq [commit (:commits body)]
      (handle-commit project-id repo commits-url commit))))

(defn on-unhandled [event]
  (log/infof "Event %s is unhandled" event))

(defn- run-analysis [request]
  (try
    (let [event (get-in request [:headers "x-github-event"])]
      (log/info "Handling event: " event)
      (case event
        "pull_request" (on-pull-request request)
        "push" (on-push request)
        (on-unhandled event)))
    (catch Exception e
      (log/error "CodeScene CI/CD failed to do delta analysis:" (utils/ex->str e)))))

(defn- run-analysis-async [request]
  (future
    (run-analysis request)))

(defn on-hook [request]
  (let [body-as-string (:body-as-string request)
        valid? (github-validation/is-valid? (secret) body-as-string request)]
    (if valid?
      (do
        (log/debug "Request validation OK: " valid?)
        (run-analysis-async request)
        (response "Ok"))
      (do
        (log/warn "Request invalid! (Maybe wrong shared secret?)")
        default-invalid-response))))
