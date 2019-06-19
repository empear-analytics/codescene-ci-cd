(ns codescene-ci-cd.pages
  (:require [hiccup
             [core :refer [html]]
             [form :as form]
             [page :refer [html5 include-css]]]
            [ring.util.response :refer [content-type response]]))


(defn- header [message error]
  [:div.col-lg-12
   [:div
    [:h1 "CodeScene Enterprise JIRA Integration"]]
   [:hr]
   (when message
     [:div.alert.alert-success message])
   (when error
     [:div.alert.alert-danger error])])

(defn- layout [& forms]
  (html5
    (include-css "/vendor/bootstrap/css/bootstrap.min.css")
    (html
      [:div.container
       forms])))

(defn status-page
  [config message error]
  (-> (layout
        [:div.row (header message error)])
      response
      (content-type "text/html")))
