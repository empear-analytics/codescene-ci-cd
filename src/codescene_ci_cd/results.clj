(ns codescene-ci-cd.results
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

(defn- md-table-row [coll]
  (wrap-and-join "|" coll))

(defn- warning->markdown [warning]
  (let [{:keys [category details]} warning]
    (linebreak-between [(format "Category: %s" category) details])))

(defn- warning->text [warning]
  (let [{:keys [category details]} warning]
    (newline-between [(str \tab \tab "Category:" \space category)
                      (str \tab \tab details)])))

(defn- entry->markdown-table [entry options url-parts]
  (let [{:keys [risk-threshold fail-on-failed-goal fail-on-declining-code-health]} options
        {:keys [protocol host port]} url-parts
        {:keys [title view result commits goal-has-failed code-health-declined hits-risk-threshold]} entry
        {:keys [risk description warnings]} result
        view-url (format "%s//%s:%d%s" protocol host port view)]
    (concat
      [(md-table-row ["" (bold "CodeScene Delta Analysis Results")])]
      [(md-table-row ["-" "-"])]
      [(md-table-row [(bold "Risk") risk])]
      (when (or fail-on-failed-goal fail-on-declining-code-health)
        [(md-table-row [(bold "Quality Gates") (if (or goal-has-failed code-health-declined) "Fail" "OK")])])
      [(md-table-row [(bold "Description") description])]
      (when (seq commits)
        [(md-table-row [(bold "Commits") (linebreak-between commits)])])
      (when (seq warnings)
        [(md-table-row [(bold "Warnings") (linebreak-between (map warning->markdown warnings))])])
      [\newline])))

(defn- entry->text [entry options url-parts]
  (let [{:keys [risk-threshold fail-on-failed-goal fail-on-declining-code-health]} options
        {:keys [protocol host port]} url-parts
        {:keys [title view result commits goal-has-failed code-health-declined hits-risk-threshold]} entry
        {:keys [risk description warnings]} result
        view-url (format "%s//%s:%d%s" protocol host port view)]
    (concat

      ["CodeScene Delta Analysis Results:"]
      [(str \tab "Risk: " risk)]
      (when (or fail-on-failed-goal fail-on-declining-code-health)
        [(str \tab "Quality Gates:" \space (if (or goal-has-failed code-health-declined) "Fail" "OK"))])
      [(str \tab "Description:" \space description)]
      (when (seq commits)
        [(str \tab "Commits:" \space (newline-between commits))])
      (when (seq warnings)
        [(str \tab "Warnings:" \newline (newline-between (map warning->text warnings)))])
      [\newline])))

(defn as-markdown [entries options]
  (let [{:keys [delta-analysis-url]} options
        url-parts (url-parts delta-analysis-url)]
    (newline-between (mapcat #(entry->markdown-table % options url-parts) entries))))

(defn as-text [entries options]
  (let [{:keys [delta-analysis-url]} options
        url-parts (url-parts delta-analysis-url)]
    (newline-between (mapcat #(entry->text % options url-parts) entries))))
