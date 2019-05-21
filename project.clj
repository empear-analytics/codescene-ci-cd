(defproject codescene-ci-cd "1.0.0"
  :description "A bridge to CodeScene's delta analysis"
  :url "https://github.com/empear-analytics/codescene-ci-cd"
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [selmer "1.11.7"]
                 [clj-http "3.7.0"]
                 [org.clojure/tools.cli "0.4.2"]]
  :main ^:skip-aot codescene-ci-cd.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
