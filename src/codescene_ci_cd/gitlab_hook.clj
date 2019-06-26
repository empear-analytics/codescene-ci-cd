(ns codescene-ci-cd.gitlab-hook
  (:require [ring.util.response :refer [content-type response]]
            [taoensso.timbre :as log]
            [codescene-ci-cd.utils :as utils]
            [codescene-ci-cd.results :as results]
            [codescene-ci-cd.delta-analysis :as delta-analysis]
            [codescene-ci-cd.gitlab-api :as gitlab-api]))

(defn- secret []
  (utils/getenv-str "CODESCENE_CI_CD_SECRET" "<not-set>"))

(defn- api-token []
  (utils/getenv-str "CODESCENE_CI_CD_GITLAB_TOKEN" "<not-set>"))

(defn- api-url []
  "https://gitlab.com/api/v4")

(def default-invalid-response
  {:status  400
   :headers {"Content-Type" "text/plain"}
   :body    "Invalid x-gitlab-token in request."})

(defn- valid-gitlab? [secret request]
  (let [signature (get-in request [:headers "x-gitlab-token"])]
    (= signature secret)))

(defn on-merge-request [request]
  (let [{:keys [body]} request
        repo (get-in body [:repository :name])
        source-branch (get-in body [:object_attributes :source_branch])
        target-branch (get-in body [:object_attributes :target_branch])
        source-commit (get-in body [:object_attributes :last_commit :id])
        merge-request-url (get-in body [:object_attributes :url])]
    (log/debugf "Handle PR for commit %s on branch %s to branch %s in %s"
                source-commit source-branch target-branch repo)
    (response "Ok")))

(defn- handle-commit [repo project-id commit gitlab-project-id]
  (let [commit-id (get-in commit [:id])]
    (log/infof "Handle push for commit %s in %s" commit-id repo)
    (let [config (utils/delta-analysis-config project-id repo)
          results (delta-analysis/run-delta-analysis-on-commit-set config [commit-id])
          markdown (results/as-markdown [results] config)]
      (log/debugf "Decorate commit with comment")
      (gitlab-api/create-commit-note  (api-url) (api-token) gitlab-project-id  commit-id markdown (:http-timeout config)))))

(defn on-push [request]
  (let [{:keys [body]} request
        project-id (utils/project-id request)
        repo (get-in body [:repository :name])
        gitlab-project-id (get-in body [:project_id])]
    (doseq [commit (:commits body)]
      (handle-commit repo project-id commit gitlab-project-id))
    (response "Ok")))

(defn on-unhandled [event]
  (log/debugf "Event %s is unhandled" event)
  (response "No action"))

(defn on-hook [request]
  (let [valid? (valid-gitlab? secret request)]
    (if valid?
      (try
        (log/debug "Request validation OK: " valid?)
        (let [event (get-in request [:headers "x-gitlab-event"])]
          (case event
            "Merge Request Hook" (on-merge-request request)
            "Push Hook" (on-push request)
            (on-unhandled event)))
        (catch Exception e
          (log/error "CodeScene CI/CD failed to do delta analysis:" (utils/ex->str e))
          {:status  400
           :headers {"Content-Type" "text/plain"}
           :body    "CodeScene CI/CD failed to do delta analysis."}))
      (do
        (log/warn "Request invalid! (Maybe wrong shared secret?)")
        default-invalid-response))))
