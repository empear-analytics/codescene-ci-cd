(ns codescene-gitlab.web-service
  (:require [clojure.test :refer :all]
            [ring.adapter.jetty :refer [run-jetty]]))

(defn handler [request]
  {:status  200
   :headers {"Content-Type" "text/html"}
   :body    (config :body)})

(def server (atom nil))

(defn start-server
  "Start a ring server and store a reference"
  []
  (swap! server
         (fn [_] (run-jetty handler {:port 3000 :join? false}))))

(defn stop-server
  []
  (.stop @server))