(ns codescene-gitlab.core
  (:require [clojure.string :as string]
            [clojure.tools.cli :as cli]
            [codescene-gitlab.delta-analysis :as delta-analysis]
            [codescene-gitlab.gitlab-api :as gitlab])
  (:gen-class)
  ;;(:import (codescene_gitlab RemoteAnalysisException))
  )

(def ^:private cli-options
  [["-h" "--help"]
   [nil "--url URL" "Project Delta Analysis URL" :default "http://localhost:3005/projects/1/delta-analysis"]
   ["-u" "--user USER" "CodeScene User" :default "bot"]
   ["-p" "--password PWD" "CodeScene Password" :default "0bca8fd9-c137-47c7-9c2b-98f6fbc2cd1c"]
   ["-r" "--repository REPO" "Repository" :default "cmake-project-template"]
   [nil "--use-biomarkers" "Use Biomarkers" :default true]
   [nil "--pass-on-failed-analysis" "Build Success on Failed Analysis" :default true]
   [nil "--mark-build-as-unstable" "Mark as Unstable on High Risk" :default true]
   [nil "--fail-on-failed-goal" "Mark Build as Unstable on Failed Goals" :default true]
   [nil "--fail-on-declining-code-health" "Mark Build as Unstable on Code Health Decline" :default true]
   [nil "--coupling-threshold-percent" "Temporal Coupling Threshold (in percent)" :default 75]
   [nil "--risk-threshold" nil :default 9]
   [nil "--previous-commit" nil :default "aa39b593"]
   [nil "--current-commit" :default "c8c5f971"]
   [nil "--branch" nil :default "master"]
   [nil "--analyze-latest-individually" "Individual Commits" :default true]
   [nil "--analyze-branch-diff" "By Branch" :default false]
   [nil "--gitlab-url" "GitLab URL" :default nil]
   [nil "--project-id" "GitLab Project ID" :default nil]
   [nil "--api-token" "GitLab API Token" :default nil]
   [nil "--merge-request-iid" "GitLab Merge Request IID" :default nil]
   [nil "--create-gitlab-note" "By Branch" :default false]
   [nil "--base-revision" nil :default "aa39b593"]
   [nil "--html-dir" "Path where html output is generated" :default nil]])

(defn- usage [options-summary]
  (->> ["Usage: codescene-gitlab [options]"
        "Options:"
        options-summary]
       (string/join \newline)))

(defn- error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (string/join \newline errors)))

(defn- exit [success? msg]
  (println success? msg)
  (System/exit (if success? 0 1)))

(defn- validate-options [options]
  true)

(defn parse-args
  [args]
  (let [{:keys [options arguments errors summary]} (cli/parse-opts args cli-options)]
    (cond
      ;; help => exit OK with usage summary
      (:help options) {:exit-message (usage summary) :ok? true}
      ;; errors => exit with description of errors
      errors {:exit-message (error-msg errors)}
      ;; custom validation on arguments
      (validate-options options) {:options options}
      ;; failed custom validation => exit with usage summary
      :else {:exit-message (usage summary)})))

(defn run-analysis [options listener]
  (let [{:keys [analyze-latest-individually analyze-branch-diff previous-commit base-revision]} options]
    (concat
     (when (and analyze-latest-individually (some? previous-commit))
       (delta-analysis/analyze-latest-individual-commit-for options listener))
     (when (and analyze-branch-diff (some? base-revision))
       (delta-analysis/analyze-work-on-branch-for options listener)))))

(defn create-gitlab-note [options results]
  (let [{:keys [gitlab-url api-token project-id merge-request-iid]} options]
    (gitlab/create-merge-request-note gitlab-url api-token project-id merge-request-iid
                                      "CodeScene Analysis results.....")))

(defn -main
  [& args]
  (let [{:keys [options exit-message ok?]} (parse-args args)
        listener println]
    (if exit-message
      (exit ok? exit-message)
      (try
        (let [results (run-analysis options listener)
              success (not-any? :unstable results)]
          (when (and success (:create-gitlab-note options))
            (create-gitlab-note options results))
          (exit success ""))
        (catch Exception e
          (listener "Remote failure as CodeScene couldn't perform the delta analysis:")
          (listener e)
          (exit (:pass-on-failed-analysis options) ""))
        (catch Exception e
          (listener "Failed to run delta analysis:")
          (listener e)
          (exit false ""))))))
