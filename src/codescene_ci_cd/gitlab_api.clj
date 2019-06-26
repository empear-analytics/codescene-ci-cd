(ns codescene-ci-cd.gitlab-api
  "Wraps the gitlab http API"
  (:require [clj-http.client :as http]))

  (defn- header-with-pat [api-token]
  {"PRIVATE-TOKEN" api-token})

(defn- notes-url [api-url project-id merge-request-iid]
  (format "%s/projects/%d/merge_requests/%d/notes/"
          api-url project-id merge-request-iid))

(defn- commit-notes-url [api-url project-id commit-sha]
  (format "%s/projects/%d/repository/commits/%s/comments/"
          api-url project-id commit-sha))

(defn- note-url [api-url project-id merge-request-iid note-id]
  (format "%s/projects/%d/merge_requests/%d/notes/%d"
          api-url project-id merge-request-iid note-id))

(defn get-notes [notes-url api-token timeout]
  "Returns a map of id->comment"
  (->> (:body (http/get notes-url
                        {:headers        (header-with-pat api-token)
                         :as             :json
                         :socket-timeout timeout
                         :conn-timeout   timeout}))
       (map (fn [x] [(:id x) (:body x)]))
       (into {})))

(defn get-merge-request-notes [api-url api-token project-id merge-request-iid timeout]
  "Returns a map of id->comment"
  (let [notes-url (notes-url api-url project-id merge-request-iid)]
    (get-notes notes-url api-token timeout)))

(defn get-merge-request-note [api-url api-token project-id merge-request-iid note-id timeout]
  "Gets a comment text by id"
  (->> (:body (http/get (note-url api-url project-id merge-request-iid note-id)
                    {:headers        (header-with-pat api-token)
                     :as             :json
                     :socket-timeout timeout
                     :conn-timeout   timeout}))
       :body))

(defn delete-note [note-url api-token timeout]
  "Deletes a comment, returns true if succesful"
  (:body (http/delete note-url
                      {:headers (header-with-pat api-token)
                       :as      :json
                       :socket-timeout timeout
                       :conn-timeout timeout}))
  true)

(defn delete-merge-request-note [api-url api-token project-id merge-request-iid note-id timeout]
  "Deletes a comment by id, returns true if succesful"
  (let [note-url (note-url api-url project-id merge-request-iid note-id)]
    (delete-note note-url api-token timeout))
  true)

(defn create-note [notes-url api-token text timeout]
  "Creates comment and returns the comment id"
  (->> (:body (http/post notes-url
                         {:headers        (header-with-pat api-token)
                          :query-params   {:body text}
                          :as             :json
                          :socket-timeout timeout
                          :conn-timeout   timeout}))
       :id))

(defn create-merge-request-note [api-url api-token project-id merge-request-iid text timeout]
  "Creates comment and returns the comment id"
  (let [notes-url (notes-url api-url project-id merge-request-iid)]
    (create-note notes-url api-token text timeout)))

(defn create-commit-note [api-url api-token project-id commit-sha text timeout]
  "Creates comment and returns the comment id"
  (let [commit-notes-url (commit-notes-url api-url project-id commit-sha)]
    (create-note notes-url api-token text timeout)))

(defn update-merge-request-note [api-url api-token project-id merge-request-iid note-id text timeout]
  "Updates a comment text by id, returns true if succesful"
  (:body (http/put (note-url api-url project-id merge-request-iid note-id)
                   {:headers      (header-with-pat api-token)
                    :query-params {:body text}
                    :as           :json
                    :socket-timeout timeout
                    :conn-timeout timeout}))
  true)


(comment
  (def api-url "https://gitlab.com/api/v4")
  (def api-token "qQ7GM2tp397Qx4Sid-yz")
  (def project-id 12148632)
  (get-merge-request-notes api-url api-token project-id 1 3000)
  (create-merge-request-note api-url api-token project-id 1 "Bla bla bla" 3000)
  (update-merge-request-note api-url api-token project-id 1 166512857 "Bla bla blo" 3000)
  (get-merge-request-note api-url api-token project-id 1 166512857 3000)
  (delete-merge-request-note api-url api-token project-id 1 166512857 3000))