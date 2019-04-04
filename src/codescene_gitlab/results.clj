(ns codescene-gitlab.results
  (:require [clojure.java.io :as io]
            [clojure.string :as string]))


(defn- url-parts [url]
  (let [java-url (io/as-url url)]
    {:protocol (.getProtocol java-url)
     :host     (.getHost java-url)
     :port     (.getPort java-url)}))

(defn warning->text-lines [warning]
  (let [{:keys [category details]} warning]
    (into [category] (map #(str "- " %) details))))

(defn entry->text-lines [entry options show-commits url-parts]
  (let [{:keys [risk-threshold fail-on-failed-goal fail-on-declining-code-health]} options
        {:keys [protocol host port]} url-parts
        {:keys [title view result commits goal-has-failed code-health-declined hits-risk-threshold]} entry
        {:keys [risk description warnings]} result
        view-url (format "%s//%s:%d%s" protocol host port view)]
    (println description)
    (->> (concat
           [(format "%s (%s)" title view-url)
            (format "Risk: %d" risk)]
           (when (or fail-on-failed-goal fail-on-declining-code-health)
             (format "QG: %s" (if (or goal-has-failed code-health-declined) "Fail" "OK")))
           (when goal-has-failed ["Failed Quality Gate: a goal -- as defined by CodeScene's Intelligent Notes -- is violated. Check the details below."])
           (when code-health-declined ["Failed Quality Gate: the code degrades in health. Check the details below."])
           (when hits-risk-threshold [(format "This delta hit the configured risk threshold of %s." risk-threshold)])
           [description]
           (when show-commits
             (into ["Commits:"] (map #(str "- " %) commits)))
           (mapcat warning->text-lines warnings))
         (filter some?))))

(defn as-text-lines [entries options title show-commits]
  (let [{:keys [delta-analysis-url]} options
        url-parts (url-parts delta-analysis-url)]
    (apply concat
           [title]
           (map #(entry->text-lines % options show-commits url-parts) entries))))


(comment
  (def result [{:version "2",
                :url
                         "/projects/1/delta-analysis/5671ba8cf95e632e5e03576e8c15302c8973aa35",
                :view
                         "/1/delta-analysis/view/5671ba8cf95e632e5e03576e8c15302c8973aa35",
                :result
                         {:risk     10,
                          :description
                                    "The change is high risk as it more diffused (1 files modified) than your normal change sets. The risk increases as the author has somewhat lower experience in this repository.",
                          :warnings [],
                          :quality-gates
                                    {:degrades-in-code-health false, :violates-goal false}}}
               {:version "2",
                :url
                         "/projects/1/delta-analysis/2d25d2a3811d6424ad3458b58d17dd76d85b4539",
                :view
                         "/1/delta-analysis/view/2d25d2a3811d6424ad3458b58d17dd76d85b4539",
                :result
                         {:risk     10,
                          :description
                                    "The change is high risk and modifies 47 lines of code in 1 files. The risk increases as the author has somewhat lower experience in this repository.",
                          :warnings [],
                          :quality-gates
                                    {:degrades-in-code-health false, :violates-goal false}}}
               {:version "2",
                :url
                         "/projects/1/delta-analysis/3856598781ad10a53403c63002f6e3a4909cf9ec",
                :view
                         "/1/delta-analysis/view/3856598781ad10a53403c63002f6e3a4909cf9ec",
                :result
                         {:risk     1,
                          :description
                                    "The change is low risk, and touches 1 files and modifies 5 lines of code. The risk is lower since it's a very experienced author with many contributions.",
                          :warnings [],
                          :quality-gates
                                    {:degrades-in-code-health false, :violates-goal false}}}]))