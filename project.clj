(defproject codescene-ci-cd :lein-v
  :description "A bridge to CodeScene's delta analysis"
  :url "https://github.com/empear-analytics/codescene-ci-cd"
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/tools.cli "0.4.2"]
                 [org.clojure/data.json "0.2.6"]
                 [clj-http "3.9.0"]
                 [com.taoensso/timbre "4.10.0"]
                 ;; use latest tools.reader to fix issues with timbre using an old version
                 ;; see https://github.com/ptaoussanis/timbre/issues/263
                 [org.clojure/tools.reader "1.3.2"]]
  :plugins [[com.roomkey/lein-v "7.2.0"]]
  :main ^:skip-aot codescene-ci-cd.core
  :uberjar-name "codescene-ci-cd.standalone.jar"
  :profiles {:uberjar {:aot :all}}
  :release-tasks [["vcs" "assert-committed"]
                  ["v" "update"] ;; compute new version & tag it
                  ["v" "push-tags"]])
