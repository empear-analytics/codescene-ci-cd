(ns codescene-ci-cd.bitbucket-api
  "Wraps the bitbucket http API"
  (:require [clj-http.client :as http]))

(defn- header-with-pat [api-token]
  {"PRIVATE-TOKEN" api-token})

(defn- comments-url [api-url owner repo pull-request-id]
  (format "%s/repositories/%s/%s/pullrequests/%d/comments" api-url owner repo pull-request-id))

(defn- comment-url [api-url owner repo pull-request-id comment-id]
  (format "%s/repositories/%s/%s/pullrequests/%d/comments/%s" api-url owner repo pull-request-id comment-id))

(defn get-pull-request-notes [api-url api-token owner repo pull-request-id timeout]
  (:body (http/get (comments-url api-url owner repo pull-request-id)
                   {:headers (header-with-pat api-token)
                    :as      :json
                    :socket-timeout timeout
                    :conn-timeout timeout})))

(defn delete-pull-request-note [api-url api-token owner repo pull-request-id comment-id timeout]
  (:body (http/delete (comment-url api-url owner repo pull-request-id comment-id)
                      {:headers (header-with-pat api-token)
                       :as      :json
                       :socket-timeout timeout
                       :conn-timeout timeout})))

(defn create-pull-request-note [api-url api-token owner repo  pull-request-id comment-id text timeout]
  (:body (http/post (comment-url api-url owner repo pull-request-id comment-id)
                    {:headers      (header-with-pat api-token)
                     :query-params {:body text}
                     :as           :json
                     :socket-timeout timeout
                     :conn-timeout timeout})))

(defn update-pull-request-note [api-url api-token owner repo  pull-request-id comment-id text timeout]
  (:body (http/put (comment-url api-url owner repo pull-request-id comment-id)
                     {:headers      (header-with-pat api-token)
                      :query-params {:body text}
                      :as           :json
                      :socket-timeout timeout
                      :conn-timeout timeout})))

(comment
  (get-pull-request-notes "https://knorrest.atlassian.com" "" "" "" 1 1000))