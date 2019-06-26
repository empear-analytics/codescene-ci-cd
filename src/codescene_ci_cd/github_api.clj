(ns codescene-ci-cd.github-api
  "Wraps the github http API"
  (:require [clj-http.client :as http]
            [clojure.data.json :as json]))

(defn- header-with-pat [api-token]
  {"Authorization" (format "token %s" api-token)})

(defn- comments-url [api-url owner repo pull-request-id]
  (format "%s/repos/%s/%s/issues/%d/comments" api-url owner repo pull-request-id))

(defn- comment-url [api-url owner repo comment-id]
  (format "%s/repos/%s/%s/issues/comments/%s" api-url owner repo comment-id))

(defn get-comments [comments-url api-token timeout]
  "Returns a list of comments"
  (-> (:body (http/get comments-url
                        {:headers        (header-with-pat api-token)
                         :accept         :json
                         :socket-timeout timeout
                         :conn-timeout   timeout}))
      (clojure.data.json/read-str :key-fn keyword)))

(defn get-commits [commits-url api-token timeout]
  "Returns a list commits (maps)"
  (-> (:body (http/get commits-url
                        {:headers        (header-with-pat api-token)
                         :accept         :json
                         :socket-timeout timeout
                         :conn-timeout   timeout}))
      (clojure.data.json/read-str :key-fn keyword)))

(defn get-commit-ids [commits-url api-token timeout]
  "Returns a list of commit ids"
  (->> (get-commits commits-url api-token timeout)
      (map :sha)))

(defn get-pull-request-comments [api-url api-token owner repo pull-request-id timeout]
  "Returns a map of id->comment"
  (let [comments-url (comments-url api-url owner repo pull-request-id)]
    (get-comments comments-url api-token timeout)))

(defn delete-comment [comment-url api-token timeout]
  "Deletes a comment, returns true if succesful"
  (:body (http/delete comment-url
                      {:headers        (header-with-pat api-token)
                       :as             :json
                       :socket-timeout timeout
                       :conn-timeout   timeout}))
  true)

(defn delete-pull-request-comment [api-url api-token owner repo comment-id timeout]
  "Deletes a comment by id, returns true if succesful"
  (let [comment-url (comment-url api-url owner repo comment-id)]
    (delete-comment comment-url api-token timeout))
  true)

(defn create-comment [comments-url api-token text timeout]
  "Creates comment and returns the comment id"
  (->> (:body (http/post comments-url
                         {:headers        (header-with-pat api-token)
                          :body           (clojure.data.json/write-str {:body text})
                          :as             :json
                          :socket-timeout timeout
                          :conn-timeout   timeout}))
       :id))

(defn create-pull-request-comment [api-url api-token owner repo pull-request-id text timeout]
  "Creates comment and returns the comment id"
  (let [comments-url (comments-url api-url owner repo pull-request-id)]
    (create-comment comments-url api-token text timeout)))

(defn get-pull-request-comment [api-url api-token owner repo comment-id timeout]
  "Gets a comment text by id"
  (->> (:body (http/get (comment-url api-url owner repo comment-id)
                        {:headers        (header-with-pat api-token)
                         :as             :json
                         :socket-timeout timeout
                         :conn-timeout   timeout}))
       :body))

(defn update-pull-request-comment [api-url api-token owner repo comment-id text timeout]
  "Updates a comment text by id, returns true if succesful"
  (:body (http/patch (comment-url api-url owner repo comment-id)
                     {:headers        (header-with-pat api-token)
                      :body           (json/write-str {:body text})
                      :as             :json
                      :socket-timeout timeout
                      :conn-timeout   timeout}))
  true)

(comment
  (def api-url "https://api.github.com")
  (def api-token "")
  (def owner "knorrest")
  (def repo "analysis-target")
  (get-pull-request-comments api-url api-token owner repo 4 3000)
  (get-pull-request-comment api-url api-token owner repo 488999956 3000)
  (delete-pull-request-comment api-url api-token owner repo 488678846 3000)
  (create-pull-request-comment api-url api-token owner repo 1 "HoHo" 3000)
  (update-pull-request-comment api-url api-token owner repo 488999956 "HaHa" 3000))