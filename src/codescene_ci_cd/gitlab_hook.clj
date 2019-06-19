(ns codescene-ci-cd.gitlab-hook
  (:require [ring.util.response :refer [content-type response]]
            [taoensso.timbre :as log]))

(def secret "C4RqayPVGbfq")

(def default-invalid-response
  {:status  400
   :headers {"Content-Type" "text/plain"}
   :body    "Invalid x-gitlab-token in request."})

(defn- valid-gitlab? [secret request]
  (let [signature (get-in request [:headers "x-gitlab-token"])]
    (= signature secret)))

(defn on-mergerequest [request]
  (let [{:keys [body]} request
        repo (get-in body [:repository :name])
        source-branch (get-in body [:object_attributes :source_branch])
        target-branch (get-in body [:object_attributes :target_branch])
        source-commit (get-in body [:object_attributes :last_commit :id])
        mergerequest-url (get-in body [:object_attributes :url])]
    (log/debugf "Handle PR for commit %s on branch %s to branch %s in %s"
                source-commit source-branch target-branch repo)
    (response "Ok")))

(defn- handle-commit [repo commit]
  (let [hash (get-in commit [:id])
        commit-url (get-in commit [:url])]
    (log/debugf "Handle push for commit %s in %s"
                hash repo)))

(defn on-push [request]
  (let [{:keys [body]} request
        repo (get-in body [:repository :name])]
    (doseq [commit (:commits body)]
      (handle-commit repo commit))
    (response "Ok")))

(defn on-unhandled [event]
  (log/debugf "Event %s is unhandled" event)
  (response "No action"))

(defn on-hook [request]
  (let [valid? (valid-gitlab? secret request)]
    (if valid?
      (do
        (log/debug "Request validation OK: " valid?)
        (let [event (get-in request [:headers "x-gitlab-event"])]
          (case event
            "Merge Request Hook" (on-mergerequest request)
            "Push Hook" (on-push request)
            (on-unhandled event))))
      (do
        (log/warn "Request invalid! (Maybe wrong shared secret?)")
        default-invalid-response))))
