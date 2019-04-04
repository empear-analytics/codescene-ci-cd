(ns codescene-gitlab.gitlab-api
  (:require [clj-http.client :as http]))

(defn- header-with-pat [api-token]
  {"PRIVATE-TOKEN" api-token})

(defn- notes-url [api-url project-id merge-request-iid]
  (format "%s/projects/%d/merge_requests/%d/notes/"
          api-url project-id merge-request-iid))

(defn- note-url [api-url project-id merge-request-iid note-id]
  (format "%s/projects/%d/merge_requests/%d/notes/%d"
          api-url project-id merge-request-iid note-id))

(defn get-merge-request-notes [api-url api-token project-id merge-request-iid]
  (:body (http/get (notes-url api-url project-id merge-request-iid)
                   {:headers (header-with-pat api-token)
                    :as      :json})))

(defn get-merge-request-note [api-url api-token project-id merge-request-iid note-id]
  (:body (http/get (note-url api-url project-id merge-request-iid note-id)
                   {:headers (header-with-pat api-token)
                    :as      :json})))

(defn delete-merge-request-note [api-url api-token project-id merge-request-iid note-id]
  (:body (http/delete (note-url api-url project-id merge-request-iid note-id)
                      {:headers (header-with-pat api-token)
                       :as      :json})))

(defn create-merge-request-note [api-url api-token project-id merge-request-iid text]
  (:body (http/post (notes-url api-url project-id merge-request-iid)
                    {:headers      (header-with-pat api-token)
                     :query-params {:body text}
                     :as           :json})))

(defn update-merge-request-note [api-url api-token project-id merge-request-iid note-id text]
  (:body (http/put (note-url api-url project-id merge-request-iid note-id)
                   {:headers      (header-with-pat api-token)
                    :query-params {:body text}
                    :as           :json})))


(comment
  (get-merge-request-notes "http://localhost:8082/api/v4" "Q9nE8fxxs5xymf-koUD-" 4 1)
  (create-merge-request-note "http://localhost:8082/api/v4" "Q9nE8fxxs5xymf-koUD-" 4 1 "Bla bla bla")
  (delete-merge-request-note "http://localhost:8082/api/v4" "Q9nE8fxxs5xymf-koUD-" 4 1 10))