(defproject codescene-ci-cd "1.1.6"
  :description "A bridge to CodeScene's delta analysis"
  :url "https://github.com/empear-analytics/codescene-ci-cd"
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/tools.cli "0.4.2"]
                 [org.clojure/data.json "0.2.6"]
                 [compojure "1.6.1"]
                 [ring/ring-defaults "0.3.2"]
                 [ring/ring-jetty-adapter "1.7.1"]
                 [ring/ring-json "0.4.0"]
                 [ring/ring-codec "1.1.2"]
                 [clj-http "3.9.0"]
                 [hiccup "1.0.5"]
                 [com.taoensso/timbre "4.10.0"]
                 ;; use latest tools.reader to fix issues with timbre using an old version
                 ;; see https://github.com/ptaoussanis/timbre/issues/263
                 [org.clojure/tools.reader "1.3.2"]
                 [cheshire "5.8.1"]
                 ;; explicit dependency on jaxb-api for java 9+ compatibility
                 ;; see https://stackoverflow.com/questions/43574426/how-to-resolve-java-lang-noclassdeffounderror-javax-xml-bind-jaxbexception-in-j
                 [javax.xml.bind/jaxb-api "2.3.0"]
                 ;; try to fix Unexpected error (NoClassDefFoundError) macroexpanding GET at (src/codescene_ci_cd/server.clj:26:5).
                 ;;   instaparse/core/Parser$reify__17365
                 [instaparse "1.4.10"]
                 ]
  :main ^:skip-aot codescene-ci-cd.core
  :uberjar-name "codescene-ci-cd.standalone.jar"
  :profiles {:uberjar {:aot :all}})
