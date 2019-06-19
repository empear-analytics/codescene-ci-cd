(ns codescene-ci-cd.github-hook
  (:require [ring.util.response :refer [content-type response]]
            [taoensso.timbre :as log]
            [codescene-ci-cd.github-validation :as github-validation]
            [codescene-ci-cd.delta-analysis :as delta-analysis]
            [codescene-ci-cd.results :as results]
            [codescene-ci-cd.utils :as utils]
            [codescene-ci-cd.github-api :as github-api]
            [clojure.string :as string]))

(def secret "C4RqayPVGbfq")

(def api-token "43aeb96b1aea2eaa80b01f2115b76a31e221ad08")

(def pull-request-actions #{"opened" "edited" "synchronize"})

(def default-invalid-response
  {:status  400
   :headers {"Content-Type" "text/plain"}
   :body    "Invalid X-Hub-Signature in request."})


; fail-on-failed-goal fail-on-declining-code-health fail-on-high-risk risk-threshold
; codescene-delta-analysis-url codescene-user codescene-password codescene-repository
; codescene-coupling-threshold-percent http-timeout

(defn- delta-analysis-config [project-id repo]
  {:fail-on-failed-goal                  true
   :fail-on-declining-code-health        true
   :fail-on-high-risk                    true
   :risk-threshold                       7
   :codescene-delta-analysis-url         (format "%s/projects/%s/delta-analysis"
                                                 (or (System/getenv "CODESCENE_URL") "http://localhost:3003")
                                                 project-id)
   :codescene-user                       (or (System/getenv "CODESCENE_USER") "bot")
   :codescene-password                   (or (System/getenv "CODESCENE_PASSWORD") "secret")
   :codescene-repository                 repo
   :codescene-coupling-threshold-percent 45
   :http-timeout                         30000})

(defn- project-id [request]
  (or (get-in request [:query-params "project_id"]) "<unknown>"))

(defn- commit-comments-url [commits-url commit-id]
  "Use a commits-url from github to construct a url for comments attached to the commit, eg:
       'https://api.github.com/repos/knorrest/analysis-target/commits{/sha}' =>
       'https://api.github.com/repos/knorrest/analysis-target/commits/aed111ed4058973a173187ece67c82d9478ea85c/comments'"
  (string/replace commits-url #"\{\/\w+\}" (format "/%s/comments" commit-id)))

(defn on-pull-request [request]
  (let [{:keys [body]} request
        project-id (project-id request)
        action (get-in body [:action])
        repo (get-in body [:repository :name])
        source-branch (get-in body [:pull_request :head :ref])
        target-branch (get-in body [:pull_request :base :ref])
        source-commit (get-in body [:pull_request :head :sha])
        comments-url (get-in body [:pull_request :comments_url])]
    (if-not (pull-request-actions action)
      (response "No action")
      (do
        (log/infof "Handle PR for commit %s from branch %s to branch %s in %s with project id %s" source-commit source-branch target-branch repo project-id)
        (let [config (delta-analysis-config project-id repo)
              results (delta-analysis/run-delta-analysis-on-commit-set config [source-commit])
              markdown (results/as-markdown [results] config)]
          (log/debugf "Decorate PR with comment using url %s" comments-url)
          (github-api/create-comment comments-url api-token markdown (:http-timeout config))
          (response "Ok"))))))

(defn- handle-commit [project-id repo commits-url commit]
  (let [commit-id (get-in commit [:id])]
    (log/infof "Handle push for commit %s in %s" commit-id repo)
    (let [config (delta-analysis-config project-id repo)
          results (delta-analysis/run-delta-analysis-on-commit-set config [commit-id])
          markdown (results/as-markdown [results] config)
          comments-url (commit-comments-url commits-url commit-id)]
      (log/debugf "Decorate commit with comment using url %s" comments-url)
      (github-api/create-comment comments-url api-token markdown (:http-timeout config)))))

(defn on-push [request]
  (let [{:keys [body]} request
        project-id (project-id request)
        repo (get-in body [:repository :name])
        commits-url (get-in body [:repository :commits_url])]
    (doseq [commit (:commits body)]
      (handle-commit project-id repo commits-url commit))
    (response "Ok")))

(defn on-unhandled [event]
  (log/debugf "Event %s is unhandled" event)
  (response "No action"))

(defn on-hook [request]
  (let [body-as-string (:body-as-string request)
        valid? (github-validation/is-valid? secret body-as-string request)]
    (if valid?
      (try
        (log/debug "Request validation OK: " valid?)
        (let [event (get-in request [:headers "x-github-event"])]
          (case event
            "pull_request" (on-pull-request request)
            "push" (on-push request)
            (on-unhandled event)))
        (catch Exception e
          (log/error "CodeScene CI/CD failed to do delta analysis:" (utils/ex->str e))
          {:status  400
           :headers {"Content-Type" "text/plain"}
           :body    "CodeScene CI/CD failed to do delta analysis."}))
      (do
        (log/warn "Request invalid! (Maybe wrong shared secret?)")
        default-invalid-response))))