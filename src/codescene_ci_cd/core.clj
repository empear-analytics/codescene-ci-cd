(ns codescene-ci-cd.core
  "Contains the entrypoint to the app, including argument parsing and validation"
  (:require [clojure.string :as string]
            [clojure.tools.cli :as cli]
            [codescene-ci-cd.delta-analysis :as delta-analysis]
            [codescene-ci-cd.results :as results]
            [codescene-ci-cd.merge-requests :as merge-requests]
            [clojure.java.io :as io]
            [clojure.data.json :as json])
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
   [nil "--[no-]log-result" "Log the result (by printing)" :default true]
   ;; Analysis arguments
   [nil "--coupling-threshold-percent THRESHOLD" "Temporal Coupling Threshold (in percent)" :default 75 :parse-fn #(Integer/parseInt %)]
   [nil "--risk-threshold THRESHOLD" "Risk Threshold" :default 9 :parse-fn #(Integer/parseInt %)]
   [nil "--previous-commit SHA" "Previous Commit Id"]
   [nil "--current-commit SHA" "Current Commit Id"]
   [nil "--base-revision SHA" "Base Revision Id"]
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
   ;; General settings
   [nil "--result-path FILENAME" "Path where JSON output is generated"]
   [nil "--http-timeout TIMEOUT-MS" "Timeout for http API calls" :parse-fn #(Integer/parseInt %)]])

(defn- usage [options-summary]
  (->> ["Usage: codescene-ci-cd [options]"
        "Options:"
        options-summary]
       (string/join \newline)))

(defn- error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (string/join \newline errors)))

(defn- validation-error-msg [errors]
  (str "The following validation errors occurred for your command:\n\n"
       (string/join \newline errors)))

(defn- exit [ok? msg log-fn]
  (log-fn msg)
  (System/exit (if ok? 0 1)))

(defn- exit-with-exception[ok? msg e log-fn]
  (log-fn msg)
  (log-fn (with-out-str (clojure.stacktrace/print-stack-trace e)))
  (System/exit (if ok? 0 1)))

(defn- validate-options [options]
  (let [{:keys [analyze-individual-commits analyze-branch-diff create-gitlab-note create-github-comment create-bitbucket-comment
                codescene-delta-analysis-url codescene-user codescene-password codescene-repository
                previous-commit current-commit base-revision
                gitlab-api-url gitlab-api-token gitlab-project-id gitlab-merge-request-iid
                github-api-url github-api-token github-owner github-repo github-pull-request-id
                bitbucket-api-url bitbucket-user bitbucket-password bitbucket-repo bitbucket-pull-request-id]} options]
    (filter
      some?
      (concat
        (when-not (some? codescene-delta-analysis-url) ["CodeScene delta analysis URL not specified"])
        (when-not (some? codescene-user) ["Codescene user not specified"])
        (when-not (some? codescene-password) ["Codescene password not specified"])
        (when-not (some? codescene-repository) ["Codescene repository not specified"])
        (when analyze-individual-commits
          [(when-not (some? current-commit) "Current commit not specified")
           (when-not (some? previous-commit) "Previous commit not specified")])
        (when analyze-branch-diff
          ;; TODO: Don't report this twice...
          [(when-not (some? current-commit) "Current commit not specified")
           (when-not (some? base-revision) "Base revision not specified")])
        (when create-gitlab-note
          [(when-not (some? gitlab-api-url) "GitLab API URL not specified")
           (when-not (some? gitlab-api-token) "GitLab API token not specified")
           (when-not (some? gitlab-project-id) "GitLab Project Id not specified")
           (when-not (some? gitlab-merge-request-iid) "GitLab Merge request IID not specified")])
        (when create-github-comment
          [(when-not (some? github-api-url) "GitHub API URL not specified")
           (when-not (some? github-api-token) "GitHub API token not specified")
           (when-not (some? github-owner) "GitHub repository owner not specified")
           (when-not (some? github-repo) "GitHub repository name not specified")
           (when-not (some? github-pull-request-id) "GitHub pull request ID not specified")])
        (when create-bitbucket-comment
          [(when-not (some? bitbucket-api-url) "BitBucket API URL not specified")
           (when-not (some? bitbucket-user) "BitBucket user not specified")
           (when-not (some? bitbucket-password) "BitBucket password not specified")
           (when-not (some? bitbucket-repo) "BitBucket repository name not specified")
           (when-not (some? bitbucket-pull-request-id) "BitBucket pull request ID not specified")])))))

(defn parse-args
  [args]
  (let [{:keys [options arguments errors summary]} (cli/parse-opts args cli-options)]
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

(defn run-analysis [options log-fn]
  (let [{:keys [analyze-individual-commits analyze-branch-diff previous-commit base-revision]} options]
    (concat
      (when (and analyze-individual-commits (or (some? previous-commit) (some? base-revision)))
        (delta-analysis/analyze-individual-commits-for options log-fn))
      (when (and analyze-branch-diff (some? base-revision))
        (delta-analysis/analyze-work-on-branch-for options log-fn)))))

(defn log-result [options results log-fn]
  (log-fn (results/as-text results options)))

(defn ex->str [e]
  (str e (or (ex-data e) "") (with-out-str (clojure.stacktrace/print-stack-trace e))))

(defn run-analysis-and-handle-result [options log-fn]
  (try
    (let [results (run-analysis options log-fn)
          success (not-any? :unstable results)]
      (when-let [result-path (:result-path options)]
        (with-open [wr (io/writer result-path)]
          (.write wr (json/write-str results))))
      (when (:create-gitlab-note options)
        (merge-requests/create-gitlab-note options results log-fn))
      (when (:create-github-comment options)
        (merge-requests/create-github-comment options results log-fn))
      (when (:create-bitbucket-comment options)
        (merge-requests/create-bitbucket-comment options results log-fn))
      (when (:log-result options)
        (log-result options results log-fn))
      [success (if success "CodeScene delta analysis ok!" "CodeScene delta analysis detected problems!")])
    (catch Exception e
      [(:pass-on-failed-analysis options) (str "CodeScene-CI/CD couldn't perform the delta analysis:" (ex->str e))])))

(defn -main [& args]
  (let [{:keys [options exit-message ok?]} (parse-args args)
        log-fn println]
    (if exit-message
      (exit ok? exit-message log-fn)
      (let [[ok? exit-message] (run-analysis-and-handle-result options log-fn)]
        (exit ok? exit-message log-fn)))))

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
  (def results (binding [clojure.java.shell/*sh-dir* d] (run-analysis-and-handle-result options println))))