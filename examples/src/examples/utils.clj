(ns examples.utils
  (:require [clojure.java.io :as io]
            [sasori
             [core :as sasori]
             [utils :as u]
             [dsl :as dsl]]
            [selmer.parser :as tpl])
  (:import (java.io File)))

(defmacro create-kw-map
  [& symbols]
  `(zipmap (map keyword '~symbols) (list ~@symbols)))

(defn rsync-resource-to [resource-path remote-path & [opts]]
  (let [resource-file (io/as-file (io/resource resource-path))
        _ (when-not (instance? File resource-file)
            (u/error! (format "resource-path not exist: %s" resource-path)))
        local-path (.getAbsolutePath resource-file)
        dir-default-opts {:recursive true}
        [local-path opts] (if (.isFile resource-file)
                            [local-path opts]
                            [(str local-path "/")
                             (merge dir-default-opts opts)])
        rsync-opts (select-keys opts [:sudo :compress :progress :recursive :delete :human])
        dsl (dsl/rsync local-path remote-path rsync-opts)]
    (sasori/sh-dsl dsl opts)))


(defn template [node source destination & [transform-label-m]]
  (let [s (tpl/render-file source transform-label-m)]
    (sasori/scp-str-to-file s destination node)))