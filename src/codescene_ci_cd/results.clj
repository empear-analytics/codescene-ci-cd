(ns codescene-ci-cd.results
  (:require [clojure.java.io :as io]
            [clojure.string :as string]))

(defn- url-parts [url]
  (when-let [java-url (io/as-url url)]
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

(defn- comma-between [coll]
  (string/join "," coll))

(defn- md-table-row [coll]
  (wrap-and-join "|" coll))

(defn- warning->markdown [warning]
  (let [{:keys [category details]} warning]
    (linebreak-between (concat [(format "Category: %s" category)] details))))

(defn- warning->text [warning]
  (let [{:keys [category details]} warning]
    (newline-between (concat [(str \tab \tab "Category:" \space category)]
                             (map #(str \tab \tab %) details)))))

(defn- entry->markdown-table [entry options url-parts]
  (let [{:keys [risk-threshold fail-on-failed-goal fail-on-declining-code-health]} options
        {:keys [protocol host port]} url-parts
        {:keys [title view result commits goal-has-failed code-health-declined hits-risk-threshold]} entry
        {:keys [risk description code-owners-for-quality-gates warnings improvements]} result
        view-url (format "%s//%s:%d%s" protocol host port view)]
    (concat
      [(md-table-row ["" (bold "CodeScene Delta Analysis Results")])]
      [(md-table-row ["-" "-"])]
      [(md-table-row [(bold "Risk") risk])]
      (when (or fail-on-failed-goal fail-on-declining-code-health)
        [(md-table-row [(bold "Quality Gates") (if (or goal-has-failed code-health-declined) "Fail" "OK")])])
      [(md-table-row [(bold "Description") description])]
      (when (seq code-owners-for-quality-gates)
        [(md-table-row [(bold "Code Owners") (linebreak-between code-owners-for-quality-gates)])])
      (when (seq commits)
        [(md-table-row [(bold "Commits") (linebreak-between commits)])])
      (when (seq warnings)
        [(md-table-row [(bold "Warnings") (linebreak-between (map warning->markdown warnings))])])
      (when (seq improvements)
        [(md-table-row [(bold "Improvements") (linebreak-between improvements)])])
      [\newline])))

(defn- entry->text [entry options url-parts]
  (let [{:keys [risk-threshold fail-on-failed-goal fail-on-declining-code-health]} options
        {:keys [protocol host port]} url-parts
        {:keys [title view result commits goal-has-failed code-health-declined hits-risk-threshold]} entry
        {:keys [risk description code-owners-for-quality-gates warnings improvements]} result
        view-url (format "%s//%s:%d%s" protocol host port view)]
    (concat

      ["CodeScene Delta Analysis Results:"]
      [(str \tab "Risk: " risk)]
      (when (or fail-on-failed-goal fail-on-declining-code-health)
        [(str \tab "Quality Gates:" \space (if (or goal-has-failed code-health-declined) "Fail" "OK"))])
      [(str \tab "Description:" \space description)]
      (when (seq commits)
        [(str \tab "Commits:" \space (comma-between commits))])
      (when (seq code-owners-for-quality-gates)
        [(str \tab "Code Owners:" \newline (newline-between (map #(str \tab \tab %) code-owners-for-quality-gates)))])
      (when (seq warnings)
        [(str \tab "Warnings:" \newline (newline-between (map warning->text warnings)))])
      (when (seq improvements)
        [(str \tab "Improvements:" \newline (newline-between (map #(str \tab \tab %) improvements)))])
      [\newline])))

(defn as-markdown [entries options]
  (let [{:keys [delta-analysis-url]} options
        url-parts (url-parts delta-analysis-url)]
    (newline-between (mapcat #(entry->markdown-table % options url-parts) entries))))

(defn as-text [entries options]
  (let [{:keys [delta-analysis-url]} options
        url-parts (url-parts delta-analysis-url)]
    (newline-between (mapcat #(entry->text % options url-parts) entries))))


(comment
  (def options
    {:delta-analysis-url "http://localhost:3003/delta-analysis/projects/8"
     :risk-threshold 7
     :fail-on-failed-goal true
     :fail-on-declining-code-health true})
  (def entry
    {:version              "2",
     :url                  "/projects/2/delta-analysis/b428cd6e3623e6050c3aa346d1b7462178a277ac"
     :view                 "/2/delta-analysis/view/b428cd6e3623e6050c3aa346d1b7462178a277ac"
     :result               {:risk                          7,
                            :description                   "The change is high risk as it is more diffused (2 files modifified, 615 lines added, 2 lines deleted) than your normal change sets. The risk is lower since it's a very experienced author with many contributions"
                            :warnings                      [{:category "DEGRADES IN CODE HEALTH"
                                                             :details ["exmple.py degrades from a Code Health of 10 -> 6.1"]}
                                                            {:category "VIOLATES GOALS"
                                                             :details ["Hotspot marked \"supervise\", supervised.py, degrades from a CodeHealth of 10.0 -> 9.6"]}]
                            :code-owners-for-quality-gates [],
                            :quality-gates                 {:degrades-in-code-health false
                                                            :violates-goal false}}
     :title                "Not used"
     :commits              ["7d0c1c5b2a786b231538c79257499f0b5adfd8ac"]
     :goal-has-failed      true
     :code-health-declined true
     :hits-risk-threshold  true})
  (println (as-text [entry] options))
  (println (as-markdown [entry] options)))