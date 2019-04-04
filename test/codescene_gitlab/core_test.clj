(ns codescene-gitlab.core-test
  (:require [clojure.test :refer :all]
            [codescene-gitlab.core :refer :all]
            [clj-http.client :as http]))

(def ^:private test-options)

(def ^:private analysis-results)

(deftest a-test
  (testing "A first test."
    (with-redefs [http/post (fn [url  & [req & r]] {:body analysis-results})]
      (is (= (run-analysis test-options println) 1)))))


