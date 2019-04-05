(ns codescene-gitlab.results
  (:require [clojure.java.io :as io]
            [clojure.string :as string]))

(defn- url-parts [url]
  (let [java-url (io/as-url url)]
    {:protocol (.getProtocol java-url)
     :host     (.getHost java-url)
     :port     (.getPort java-url)}))

(defn- wrap [tag s]
  (str tag s tag))

(defn- bold [s]
  (wrap "**" s))

(defn- wrap-and-join [tag coll]
  (str tag (string/join tag coll) tag))

(defn- linebreak-between [coll]
  (string/join "<br/>" coll))

(defn- newline-between [coll]
  (string/join \newline coll))

(defn- table-row [coll]
  (wrap-and-join "|" coll))

(defn warning->markdown [warning]
  (let [{:keys [category details]} warning]
    (linebreak-between (into [(format "Category: %s" category)] details))))

(defn entry->markdown-table [entry options url-parts]
  (let [{:keys [risk-threshold fail-on-failed-goal fail-on-declining-code-health]} options
        {:keys [protocol host port]} url-parts
        {:keys [title view result commits goal-has-failed code-health-declined hits-risk-threshold]} entry
        {:keys [risk description warnings]} result
        view-url (format "%s//%s:%d%s" protocol host port view)]
    (concat
      [(table-row ["" (bold "CodeScene Delta Analysis Results")])]
      [(table-row ["-" "-"])]
      [(table-row [(bold "Risk") risk])]
      (when (or fail-on-failed-goal fail-on-declining-code-health)
        [(table-row [(bold "Quality Gates") (if (or goal-has-failed code-health-declined) "Fail" "OK")])])
      [(table-row [(bold "Description") description])]
      (when (seq commits)
        [(table-row [(bold "Commits") (linebreak-between commits)])])
      (when (seq warnings)
        [(table-row [(bold "Warnings") (linebreak-between (mapcat warning->markdown warnings))])])
      ["\n"])))

(defn as-markdown [entries options]
  (let [{:keys [delta-analysis-url]} options
        url-parts (url-parts delta-analysis-url)]
    (newline-between (mapcat #(entry->markdown-table % options url-parts) entries))))
