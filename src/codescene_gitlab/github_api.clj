(ns codescene-gitlab.github-api
  "Wraps the github http API"
  (:require [clj-http.client :as http]
            [clojure.data.json :as json]))

(defn- header-with-pat [api-token]
  {"Authorization" (format "token %s" api-token)})

(defn- comments-url [api-url owner repo pull-request-id]
  (format "%s/repos/%s/%s/issues/%d/comments" api-url owner repo pull-request-id))

(defn- comment-url [api-url owner repo comment-id]
  (format "%s/repos/%s/%s/issues/comments/%s" api-url owner repo comment-id))

(defn get-pull-request-comments [api-url api-token owner repo pull-request-id timeout]
  (:body (http/get (comments-url api-url owner repo pull-request-id)
                   {:headers (header-with-pat api-token)
                    :as      :json
                    :socket-timeout timeout
                    :conn-timeout timeout})))

(defn delete-pull-request-comment [api-url api-token owner repo comment-id timeout]
  (:body (http/delete (comment-url api-url owner repo comment-id)
                      {:headers (header-with-pat api-token)
                       :as      :json
                       :socket-timeout timeout
                       :conn-timeout timeout})))

(defn create-pull-request-comment [api-url api-token owner repo pull-request-id text timeout]
  (:body (http/post (comments-url api-url owner repo pull-request-id)
                    {:headers      (header-with-pat api-token)
                     :body (clojure.data.json/write-str {:body text})
                     :as           :json
                     :socket-timeout timeout
                     :conn-timeout timeout})))

(defn get-pull-request-comment [api-url api-token owner repo  comment-id timeout]
  (:body (http/get (comment-url api-url owner repo comment-id)
                     {:headers      (header-with-pat api-token)
                      :as           :json
                      :socket-timeout timeout
                      :conn-timeout timeout})))

(defn update-pull-request-comment [api-url api-token owner repo comment-id text timeout]
  (:body (http/patch (comment-url api-url owner repo comment-id)
                    {:headers      (header-with-pat api-token)
                     :body (json/write-str {:body text})
                     :as           :json
                     :socket-timeout timeout
                     :conn-timeout timeout})))

(comment
  (def api-url "https://api.github.com")
  (def api-token "")
  (def owner "knorrest")
  (def repo "analysis-target")
  (get-pull-request-comments api-url api-token owner repo 1 3000)
  (get-pull-request-comment api-url api-token owner repo 488678846 3000)
  (delete-pull-request-comment api-url api-token owner repo 488824705 3000)
  (create-pull-request-comment api-url api-token owner repo 1 "HoHo" 3000)
  (update-pull-request-comment api-url api-token owner repo 488824705 "HaHa" 3000))