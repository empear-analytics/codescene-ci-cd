(ns codescene-ci-cd.core-test
  (:require [clojure.test :refer :all]
            [codescene-ci-cd.core :refer :all]
            [codescene-ci-cd.gitlab-api :as gitlab]
            [codescene-ci-cd.codescene-api :as codescene]
            [codescene-ci-cd.git-api :as git]
            [codescene-ci-cd.test-data :as test-data]
            [codescene-ci-cd.golden-copy :as golden-copy]))

(defmacro with-gitlab-no-op-redefs [& body]
  `(with-redefs [gitlab/get-merge-request-notes (constantly [])
                 gitlab/delete-merge-request-note (constantly nil)
                 gitlab/create-merge-request-note (constantly nil)]
     ~@body))

(defn- throwing-fn [& _args]
  (fn [] (throw (ex-info "Boom" {}))))

(deftest delta-analysis-test
  (comment(testing "Succesful analysis - golden copy."
     (with-gitlab-no-op-redefs
       (with-redefs [codescene/run-delta-analysis-on-commits (constantly test-data/codescene-reply)
                     git/commit-range (fn [from-commit to-commit] [to-commit])]
         (let [[ok? exit-message] (run-analysis-and-handle-result test-data/test-options)]
           (is (= true ok?))
           (is (= golden-copy/analysis-results)))))))

  (comment (testing "Optionally pass on failed analysis."
     (with-gitlab-no-op-redefs
       (with-redefs [codescene/run-delta-analysis-on-commits (throwing-fn)
                     git/commit-range (fn [from-commit to-commit] [to-commit])]
         (let [options (assoc test-data/test-options :pass-on-failed-analysis true)
               [ok? exit-message] (run-analysis-and-handle-result options)]
           (is (= true ok?)))
         (let [options (assoc test-data/test-options :pass-on-failed-analysis false)
               [ok? exit-message] (run-analysis-and-handle-result options)]
           (is (= false ok?)))))))

  (testing "Optionally fail on degrading code health."
    (with-gitlab-no-op-redefs
      (with-redefs [codescene/run-delta-analysis-on-commits (constantly test-data/codescene-degrading-code-health-reply)
                    git/commit-range (fn [from-commit to-commit] [to-commit])]
        (let [options (assoc test-data/test-options :fail-on-declining-code-health true)
              [ok? exit-message] (run-analysis-and-handle-result options)]
          (is (= false ok?)))
        (let [options (assoc test-data/test-options :fail-on-declining-code-health false)
              [ok? exit-message] (run-analysis-and-handle-result options)]
          (is (= true ok?))))))

  (testing "Optionally fail on violated goal."
    (with-gitlab-no-op-redefs
      (with-redefs [codescene/run-delta-analysis-on-commits (constantly test-data/codescene-failed-goal-reply)
                    git/commit-range (fn [from-commit to-commit] [to-commit])]
        (let [options (assoc test-data/test-options :fail-on-failed-goal true)
              [ok? exit-message] (run-analysis-and-handle-result options)]
          (is (= false ok?)))
        (let [options (assoc test-data/test-options :fail-on-failed-goal false)
              [ok? exit-message] (run-analysis-and-handle-result options)]
          (is (= true ok?))))))

  (testing "Pass when no commits."
    (with-gitlab-no-op-redefs
      (with-redefs [codescene/run-delta-analysis-on-commits (constantly test-data/codescene-failed-goal-reply)
                    git/commit-range (fn [from-commit to-commit] [])]
        (let [[ok? exit-message] (run-analysis-and-handle-result test-data/test-options)]
          (is (= true ok?)))
        (let [options (-> test-data/test-options
                          (assoc :analyze-branch-diff false)
                          (assoc :analyze-individual-commits true))
              [ok? exit-message] (run-analysis-and-handle-result options)]
          (is (= true ok?)))))))



