(ns sasori.exec
  (:require [clojure.java.io :as io])
  (:require [sasori
             [utils :as u]
             [log :as log]
             [protocols :as protocols]]))

(defrecord Ok [code out err]
  protocols/ICmdStatus
  (ok? [_] true)

  protocols/ITaskReturn
  (merge-to-msg [_ msg] msg))

(defrecord Failed [code out err]
  protocols/ICmdStatus
  (ok? [_] false)

  protocols/ITaskReturn
  (merge-to-msg [this msg]
    (assoc msg :error (:out this))))

(defn sh
  [& args]
  (let [[cmd opts] (split-with (complement keyword?) args)
        opts (apply hash-map opts)
        opts (merge {:verbose true} opts)
        builder (-> (ProcessBuilder. (into-array String cmd))
                    (.redirectErrorStream true))
        env (.environment builder)]
    (-> (apply u/join cmd)
        (log/debug opts))
    (when (:clear-env opts)
      (.clear env))
    (doseq [[k v] (:env opts)]
      (.put env k v))
    (when-let [dir (:dir opts)]
      (.directory builder (io/file dir)))
    (when (= :very (:verbose opts))
      (when-let [env (:env opts)] (log/info {:env env}))
      (when-let [dir (:dir opts)] (log/info {:dir dir})))
    (let [proc (.start builder)
          in (:in opts)]
      (if in
        (future
          (with-open [os (.getOutputStream proc)]
            (io/copy in os)))
        (.close (.getOutputStream proc)))
      (let [out
            (with-open
              [out-reader (io/reader (.getInputStream proc))]
              (loop [out []]
                (if-let [line (.readLine out-reader)]
                  (do (when (:verbose opts)
                        (log/info line opts))
                      (recur (conj out line)))
                  out)))
            exit-code (.waitFor proc)]
        (if (= 0 exit-code)
          (->Ok exit-code out nil)
          (->Failed exit-code out nil))))))
