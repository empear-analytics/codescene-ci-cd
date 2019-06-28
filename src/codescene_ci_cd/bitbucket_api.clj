(ns codescene-ci-cd.bitbucket-api
  "Wraps the bitbucket http API"
  (:require [clj-http.client :as http]
            [clojure.data.json :as json]))

(defn- header-with-pat [api-token]
  {"Authorization" (format "Bearer %s" api-token)})

(defn- comments-url [api-url user repo pull-request-id]
  (format "%s/repositories/%s/%s/pullrequests/%d/comments" api-url user repo pull-request-id))

(defn- comment-url [api-url user repo pull-request-id comment-id]
  (format "%s/repositories/%s/%s/pullrequests/%d/comments/%s" api-url user repo pull-request-id comment-id))

(defn get-comments [comments-url user password timeout]
  "Returns a map of id->comment"
  (->> (:body (http/get comments-url
                        {:basic-auth     [user password]
                         :as             :json
                         :socket-timeout timeout
                         :conn-timeout   timeout}))
       :values
       (map (fn [x] [(:id x) (get-in x [:content :raw])]))
       (into {})))

(defn get-pull-request-comments [api-url user password repo pull-request-id timeout]
  "Returns a map of id->comment"
  (let [comments-url (comments-url api-url user repo pull-request-id)]
    (get-comments comments-url user password timeout)))

(defn delete-comment [comment-url user password timeout]
  "Deletes a comment, returns true if succesful"
  (http/delete comment-url
               {:basic-auth     [user password]
                :as             :json
                :socket-timeout timeout
                :conn-timeout   timeout})
  true)

(defn delete-pull-request-comment [api-url user password repo pull-request-id comment-id timeout]
  "Deletes a comment by id, returns true if succesful"
  (let [comment-url (comment-url api-url user repo pull-request-id comment-id)]
    (delete-comment comment-url user password timeout))
  true)

(defn create-comment [comments-url user password text timeout]
  "Creates comment and returns the comment id"
  (->> (:body (http/post comments-url
                         {:basic-auth     [user password]
                          ;;:body           (format "{\"content\": {\"raw\": \"%s\"}}" text)
                          :body           (json/write-str {:content {:raw text}})
                          :content-type :json
                          :as             :json
                          :socket-timeout timeout
                          :conn-timeout   timeout}))
       :id))

(defn create-pull-request-comment [api-url user password repo pull-request-id text timeout]
  "Creates comment and returns the comment id"
  (let [comments-url (comments-url api-url user repo pull-request-id)]
    (create-comment comments-url user password text timeout)))

(comment
  (def api-url "https://bitbucket.org/api/2.0")
  (def api-token "")
  (def user "knorrest")
  (def repo "test1")
  (get-pull-request-comments api-url user api-token repo 1 3000)
  (delete-pull-request-comment api-url user api-token repo 1 100806278 3000)
  (create-pull-request-comment api-url user api-token repo 1 "hohoho" 3000))
