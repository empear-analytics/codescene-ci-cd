(defproject codescene-ci-cd "1.1.0-SNAPSHOT"
  :description "A bridge to CodeScene's delta analysis"
  :url "https://github.com/empear-analytics/codescene-ci-cd"
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/tools.cli "0.4.2"]
                 [org.clojure/data.json "0.2.6"]
                 [org.clojure/core.async "0.4.500"]
                 [compojure "1.6.1"]
                 [ring/ring-defaults "0.3.2"]
                 [ring/ring-jetty-adapter "1.7.1"]
                 [ring/ring-json "0.4.0"]
                 [ring/ring-codec "1.1.2"]
                 [clj-http "3.10.0"]
                 [hiccup "1.0.5"]
                 [com.taoensso/timbre "4.10.0"]
                 [cheshire "5.8.1"]]
  :main ^:skip-aot codescene-ci-cd.core
  :uberjar-name "codescene-ci-cd.standalone.jar"
  :profiles {:uberjar {:aot :all}})
