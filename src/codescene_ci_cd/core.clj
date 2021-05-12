(ns codescene-ci-cd.core
  "Contains the entrypoint to the app, including argument parsing and validation"
  (:require [clojure.tools.cli :as cli]
            [codescene-ci-cd.delta-analysis :as delta-analysis]
            [codescene-ci-cd.merge-requests :as merge-requests]
            [clojure.java.io :as io]
            [clojure.data.json :as json]
            [taoensso.timbre :as log]
            [codescene-ci-cd.utils :as utils]
            [clojure.string :as str])
  (:gen-class))

(def ^:private cli-options
  [["-h" "--help"]
   ;; Codescene access settings
   [nil "--codescene-delta-analysis-url URL" "CodeScene Delta Analysis URL"]
   ["-u" "--codescene-user USER" "CodeScene User"]
   ["-p" "--codescene-password PWD" "CodeScene Password"]
   ["-r" "--codescene-repository REPO" "CodeScene Repository"]
   ;; Flags
   [nil "--analyze-individual-commits" "Individual Commits" :default false]
   [nil "--analyze-branch-diff" "By Branch" :default false]
   [nil "--pass-on-failed-analysis" "Pass Build on Failed Analysis" :default false]
   [nil "--fail-on-high-risk" "Fail Build on High Risk" :default false]
   [nil "--fail-on-failed-goal" "Fail Build on Failed Goals" :default false]
   [nil "--fail-on-declining-code-health" "Fail Build on Code Health Decline" :default false]
   [nil "--create-gitlab-note" "Create Note For Gitlab Merge Request" :default false]
   [nil "--create-github-comment" "Create Comment For GitHub Pull Request" :default false]
   [nil "--create-bitbucket-comment" "Create Comment For Bitbucket Pull Request" :default false]
   [nil "--create-azure-comment" "Create Comment For Azure Pull Request" :default false]
   [nil "--[no-]log-result" "Log the result (by printing)" :default true]
   ;; Analysis arguments
   [nil "--coupling-threshold-percent THRESHOLD" "Temporal Coupling Threshold (in percent)" :default 75 :parse-fn #(Integer/parseInt %)]
   [nil "--risk-threshold THRESHOLD" "Risk Threshold" :default 9 :parse-fn #(Integer/parseInt %)]
   [nil "--previous-commit SHA" "Previous Commit Id"]
   [nil "--current-commit SHA/refname" "Current Commit Id"]
   [nil "--base-revision SHA/refname" "Base Revision Id"]
   [nil "--external-review-id ID" "External Review Id"]
   ;; GitLab settings
   [nil "--gitlab-api-url URL" "GitLab API URL"]
   [nil "--gitlab-api-token TOKEN" "GitLab API Token"]
   [nil "--gitlab-project-id ID" "GitLab Project ID" :parse-fn #(Integer/parseInt %)]
   [nil "--gitlab-merge-request-iid IID" "GitLab Merge Request IID" :parse-fn #(Integer/parseInt %)]
   ;; Github settings
   [nil "--github-api-url URL" "GitHub API URL"]
   [nil "--github-api-token TOKEN" "GitHub API Token"]
   [nil "--github-owner OWNER" "GitHub Repository Owner"]
   [nil "--github-repo REPO" "GitHub Repository Name"]
   [nil "--github-pull-request-id ID" "GitHub Pull Request ID" :parse-fn #(Integer/parseInt %)]
   ;; BitBucket settings
   [nil "--bitbucket-api-url URL" "BitBucket API URL"]
   [nil "--bitbucket-user USER" "BitBucket User"]
   [nil "--bitbucket-password PASSWORD" "BitBucket Password"]
   [nil "--bitbucket-repo REPO" "BitBucket Repository Name"]
   [nil "--bitbucket-pull-request-id ID" "BitBucket Pull Request ID" :parse-fn #(Integer/parseInt %)]
   ;; Azure settings
   [nil "--azure-api-url URL" "Azure API URL"]
   [nil "--azure-api-token TOKEN" "Azure API Token"]
   [nil "--azure-project PROJECT" "Azure Project Name"]
   [nil "--azure-repo REPO" "Azure Repository Name"]
   [nil "--azure-pull-request-id ID" "Azure Pull Request ID" :parse-fn #(Integer/parseInt %)]
   ;; General settings
   [nil "--result-path FILENAME" "Path where JSON output is generated"]
   [nil "--http-timeout TIMEOUT-MS" "Timeout for http API calls" :parse-fn #(Integer/parseInt %)]])

(defn- usage [options-summary]
  (->> ["Usage: codescene-ci-cd [options]"
        "Options:"
        options-summary]
       (str/join \newline)))

(defn- error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (str/join \newline errors)))

(defn- validation-error-msg [errors]
  (str "The following validation errors occurred for your command:\n\n"
       (str/join \newline errors)))

(defn- exit [ok? msg]
  (log/info msg)
  (System/exit (if ok? 0 1)))

(defn- validate-options [options]
  (let [{:keys [analyze-individual-commits analyze-branch-diff 
                create-gitlab-note create-github-comment create-bitbucket-comment create-azure-comment
                codescene-delta-analysis-url codescene-user codescene-password codescene-repository
                previous-commit current-commit base-revision
                gitlab-api-url gitlab-api-token gitlab-project-id gitlab-merge-request-iid
                github-api-url github-api-token github-owner github-repo github-pull-request-id
                bitbucket-api-url bitbucket-user bitbucket-password bitbucket-repo bitbucket-pull-request-id
                azure-api-url azure-api-token azure-project azure-repo azure-pull-request-id]} options]
    (filter
      some?
      (concat
        (when (empty? codescene-delta-analysis-url) ["CodeScene delta analysis URL not specified"])
        (when (empty? codescene-user) ["Codescene user not specified"])
        (when (empty? codescene-password) ["Codescene password not specified"])
        (when (empty? codescene-repository) ["Codescene repository not specified"])
        (when analyze-individual-commits
          [(when (empty? current-commit) "Current commit not specified")
           (when (empty? previous-commit) "Previous commit not specified")])
        (when analyze-branch-diff
          ;; TODO: Don't report this twice...
          [(when (empty? current-commit) "Current commit not specified")
           (when (empty? base-revision) "Base revision not specified")])
        (when create-gitlab-note
          [(when (empty? gitlab-api-url) "GitLab API URL not specified")
           (when (empty? gitlab-api-token) "GitLab API token not specified")
           (when-not (some? gitlab-project-id) "GitLab Project Id not specified")
           (when-not (some? gitlab-merge-request-iid) "GitLab Merge request IID not specified")])
        (when create-github-comment
          [(when (empty? github-api-url) "GitHub API URL not specified")
           (when (empty? github-api-token) "GitHub API token not specified")
           (when (empty? github-owner) "GitHub repository owner not specified")
           (when (empty? github-repo) "GitHub repository name not specified")
           (when-not (some? github-pull-request-id) "GitHub pull request ID not specified")])
        (when create-bitbucket-comment
          [(when (empty? bitbucket-api-url) "BitBucket API URL not specified")
           (when (empty? bitbucket-user) "BitBucket user not specified")
           (when (empty? bitbucket-password) "BitBucket password not specified")
           (when (empty? bitbucket-repo) "BitBucket repository name not specified")
           (when-not (some? bitbucket-pull-request-id) "BitBucket pull request ID not specified")])
        (when create-azure-comment
          [(when (empty? azure-api-url) "Azure API URL not specified")
           (when (empty? azure-api-token) "Azure API token not specified")
           (when (empty? azure-project) "Azure project name not specified")
           (when (empty? azure-repo) "Azure repository name not specified")
           (when-not (some? azure-pull-request-id) "Azure pull request ID not specified")])))))

(defn parse-args
  [args]
  (let [{:keys [options errors summary]} (cli/parse-opts args cli-options)]
    (cond
      ;; help => exit OK with usage summary
      (:help options) {:exit-message (usage summary) :ok? true}

      ;; errors => exit with description of errors
      errors {:exit-message (error-msg errors)}
      :else (let [validation-errors (validate-options options)]
              (if (seq validation-errors)
                ;; failed custom validation => exit with usage summary
                {:exit-message (validation-error-msg validation-errors)}
                ;; success => exit with options
                {:options options})))))

(defn run-analysis [options]
  (let [{:keys [analyze-individual-commits analyze-branch-diff previous-commit base-revision]} options]
    (concat
      (when (and analyze-individual-commits (or (some? previous-commit) (some? base-revision)))
        (delta-analysis/analyze-individual-commits-for options))
      (when (and analyze-branch-diff (some? base-revision))
        (delta-analysis/analyze-work-on-branch-for options)))))
 
(defn log-result [results]
  (let [text (->> results (map #(get-in % [:result :result-as-text])) (str/join \newline))]
    (println text)))

(defn run-analysis-and-handle-result [options]
  (try
    (let [results (run-analysis options)
          success (not-any? :unstable results)]
      (when-let [result-path (:result-path options)]
        (with-open [wr (io/writer result-path)]
          (.write wr (json/write-str results))))
      (when (:create-gitlab-note options)
        (merge-requests/create-gitlab-note options results))
      (when (:create-github-comment options)
        (merge-requests/create-github-comment options results))
      (when (:create-bitbucket-comment options)
        (merge-requests/create-bitbucket-comment options results))
      (when (:create-azure-comment options)
        (merge-requests/create-azure-comment options results))
      (when (:log-result options)
        (log-result results))
      [success (if success "CodeScene delta analysis ok!" "CodeScene delta analysis detected problems!")])
    (catch Exception e
      [(:pass-on-failed-analysis options) (str "CodeScene-CI/CD couldn't perform the delta analysis:" (utils/ex->str e))])))

(defn -main [& args]
  (let [{:keys [options exit-message ok?]} (parse-args args)]
    (if exit-message
      (exit ok? exit-message)
      (let [[ok? exit-message] (run-analysis-and-handle-result options)]
        (exit ok? exit-message)))))

(comment
  (def options {:analyze-individual-commits false,
                :codescene-repository "cmake-project-template",
                :create-gitlab-note true,
                :codescene-password "",
                :codescene-delta-analysis-url "http://localhost:3005/projects/2/delta-analysis",
                :analyze-branch-diff true,
                :fail-on-declining-code-health true,
                :risk-threshold 7,
                :pass-on-failed-analysis true,
                :base-revision "origin/master",
                :coupling-threshold-percent 45,
                :gitlab-merge-request-iid 1,
                :gitlab-project-id 4,
                :gitlab-api-token "",
                :current-commit "96539487a532cadc1f9177cf4b6b1a61bad88049",
                :gitlab-api-url "http://gitlab:80/api/v4",
                :codescene-user "bot",
                :fail-on-failed-goal true,
                :fail-on-high-risk true})
  (def results (clojure.java.shell/with-sh-dir ,,,
                 (run-analysis-and-handle-result options)))

  ;; end
  )

