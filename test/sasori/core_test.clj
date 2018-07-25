(ns sasori.core-test
  (:require [clojure.test :refer :all]
            [sasori
             [core :as sasori]
             [dsl :as dsl]]
            [sasori.protocols :as protocols]))

(defn test-local-ls
  [node context]
  (let [dsl (dsl/cmd "ls")]
    (sasori/sh-dsl dsl node)))

(deftest test-do-parallel
  (let [task-vars (sasori/task-vars test-local-ls)
        result (sasori/play task-vars
                            {:parallel? true
                             :global-opts {:verbose false}
                             :hosts-info {:host "local"}})
        stub :stub
        error-result (sasori/play task-vars
                                  {:parallel? true
                                   :global-opts {:verbose false}
                                   :hosts-info {:host "local"}
                                   :context {:key-to-test-context stub}})]
    (is (not (sasori/failed-msg? (first result))))
    (is (= stub (-> error-result first :context :key-to-test-context)))))

(deftest test-do-sequence
  (let [task-vars (sasori/task-vars test-local-ls)
        host-info {:host "local"}
        result (sasori/play task-vars
                            {:hosts-info {:host "local"}
                             :global-opts {:verbose false}})
        stub :stub
        error-result (sasori/play task-vars
                                  {:hosts-info host-info
                                   :global-opts {:verbose false}
                                   :context {:key-to-test-context stub}})]
    (is (not (sasori/failed-msg? (first result))))
    (is (= stub (-> error-result first :context :key-to-test-context)))))

(deftest test-context-merge-msg
  (let [m {:a 1 :b 2}
        context (sasori/make-context m)
        msg (sasori/make-msg (sasori/make-node))]
    (is (= m (-> (protocols/merge-to-msg context msg) :context)))))
