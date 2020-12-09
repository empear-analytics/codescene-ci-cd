(ns codescene-ci-cd.utils
  (:require [clojure.string :as string]))

(def ^:private codescene-identifier "4744e426-5795-11e9-8647-d663bd873d93")
(def ^:private identifier-comment (format "<!--%s-->" codescene-identifier))

(defn with-codescene-identifier [markdown]
  (string/join \newline [identifier-comment markdown]))

(defn comments->codescene-comment-ids [comments]
  (->> comments
       (filter #(string/includes? (:body %) codescene-identifier))
       (map :id)))

(defn ids-to-comments->codescene-comment-ids [ids-to-comments]
  (->> ids-to-comments
       (filter #(string/includes? (second %) codescene-identifier))
       (into {})
       (map first)))

(defn comments-and-urls->codescene-comment-urls [comments-and-urls]
  (->> comments-and-urls
       (filter #(string/includes? (first %) codescene-identifier))
       (map second)))

(defn ex->str [e]
  (str e (or (ex-data e) "") (with-out-str (clojure.stacktrace/print-stack-trace e))))
