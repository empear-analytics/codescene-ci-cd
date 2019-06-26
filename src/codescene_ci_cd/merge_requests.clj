(ns codescene-ci-cd.merge-requests
  (:require [clojure.string :as string]
            [codescene-ci-cd.github-api :as github]
            [codescene-ci-cd.bitbucket-api :as bitbucket]
            [codescene-ci-cd.results :as results]
            [codescene-ci-cd.gitlab-api :as gitlab]
            [taoensso.timbre :as log]
            [codescene-ci-cd.utils :as utils]))

(defn create-gitlab-note [options results]
  (log/info "Create GitLab Note for merge request...")
  (let [{:keys [gitlab-api-url gitlab-api-token gitlab-project-id gitlab-merge-request-iid http-timeout]} options
        notes (gitlab/get-merge-request-notes gitlab-api-url gitlab-api-token gitlab-project-id gitlab-merge-request-iid http-timeout)
        note-ids (utils/ids-to-comments->codescene-comment-ids notes)
        markdown (results/as-markdown results options)]
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
        markdown (results/as-markdown results options)]
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
        markdown (results/as-markdown results options)]
    (doseq [comment-id comment-ids]
      (log/info (format "Remove old Bitbucket Comment with id %d for merge request..." comment-id))
      (bitbucket/delete-pull-request-comment bitbucket-api-url bitbucket-user bitbucket-password bitbucket-repo bitbucket-pull-request-id comment-id http-timeout))
    (bitbucket/create-pull-request-comment bitbucket-api-url bitbucket-user bitbucket-password bitbucket-repo bitbucket-pull-request-id
                                           (utils/with-codescene-identifier markdown) http-timeout)))
