(ns sasori.utils-test
  (:require [clojure.test :refer :all]
            [sasori.utils :as u]))

(deftest test-cond->join
  (is (= "rsync -v -z -r"
         (let [params-in-s
               (u/cond-join
                 true "-v"
                 true "-z"
                 true "-r")]
           (u/join-not-blank "rsync" params-in-s))))

  (is (= "rsync -v -r"
         (let [params-in-s
               (u/cond-join
                 true "-v"
                 false "-z"
                 true "-r")]
           (u/join-not-blank "rsync" params-in-s)))))
