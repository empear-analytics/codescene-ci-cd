(ns codescene-gitlab.gitlab-api
  (:require [clj-http.client :as http]))

(defn- header-with-pat [api-token]
  {"PRIVATE-TOKEN" api-token})

(defn- notes-url [url project-id merge-request-iid]
  (format "%s/api/v4/projects/%d/merge_requests/%d/notes/"
          url project-id merge-request-iid))

(defn- note-url [url project-id merge-request-iid note-id]
  (format "%s/api/v4/projects/%d/merge_requests/%d/notes/%d"
          url project-id merge-request-iid note-id))

(defn get-merge-request-notes [url api-token project-id merge-request-iid]
  (:body (http/get (notes-url url project-id merge-request-iid)
                   {:headers (header-with-pat api-token)
                    :as      :json})))

(defn get-merge-request-note [url api-token project-id merge-request-iid note-id]
  (:body (http/get (note-url url project-id merge-request-iid note-id)
                   {:headers (header-with-pat api-token)
                    :as      :json})))

(defn delete-merge-request-note [url api-token project-id merge-request-iid note-id]
  (:body (http/delete (note-url url project-id merge-request-iid note-id)
                      {:headers (header-with-pat api-token)
                       :as      :json})))

(defn create-merge-request-note [url api-token project-id merge-request-iid text]
  (:body (http/post (notes-url url project-id merge-request-iid)
                    {:headers      (header-with-pat api-token)
                     :query-params {:body text}
                     :as           :json})))

(defn update-merge-request-note [url api-token project-id merge-request-iid note-id text]
  (:body (http/put (note-url url project-id merge-request-iid note-id)
                   {:headers      (header-with-pat api-token)
                    :query-params {:body text}
                    :as           :json})))