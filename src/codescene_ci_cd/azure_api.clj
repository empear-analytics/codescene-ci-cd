(ns codescene-ci-cd.azure-api
  "Wraps the bitbucket http API"
  (:require [clj-http.client :as http]
            [clojure.data.json :as json]))

defn get-commits
  "Returns a list commits (maps)"
  [commits-url api-token timeout]
  (:body (http/get commits-url
                   {:basic-auth     ["" api-token]
                    :as             :json-strict
                    :socket-timeout timeout
                    :conn-timeout   timeout})))

(defn get-commit-ids
  "Returns a list of commit ids"
  [commits-url api-token timeout]
  (->> (get-commits commits-url api-token timeout)
       :value
       (map :commitId)))

(defn get-comments 
  "Returns tuples [content, url]"
  [threads-url api-token timeout]
  (->> (:body (http/get threads-url
                        {:basic-auth     ["" api-token]
                         :as             :json-strict
                         :socket-timeout timeout
                         :conn-timeout   timeout}))
       :value
       (mapcat :comments)
       (filter #(some? (:content %)))
       (map (fn [x] [(:content x) (get-in x [:_links :self :href])]))))

(defn delete-comment 
  "Deletes a comment, returns true if succesful"
  [comment-url api-token timeout]
  (http/delete comment-url
               {:basic-auth     ["" api-token]
                :content-type :json
                :socket-timeout timeout
                :conn-timeout   timeout})
  true)

(defn create-comment [threads-url api-token text timeout]
  "Creates comment thread and returns the comment thread id"
  (->> (:body (http/post threads-url
                         {:basic-auth     ["" api-token]
                          :body           (json/write-str {:comments [{:content text}]})
                          :content-type :json
                          :socket-timeout timeout
                          :conn-timeout   timeout}))
       :id))

(comment
  (def api-url "https://bitbucket.org/api/2.0")
  (def api-token (System/getenv "CODESCENE_CI_CD_AZURE_TOKEN"))
  (def user "knorrest")
  (def repo "test1")
  (def commits-url "https://dev.azure.com/knorrest/225f5fb0-840a-4b74-8cb9-2d619b7dc09b/_apis/git/repositories/0cfe4896-f546-4518-b798-91a927b6752d/pullRequests/1/commits")
  (get-commit-ids commits-url api-token 10000))

