(ns codescene-ci-cd.azure-api
  "Wraps the bitbucket http API"
  (:require [clj-http.client :as http]))

(defn- threads-url [api-url project repo pull-request-id]
  (format "%s/%s/_apis/git/repositories/%s/pullrequests/%s/threads" api-url project repo pull-request-id))

(defn- get-data
  ([url api-token timeout]
   (get-data url api-token {} timeout))
  ([url api-token query-params timeout]
   (let [result (http/get url
                          {:basic-auth        ["" api-token]
                           :query-params      query-params
                           :content-type      :json
                           :accept            :json
                           :as                :json-strict
                           :conn-timeout      timeout
                           :socket-timeout    timeout})]
     (:body result))))

(defn- post-data
  ([url api-token form-params timeout]
   (post-data url api-token form-params {} timeout))
  ([url api-token form-params query-params timeout]
  (let [result (http/post url
                          {:basic-auth        ["" api-token]
                           :query-params      query-params
                           :form-params       form-params
                           :content-type      :json
                           :accept            :json
                           :as                :json-strict
                           :conn-timeout      timeout
                           :socket-timeout    timeout})]
    (:body result))))

(defn- delete
  [url api-token query-params timeout]
  (let [result (http/delete url
                            {:basic-auth        ["" api-token]
                             :query-params      query-params
                             :content-type      :json
                             :accept            :json
                             :as                :json-strict
                             :conn-timeout      timeout
                             :socket-timeout    timeout})]
    (:body result)))

(defn get-commits
  "Returns a list commits (maps)"
  [commits-url api-token timeout]
  (get-data commits-url api-token timeout))

(defn get-commit-ids
  "Returns a list of commit ids"
  [commits-url api-token timeout]
  (->> (get-commits commits-url api-token timeout)
       :value
       (map :commitId)))

(defn get-comments 
  "Returns tuples [content, url]"
  [api-url api-token project repo pull-request-id timeout]
  (let [threads-url (threads-url api-url project repo pull-request-id)]
    (->> (get-data threads-url  api-token timeout)
         :value
         (mapcat :comments)
         (filter #(some? (:content %)))
         (map (fn [x] [(:content x) (get-in x [:_links :self :href])])))))

(defn delete-comment 
  "Deletes a comment, returns true if succesful"
  [comment-url api-token timeout]
  (delete comment-url api-token {:api-version "5.0"} timeout)
  true)

(defn create-comment [threads-url api-token text timeout]
  (->> (post-data threads-url api-token
                  {:comments [{:content text}]
                   :status 1}
                  {:api-version "5.0"}
                  timeout)
       :id))

(defn create-pull-request-comment
  "Creates comment thread and returns the comment thread id"
  [api-url api-token project repo pull-request-id text timeout]
  (let [threads-url (threads-url api-url project repo pull-request-id)]
    (create-comment threads-url api-token text timeout)))

(comment
  (def api-url "https://bitbucket.org/api/2.0")
  (def api-token (System/getenv "CODESCENE_CI_CD_AZURE_TOKEN"))
  (def user "knorrest")
  (def repo "test1")
  (def commits-url "https://dev.azure.com/knorrest/225f5fb0-840a-4b74-8cb9-2d619b7dc09b/_apis/git/repositories/0cfe4896-f546-4518-b798-91a927b6752d/pullRequests/1/commits")
  (get-commit-ids commits-url api-token 10000))

