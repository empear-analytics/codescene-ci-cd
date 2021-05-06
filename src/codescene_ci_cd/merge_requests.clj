(ns codescene-ci-cd.merge-requests
  (:require [codescene-ci-cd.azure-api :as azure]
            [codescene-ci-cd.bitbucket-api :as bitbucket]
            [codescene-ci-cd.github-api :as github]
            [codescene-ci-cd.gitlab-api :as gitlab]
            [taoensso.timbre :as log]
            [codescene-ci-cd.utils :as utils]
            [clojure.string :as str]))

(defn- as-markdown [results]
  ;; TODO: Add a title
  (->> results (map :result-as-markdown) (str/join \newline)))

(defn create-gitlab-note [options results]
  (log/info "Create GitLab Note for merge request...")
  (let [{:keys [gitlab-api-url gitlab-api-token gitlab-project-id gitlab-merge-request-iid http-timeout]} options
        notes (gitlab/get-merge-request-notes gitlab-api-url gitlab-api-token gitlab-project-id gitlab-merge-request-iid http-timeout)
        note-ids (utils/ids-to-comments->codescene-comment-ids notes)
        markdown (as-markdown results)]
    (doseq [note-id note-ids]
      (log/info (format "Remove old GitLab Note with id %d for merge request..." note-id))
      (gitlab/delete-merge-request-note gitlab-api-url gitlab-api-token gitlab-project-id gitlab-merge-request-iid note-id http-timeout))
    (gitlab/create-merge-request-note gitlab-api-url gitlab-api-token gitlab-project-id gitlab-merge-request-iid
                                      (utils/with-codescene-identifier markdown) http-timeout)))

(defn create-github-comment [options results]
  (log/info "Create GitHub Comment for pull request...")
  (let [{:keys [github-api-url github-api-token github-owner github-repo github-pull-request-id http-timeout]} options
        comments (github/get-pull-request-comments github-api-url github-api-token github-owner github-repo github-pull-request-id http-timeout)
        comment-ids (utils/ids-to-comments->codescene-comment-ids comments)
        markdown (as-markdown results)]
    (doseq [comment-id comment-ids]
      (log/info (format "Remove old GitHub Comment with id %d for merge request..." comment-id))
      (github/delete-pull-request-comment github-api-url github-api-token github-owner github-repo comment-id http-timeout))
    (github/create-pull-request-comment github-api-url github-api-token github-owner github-repo github-pull-request-id
                                        (utils/with-codescene-identifier markdown) http-timeout)))

(defn create-bitbucket-comment [options results]
  (log/info "Create BitBucket Comment for pull request...")
  (let [{:keys [bitbucket-api-url bitbucket-user bitbucket-password bitbucket-repo bitbucket-pull-request-id http-timeout]} options
        comments (bitbucket/get-pull-request-comments bitbucket-api-url bitbucket-user bitbucket-password bitbucket-repo bitbucket-pull-request-id http-timeout)
        comment-ids (utils/ids-to-comments->codescene-comment-ids comments)
        markdown (as-markdown results)]
    (doseq [comment-id comment-ids]
      (log/info (format "Remove old Bitbucket Comment with id %d for merge request..." comment-id))
      (bitbucket/delete-pull-request-comment bitbucket-api-url bitbucket-user bitbucket-password bitbucket-repo bitbucket-pull-request-id comment-id http-timeout))
    (bitbucket/create-pull-request-comment bitbucket-api-url bitbucket-user bitbucket-password bitbucket-repo bitbucket-pull-request-id
                                           (utils/with-codescene-identifier markdown) http-timeout)))

(defn create-azure-comment [options results]
  (let [{:keys [azure-api-url azure-api-token azure-project azure-repo azure-pull-request-id http-timeout]} options
        comments (azure/get-comments azure-api-url azure-api-token azure-project azure-repo azure-pull-request-id http-timeout)
        comment-urls (utils/comments-and-urls->codescene-comment-urls comments)      
        markdown (as-markdown results)]
    (doseq [comment-url comment-urls]
      (log/debugf "Remove old Azure Comment with url %s..." comment-url)
      (let [comment-url (str comment-url "?api-version=5.1")]
        (azure/delete-comment comment-url azure-api-token http-timeout)))
    (log/debug "Create new Azure Comment...")
    (azure/create-pull-request-comment azure-api-url azure-api-token azure-project azure-repo azure-pull-request-id
                                      (utils/with-codescene-identifier markdown) http-timeout)))