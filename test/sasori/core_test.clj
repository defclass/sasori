(ns sasori.core-test
  (:require [clojure.test :refer :all]
            [sasori
             [core :as sasori]
             [dsl :as dsl]]))

(defn test-local-ls
  [node context]
  (let [dsl (dsl/cmd "ls")]
    (sasori/sh-dsl dsl node)))

(deftest test-do-parallel
  (let [task-vars (sasori/task-vars test-local-ls)
        host-infos [{:host "local"}]
        result (sasori/parallel-tasks
                 task-vars host-infos
                 :global-opts {:verbose false})
        stub :stub
        error-result (sasori/parallel-tasks
                       task-vars host-infos
                       :global-opts {:verbose false}
                       :context {:key-to-test-context stub})]
    (is (not (#'sasori/error? (first result))))
    (is (= stub (-> error-result first :context :key-to-test-context)))))

(deftest test-do-sequence
  (let [task-vars (sasori/task-vars test-local-ls)
        host-infos [{:host "local"}]
        result (sasori/sequence-tasks
                 task-vars host-infos
                 :global-opts {:verbose false})
        stub :stub
        error-result (sasori/sequence-tasks
                       task-vars host-infos
                       :global-opts {:verbose false}
                       :context {:key-to-test-context stub})]
    (is (not (#'sasori/error? (first result))))
    (is (= stub (-> error-result first :context :key-to-test-context)))))
