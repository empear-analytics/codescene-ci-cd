(ns codescene-ci-cd.git-api
  "Wraps the git command-line API"
  (:require [clojure.java.shell :as shell]
            [clojure.string :as string]))

(defn commit-range [from-commit to-commit]
  [from-commit to-commit]
  (let [{:keys [exit out err] :as git-result} (shell/sh "git" "log" "--pretty='%H'" (format "%s..%s" from-commit to-commit))]
    (clojure.pprint/pprint exit)
    (clojure.pprint/pprint out)
    (if (= exit 0)
      (->> out
          (#(string/split % #"\n"))
          (mapv #(string/replace % #"['\"]" ""))
          (filter not-empty))
      (throw (ex-info "Failed to get commits from git" git-result)))))


(comment
  (commit-range "5d93403" "fc77573")  )
