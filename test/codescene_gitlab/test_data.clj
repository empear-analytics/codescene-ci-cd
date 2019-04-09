(ns codescene-gitlab.test-data
  (:require [clojure.test :refer :all]))

(def test-options
  {:use-biomarkers                true,
   :analyze-individual-commits    false,
   :repository                    "cmake-project-template",
   :create-gitlab-note            true,
   :password                      "0bca8fd9-c137-47c7-9c2b-98f6fbc2cd1c",
   :delta-analysis-url            "http://codescene:3003/projects/2/delta-analysis",
   :analyze-branch-diff           true,
   :fail-on-declining-code-health true,
   :risk-threshold                7,
   :pass-on-failed-analysis       true,
   :base-revision                 "origin/master",
   :coupling-threshold-percent    45,
   :merge-request-iid             1,
   :branch                        "my-branch",
   :project-id                    4,
   :api-token                     "Q9nE8fxxs5xymf-koUD-",
   :current-commit                "7d0c1c5b2a786b231538c79257499f0b5adfd8ac",
   :gitlab-api-url                "http://localhost:8082/api/v4",
   :user                          "bot",
   :fail-on-failed-goal           true,
   :fail-on-high-risk             true})

(def codescene-reply
  {:version "2",
   :url     "/projects/2/delta-analysis/b428cd6e3623e6050c3aa346d1b7462178a277ac",
   :view    "/2/delta-analysis/view/b428cd6e3623e6050c3aa346d1b7462178a277ac",
   :result  {:risk          8,
             :description   "The change is low risk, and touches 1 files and modifies 0 lines of code. The risk is somewhat lower due to an experienced author.",
             :warnings      [],
             :quality-gates {:degrades-in-code-health false, :violates-goal false}},
   :title   "7d0c1c5b2a786b231538c79257499f0b5adfd8ac"})
