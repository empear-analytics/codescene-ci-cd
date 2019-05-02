(ns codescene-ci-cd.gitlab-api
  "Wraps the gitlab http API"
  (:require [clj-http.client :as http]))

  (defn- header-with-pat [api-token]
  {"PRIVATE-TOKEN" api-token})

(defn- notes-url [api-url project-id merge-request-iid]
  (format "%s/projects/%d/merge_requests/%d/notes/"
          api-url project-id merge-request-iid))

(defn- note-url [api-url project-id merge-request-iid note-id]
  (format "%s/projects/%d/merge_requests/%d/notes/%d"
          api-url project-id merge-request-iid note-id))

(defn get-merge-request-notes [api-url api-token project-id merge-request-iid timeout]
  (:body (http/get (notes-url api-url project-id merge-request-iid)
                   {:headers (header-with-pat api-token)
                    :as      :json
                    :socket-timeout timeout
                    :conn-timeout timeout})))

(defn get-merge-request-note [api-url api-token project-id merge-request-iid note-id timeout]
  (:body (http/get (note-url api-url project-id merge-request-iid note-id)
                   {:headers (header-with-pat api-token)
                    :as      :json
                    :socket-timeout timeout
                    :conn-timeout timeout})))

(defn delete-merge-request-note [api-url api-token project-id merge-request-iid note-id timeout]
  (:body (http/delete (note-url api-url project-id merge-request-iid note-id)
                      {:headers (header-with-pat api-token)
                       :as      :json
                       :socket-timeout timeout
                       :conn-timeout timeout})))

(defn create-merge-request-note [api-url api-token project-id merge-request-iid text timeout]
  (:body (http/post (notes-url api-url project-id merge-request-iid)
                    {:headers      (header-with-pat api-token)
                     :query-params {:body text}
                     :as           :json
                     :socket-timeout timeout
                     :conn-timeout timeout})))

(defn update-merge-request-note [api-url api-token project-id merge-request-iid note-id text timeout]
  (:body (http/put (note-url api-url project-id merge-request-iid note-id)
                   {:headers      (header-with-pat api-token)
                    :query-params {:body text}
                    :as           :json
                    :socket-timeout timeout
                    :conn-timeout timeout})))


(comment
  (get-merge-request-notes "http://localhost:8082/api/v4" "Q9nE8fxxs5xymf-koUD-" 4 1)
  (create-merge-request-note "http://localhost:8082/api/v4" "Q9nE8fxxs5xymf-koUD-" 4 1 "Bla bla bla")
  (delete-merge-request-note "http://localhost:8082/api/v4" "Q9nE8fxxs5xymf-koUD-" 4 1 10))