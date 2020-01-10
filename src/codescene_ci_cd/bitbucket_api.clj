(ns codescene-ci-cd.bitbucket-api
  "Wraps the bitbucket http API"
  (:require [clj-http.client :as http]
            [clojure.data.json :as json]))

(defn get-commits
  "Returns a list of commits (maps)"
  [commits-url user password timeout]
  (:body (http/get commits-url
                   {:basic-auth     [user password]
                    :as             :json-strict
                    :socket-timeout timeout
                    :conn-timeout   timeout})))

(defn get-commit-ids
  "Returns a list of commit ids"
  [commits-url user password timeout]
  (->> (get-commits commits-url user password timeout)
       :values
       (map :hash)))

(defn- comments-url [api-url user repo pull-request-id]
  (format "%s/repositories/%s/%s/pullrequests/%d/comments" api-url user repo pull-request-id))

(defn- comment-url [api-url user repo pull-request-id comment-id]
  (format "%s/repositories/%s/%s/pullrequests/%d/comments/%s" api-url user repo pull-request-id comment-id))

(defn get-comments [comments-url user password timeout]
  "Returns a map of id->comment"
  (->> (:body (http/get comments-url
                        {:basic-auth     [user password]
                         :as             :json-strict
                         :socket-timeout timeout
                         :conn-timeout   timeout}))
       :values))

(defn get-comments-and-urls [comments-url user password timeout]
  (->> (get-comments comments-url user password timeout)
       (map (fn [x] [(get-in x [:content :raw]) (get-in x [:links :self :href])]))))

(defn get-pull-request-comments [api-url user password repo pull-request-id timeout]
  "Returns a map of id->comment"
  (let [comments-url (comments-url api-url user repo pull-request-id)]
    (->> (get-comments comments-url user password timeout)
         (map (fn [x] [(:id x) (get-in x [:content :raw])]))
         (into {}))))

(defn delete-comment [comment-url user password timeout]
  "Deletes a comment, returns true if succesful"
  (http/delete comment-url
               {:basic-auth     [user password]
                :as             :json-strict
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
                          :as             :json-strict
                          :socket-timeout timeout
                          :conn-timeout   timeout}))
       :id))

(defn create-pull-request-comment [api-url user password repo pull-request-id text timeout]
  "Creates comment and returns the comment id"
  (let [comments-url (comments-url api-url user repo pull-request-id)]
    (create-comment comments-url user password text timeout)))

(comment
  (def api-url "https://bitbucket.org/api/2.0")
  (def password (System/getenv "CODESCENE_CI_CD_BITBUCKET_APP_PASSWORD"))
  (def user (System/getenv "CODESCENE_CI_CD_BITBUCKET_USER"))
  (def repo "analysis-target")
  (def commits-url "https://api.bitbucket.org/2.0/repositories/knorrest/analysis-target/pullrequests/2/commits")
  (def comments-url "https://api.bitbucket.org/2.0/repositories/knorrest/analysis-target/pullrequests/2/comments")
  (get-commit-ids commits-url user password 3000)
  (get-comments-and-urls comments-url user password 3000)
  (get-pull-request-comments api-url user password repo 1 3000)
  (delete-pull-request-comment api-url user password repo 1 100806278 3000)
  (create-pull-request-comment api-url user password repo 1 "hohoho" 3000))
