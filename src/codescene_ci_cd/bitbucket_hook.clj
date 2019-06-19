(ns codescene-ci-cd.bitbucket-hook
  (:require [ring.util.response :refer [response]]
            [taoensso.timbre :as log]))

(defn on-pullrequest [request]
  (let [{:keys [body]} request
        repo (get-in body [:repository :name])
        source-branch (get-in body [:pullrequest :source :branch :name])
        target-branch (get-in body [:pullrequest :destination :branch :name])
        source-commit (get-in body [:pullrequest :source :commit :hash])
        comment-url (get-in body [:pullrequest :links :comments :href])]
    (log/debugf "Handle PR for commit %s on branch %s to branch %s in %s"
                source-commit source-branch target-branch repo)
    (response "Ok")))

(defn- handle-commit [repo commit]
  (let [hash (get-in commit [:hash])
        comment-url (get-in commit [:links :comments :href])]
    (log/debugf "Handle push for commit %s in %s"
                hash repo)))

(defn on-push [request]
  (let [{:keys [body]} request
        repo (get-in body [:repository :name])
        changes (get-in body [:push :changes])]
    (doseq [change changes]
      (doseq [commit (:commits change)]
        (handle-commit repo commit)))
    (response "Ok")))

(defn on-unhandled [event]
  (log/debugf "Event %s is unhandled" event)
  (response "No action"))

(defn on-hook [request]
  (let [event (get-in request [:headers "x-event-key"])
        repo (get-in request [:body :repository :name] "<unknown>")]
    (log/debugf "Handle BitBucket event %s in %s" event repo)
    (case event
      "pullrequest:created" (on-pullrequest request)
      "pullrequest:updated" (on-pullrequest request)
      "repo:push" (on-push request)
      (on-unhandled event))))
