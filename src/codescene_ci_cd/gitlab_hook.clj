(ns codescene-ci-cd.gitlab-hook
  (:require [ring.util.response :refer [content-type response]]
            [taoensso.timbre :as log]
            [codescene-ci-cd.utils :as utils]
            [codescene-ci-cd.results :as results]
            [codescene-ci-cd.delta-analysis :as delta-analysis]
            [codescene-ci-cd.gitlab-api :as gitlab-api]))

(defn- secret []
  (utils/getenv-str "CODESCENE_CI_CD_GITLAB_SECRET" "<not-set>"))

(defn- api-token []
  (utils/getenv-str "CODESCENE_CI_CD_GITLAB_TOKEN" "<not-set>"))

(defn- api-url []
  (utils/getenv-str "CODESCENE_CI_CD_GITLAB_API_URL" "https://gitlab.com/api/v4"))

(def default-invalid-response
  {:status  400
   :headers {"Content-Type" "text/plain"}
   :body    "Invalid x-gitlab-token in request."})

(defn- valid-gitlab? [secret request]
  (let [signature (get-in request [:headers "x-gitlab-token"])]
    (= signature secret)))

(defn- create-note [api-url token project-id merge-request-iid markdown timeout]
  (let [notes (gitlab-api/get-merge-request-notes api-url token project-id merge-request-iid timeout)
        note-ids (utils/ids-to-comments->codescene-comment-ids notes)
        text (utils/with-codescene-identifier markdown)]
    (doseq [note-id note-ids]
      (log/debugf "Remove old GitLab Note with id %s..." note-id)
      (gitlab-api/delete-merge-request-note api-url token project-id merge-request-iid note-id timeout))
    (log/debug "Create new GitLab Note...")
    (gitlab-api/create-merge-request-note api-url token project-id merge-request-iid text timeout)))

(defn on-merge-request [request]
  (let [{:keys [body]} request
        cs-project-id (utils/project-id request)
        repo (get-in body [:repository :name])
        source-branch (get-in body [:object_attributes :source_branch])
        target-branch (get-in body [:object_attributes :target_branch])
        gitlab-project-id (get-in body [:project :id])
        merge-request-iid (get-in body [:object_attributes :iid])]
    (log/infof "Handle MR %d from branch %s to branch %s in %s with project id %s" merge-request-iid source-branch target-branch repo cs-project-id)
    (let [config (utils/delta-analysis-config cs-project-id repo)
          api-url (api-url)
          token (api-token)
          timeout (:http-timeout config)
          commits (gitlab-api/get-commit-ids api-url token gitlab-project-id merge-request-iid timeout)
          results (delta-analysis/run-delta-analysis-on-commit-set config commits)
          markdown (results/as-markdown [results] config)]
      (log/debugf "Decorate MR with note" )
      (create-note api-url token gitlab-project-id merge-request-iid markdown timeout)
      (log/infof "Done with MR %d" merge-request-iid))))

(defn- handle-commit [repo project-id commit gitlab-project-id]
  (let [commit-id (get-in commit [:id])]
    (log/infof "Handle push for commit %s in %s" commit-id repo)
    (let [config (utils/delta-analysis-config project-id repo)
          timeout (:http-timeout config)
          results (delta-analysis/run-delta-analysis-on-commit-set config [commit-id])
          markdown (results/as-markdown [results] config)]
      (log/debugf "Decorate commit with comment")
      (gitlab-api/create-commit-note  (api-url) (api-token) gitlab-project-id  commit-id markdown timeout)
      (log/infof "Done with commit %s" commit-id))))

(defn on-push [request]
  (let [{:keys [body]} request
        project-id (utils/project-id request)
        repo (get-in body [:repository :name])
        gitlab-project-id (get-in body [:project :id])]
    (doseq [commit (:commits body)]
      (handle-commit repo project-id commit gitlab-project-id))
    (response "Ok")))

(defn on-unhandled [event]
  (log/debugf "Event %s is unhandled" event)
  (response "No action"))

(defn- handle-event [request]
  (let [event (get-in request [:headers "x-gitlab-event"])]
    (try
      (case event
        "Merge Request Hook" (on-merge-request request)
        "Push Hook" (on-push request)
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
  (let [valid? (valid-gitlab? (secret) request)]
    (if valid?
      (do
        (log/debug "Request validation OK: " valid?)
        (handle-event-async request))
      (do
        (log/warn "Request invalid! (Maybe wrong shared secret?)")
        default-invalid-response))))
