(ns codescene-gitlab.git-api
  "Wraps the git command-line API"
  (:require [clojure.java.shell :as shell]
            [clojure.string :as string]))

(defn commit-range [from-commit to-commit]
  [from-commit to-commit]
  (->> (shell/sh "git" "log" "--pretty='%H'" (format "%s..%s" from-commit to-commit))
       :out
       (#(string/split % #"\n"))
       (mapv #(string/replace % #"['\"]" ""))
       (filter empty)))