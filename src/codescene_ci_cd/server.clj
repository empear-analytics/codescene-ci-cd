(ns codescene-ci-cd.server
  (:require [taoensso.timbre :as log]
            [hiccup.middleware :refer [wrap-base-url]]
            [ring.adapter.jetty :refer [run-jetty]]
            [codescene-ci-cd
             [github-hook :as github-hook]
             [gitlab-hook :as gitlab-hook]
             [pages :as pages]
             [bitbucket-hook :as bitbucket-hook]
             [utils :as utils]]
            [ring.middleware
             [defaults :refer [api-defaults wrap-defaults]]
             [json :refer [wrap-json-response wrap-json-body]]
             [params :refer [wrap-params]]
             [resource :refer [wrap-resource]]]
            [compojure
             [core :refer [GET POST routes]]
             [route :as route]]
            [ring.util.response :refer [content-type response]]
            [clojure.java.io :as io]))

(def app nil)

(defn app-routes [config]
  (routes
    (GET "/" [message error]
         (pages/status-page config message error))

    (POST "/hooks/github" req
          (github-hook/on-hook req))

    (POST "/hooks/gitlab" req
          (gitlab-hook/on-hook req))

    (POST "/hooks/bitbucket" req
          (bitbucket-hook/on-hook req))

    (route/not-found "Not Found")))

;; Warn: Body-stream can only be slurped once. Possible conflict with other ring middleware
(defn- body-as-string [request]
  (let [body (:body request)]
    (if (string? body)
      body
      (slurp body))))

(defn wrap-body-as-string
  "Saves the body as a string for use in hook validation"
  [handler]
  (fn [request]
    (let [body (body-as-string request)]
      (handler (assoc request :body (io/input-stream (.getBytes body))
                              :body-as-string body)))))

(defn init-app [config]
  (alter-var-root
    #'app
    (constantly
      (-> config
          ;config/validate
          app-routes
          ;wrap-base-url
          ;(wrap-resource "public")
          ;(auth/wrap-auth config)
          (wrap-params)
          (wrap-json-response)
          (wrap-json-body {:keywords? true})
          (wrap-defaults api-defaults)
          (wrap-body-as-string)))))

(defn- load-config []
  (comment (let [config (config/read-config)]
             (log/info "Config loaded.")
             config))
  {})

(defn init []
  (let [config (load-config)]
    (init-app config)
    config))

(defn destroy []
  (log/info "destroy called"))

(defonce ^:private server (atom nil))

(defn stop-server []
  (when @server
    (.stop @server)))

(defn start-server
  ([]
   (start-server {}))
  ([options]
   (stop-server)
   (let [user-config (init)
         port (utils/getenv-int "CODESCENE_CI_CD_PORT" 3005)]

     (log/infof "Starting server at port %d..." port)
     (let [jetty-server (run-jetty
                          #'app
                          (merge {:port  port
                                  :join? false}
                                 options))]
       (reset! server jetty-server)))))


