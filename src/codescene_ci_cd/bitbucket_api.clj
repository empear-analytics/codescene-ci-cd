(ns codescene-ci-cd.bitbucket-api
  "Wraps the bitbucket http API"
  (:require [clj-http.client :as http]))

(defn- header-with-pat [api-token]
  {"Authorization" (format "Bearer %s" api-token)})

(defn- comments-url [api-url user repo pull-request-id]
  (format "%s/repositories/%s/%s/pullrequests/%d/comments" api-url user repo pull-request-id))

(defn- comment-url [api-url user repo pull-request-id comment-id]
  (format "%s/repositories/%s/%s/pullrequests/%d/comments/%s" api-url user repo pull-request-id comment-id))

(defn get-pull-request-comments [api-url user password repo pull-request-id timeout]
  "Returns a map of id->comment"
  (->> (:body (http/get (comments-url api-url user repo pull-request-id)
                    {:basic-auth     [user password]
                     :as             :json
                     :socket-timeout timeout
                     :conn-timeout   timeout}))
       :values
       (map (fn [x] [(:id x) (get-in x [:content :raw])]))
       (into {})))

(defn delete-pull-request-comment [api-url user password repo pull-request-id comment-id timeout]
  (http/delete (comment-url api-url user repo pull-request-id comment-id)
               {:basic-auth     [user password]
                :as             :json
                :socket-timeout timeout
                :conn-timeout   timeout})
  true)

(defn create-pull-request-comment [api-url user password repo pull-request-id text timeout]
  (->> (:body (http/post (comments-url api-url user repo pull-request-id)
                         {:basic-auth     [user password]
                          ;;:body           (format "{\"content\": {\"raw\": \"%s\"}}" text)
                          :body           (clojure.data.json/write-str {:content {:raw text}})
                          :content-type :json
                          :as             :json
                          :socket-timeout timeout
                          :conn-timeout   timeout}))
       :id))

(comment
  (def api-url "https://bitbucket.org/api/2.0")
  (def api-token "")
  (def user "knorrest")
  (def repo "test1")
  (get-pull-request-comments api-url user api-token repo 1 3000)
  (delete-pull-request-comment api-url user api-token repo 1 100806278 3000)
  (create-pull-request-comment api-url user api-token repo 1 "hohoho" 3000))