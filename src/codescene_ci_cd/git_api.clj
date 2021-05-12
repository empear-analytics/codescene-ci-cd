(ns codescene-ci-cd.git-api
  "Wraps the git command-line API"
  (:require [clojure.java.shell :as shell]
            [clojure.string :as string]))

(defn commit-range 
  "Returns commits in the requested range, ordered oldest to newest"
  [from-commit to-commit]
  [from-commit to-commit]
  (let [{:keys [exit out _err] :as git-result} (shell/sh "git" "log" "--pretty='%H'" (format "%s..%s" from-commit to-commit))]
    (if (= exit 0)
      (->> out
          (#(string/split % #"\n"))
          (mapv #(string/replace % #"['\"]" ""))
          (filter not-empty)
           reverse)
      (throw (ex-info "Failed to get commits from git" git-result)))))


(comment
  (commit-range "master" "496-update-codescene-ci-cd-with-latest-delta-analysis-format")  )
