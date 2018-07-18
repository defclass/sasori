(ns sasori.utils
  (:require [clojure.string :as str]))

(defn error! [msg & [map]]
  (let [map (or map {})]
    (throw (ex-info msg map))))

(defn not-blank?
  [x]
  (and (string? x)
       (not (str/blank? x))))

(defn join [& strings]
  (str/join " " strings))

(defn join-not-blank [& seq-of-s]
  (->> (filter not-blank? seq-of-s)
       (apply join)))

(defmacro cond-join
  [& clauses]
  (assert (even? (count clauses)))
  (let [steps (map (fn [[test result]] `(when ~test ~result))
                   (partition 2 clauses))]
    `(join-not-blank
       ~@steps)))

(defn maybe-map? [m]
  (let [maybe-map-f (some-fn nil? map?)]
    (maybe-map-f m)))



