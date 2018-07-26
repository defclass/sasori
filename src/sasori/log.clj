(ns sasori.log
  (:require [clojure.string :as str])
  (:require [sasori.color :as color]
            [sasori.utils :as u])
  (:import (java.text SimpleDateFormat)))

(def time-formatter (SimpleDateFormat. "HH:mm:ss"))

(defn get-now []
  (let [date (new java.util.Date)]
    (.format time-formatter date)))

(defn build-host-info [m]
  (let [{:keys [local? host hostname]} m
        host (if local? "local" (or host hostname))]
    (when-not (string? host)
      (u/error! "Unknown host" {:m m}))
    (color/wrap-host host m)))

(def levels (atom #{:info :error}))

(defn set-level! [level]
  (let [level-seq [:debug :info :error]]
    (reset! levels
            (-> (drop-while #(not= % level) level-seq)
                (set)))))

(defn- log [level msg & [opts]]
  (let [hostname (build-host-info opts)
        now (get-now)]
    (when (contains? @levels level)
      (let [level (str/upper-case (name level))
            msg (if (string? msg)
                  msg
                  (with-out-str (pr msg)))]
        (locking *out*
          (println (format "%s [%s] %s - %s" now hostname level msg)))))))

(def debug (partial log :debug))
(def info (partial log :info))
(def error (partial log :error))
