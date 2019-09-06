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
  (wrap " "(wrap "**" s)))

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

(defn- html-list [coll]
  (->> coll
       (map #(format "<li>%s</li>" %))
       string/join
       (format "<ul>%s</ul>")))

(defn- warning->markdown [warning]
  (let [{:keys [category details]} warning]
    (linebreak-between [(str category)
                        (html-list details)])))

(defn- warning->text [warning]
  (let [{:keys [category details]} warning]
    (newline-between (concat [(str \tab \tab category)]
                             (map #(str \tab \tab "- " %) details)))))

(defn- delta-description->text [delta-description]
  (let [{:keys [name degraded improved]} delta-description]
    (newline-between (concat
                      [(str \tab \tab name)]
                      (when (seq improved)
                        [(str \tab \tab "- Improvements: " (comma-between improved))])
                      (when (seq degraded)
                        [(str \tab \tab "- Degradations: " (comma-between degraded))])))))

(defn- delta-description->markdown [delta-description]
  (let [{:keys [name degraded improved]} delta-description]
    (linebreak-between [name
                        (html-list (concat
                                    (when (seq improved)
                                      [(str (bold "Improvements:") (comma-between improved))])
                                    (when (seq degraded)
                                      [(str (bold "Degradations:") (comma-between degraded))])))])))

(defn- entry->markdown-table [entry options url-parts]
  (let [{:keys [risk-threshold fail-on-failed-goal fail-on-declining-code-health]} options
        {:keys [protocol host port]} url-parts
        {:keys [title view result commits goal-has-failed code-health-declined hits-risk-threshold]} entry
        {:keys [risk description code-owners-for-quality-gates 
                warnings improvements code-health-delta-descriptions]} result
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
     (when (seq code-health-delta-descriptions)
       [(md-table-row [(bold "Code Health Delta Descriptions:") (linebreak-between (map delta-description->markdown code-health-delta-descriptions))])])
     [\newline])))

(defn- entry->text [entry options url-parts]
  (let [{:keys [risk-threshold fail-on-failed-goal fail-on-declining-code-health]} options
        {:keys [protocol host port]} url-parts
        {:keys [title view result commits goal-has-failed code-health-declined hits-risk-threshold]} entry
        {:keys [risk description code-owners-for-quality-gates 
                warnings improvements code-health-delta-descriptions]} result
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
     (when (seq code-health-delta-descriptions)
       [(str \tab "Code Health Delta Descriptions:" \newline (newline-between (map delta-description->text code-health-delta-descriptions)))])
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
    {:version              "2"
     :url                  "/projects/2/delta-analysis/b428cd6e3623e6050c3aa346d1b7462178a277ac"
     :view                 "/2/delta-analysis/view/b428cd6e3623e6050c3aa346d1b7462178a277ac"
     :result               {:id       "0ce49f96f2c4d028b3645a9f009dd490997b8a01"
                            :risk     5
                            :description "The risk is somewhat lower due to an experienced author."
                            :improvements  ["ViewComponentDescriptor.cs improves its Code Health from 8.3 -> 10.0"]
                            :quality-gates {:degrades-in-code-health true
                                            :violates-goal           false}
                            :warnings [{:category "Modifies Hotspot"
                                        :details  ["test-aspnet-mvc-repo/test/Microsoft.AspNetCore.Mvc.ViewFeatures.Test/ViewComponentResultTest.cs"]}
                                       {:category "Complexity Trend Warning"
                                        :details  ["test-aspnet-mvc-repo/test/Microsoft.AspNetCore.Mvc.Core.Test/Internal/ControllerActionInvokerTest.cs"]}
                                       {:category "Degrades in Code Health"
                                        :details  ["DefaultViewComponentHelperTest.cs degrades from a Code Health of 10.0 -> 9.0"
                                                   "ViewComponentResultTest.cs degrades from a Code Health of 9.3 -> 9.0"
                                                   "ControllerActionInvokerTest.cs degrades from a Code Health of 5.0 -> 4.7"]}]
                            :code-health-delta-descriptions [{:name     "DefaultViewComponentHelperTest.cs"
                                                              :degraded ["Duplicated Assertion Blocks - new issue"
                                                                         "High Degree of Code Duplication - new issue"]
                                                              :improved []}
                                                             {:name     "ViewComponentResultTest.cs"
                                                              :degraded ["Similar Code in Multiple Functions - new issue"]
                                                              :improved ["String Heavy Function Arguments - no longer an issue"
                                                                         "Constructor Over-Injection - no longer an issue"]}
                                                             {:name     "ControllerActionInvokerTest.cs"
                                                              :degraded ["Constructor Over-Injection - new issue"
                                                                         "Primitive Obsession - new issue"]
                                                              :improved []}]}
     :title                "Not used"
     :commits              ["7d0c1c5b2a786b231538c79257499f0b5adfd8ac"]
     :goal-has-failed      true
     :code-health-declined true
     :hits-risk-threshold  true})
  (spit "results.txt"(as-text [entry] options))
  (spit "results.md" (as-markdown [entry] options)))