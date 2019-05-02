(ns codescene-ci-cd.golden-copy
  (:require [clojure.test :refer :all]))


(def analysis-results
  [{:commits             ["7d0c1c5b2a786b231538c79257499f0b5adfd8ac"]
    :hits-risk-threshold true
    :result              {:description   "The change is low risk, and touches 1 files and modifies 0 lines of code. The risk is somewhat lower due to an experienced author."
                          :quality-gates {:degrades-in-code-health false
                                          :violates-goal           false}
                          :risk          8
                          :warnings      []}
    :title               "7d0c1c5b2a786b231538c79257499f0b5adfd8ac"
    :unstable            true
    :url                 "/projects/2/delta-analysis/b428cd6e3623e6050c3aa346d1b7462178a277ac"
    :version             "2"
    :view                "/2/delta-analysis/view/b428cd6e3623e6050c3aa346d1b7462178a277ac"}])