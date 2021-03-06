(ns examples.elk.core
  "To deploy out of the box elk logging service."
  (:require [sasori
             [core :as sasori]
             [dsl :as dsl]])
  (:require [examples.utils :as utils]))

(defn remote-base-path
  "Ensure remote base path."
  [node _]
  (let [remote-base-path (str (sasori/get-home node) "/elk")]
    (sasori/make-context (utils/create-kw-map remote-base-path))))

(defn sync-docker-elk
  [node {:keys [remote-base-path]}]
  (let [opts (merge {:recursive true} node)]
    (utils/rsync-resource-to "./elk" remote-base-path opts)))

(defn template-docker-compose
  "Replace docker-compose template"
  [node {:keys [remote-base-path]}]
  (let [local-tpl "./elk/docker-compose.yml"
        remote-path (str remote-base-path "/docker-compose.yml")
        tpl-params {:volumes (format "%s/%s" remote-base-path "volumes")}]
    (utils/template node local-tpl remote-path tpl-params)))

(defn docker-build
  "Build images"
  [node {:keys [remote-base-path]}]
  (let [dsl (dsl/ssh
              (dsl/cmd "cd" remote-base-path)
              (dsl/cmd "docker-compose build"))]
    (sasori/sh-dsl dsl node)))

(defn docker-up
  [node {:keys [remote-base-path]}]
  (let [dsl (dsl/ssh
              (dsl/cmd "cd" remote-base-path)
              (dsl/cmd "docker-compose up"))]
    (sasori/sh-dsl dsl node)))

;; host is for the nickname of the host, represent a entry in ~/.ssh/config.
;; eg:
;; host v1
;;    HostName ip-or-domain
;;    user username

(def host-info {:host "v1"})
(def global-opts {:verbose false :color true})

(defn play
  [& [opts]]
  (let [parallel? (:parallel? opts)
        task-vars (sasori/task-vars
                    remote-base-path
                    sync-docker-elk
                    template-docker-compose
                    docker-build)]
    (sasori/play task-vars
                 {:parallel? parallel?
                  :hosts-info host-info
                  :global-opts global-opts
                  :context nil})))

(defn -main
  " Usage:

  lein run -m examples.elk.core '{:parallel? true}'
  "
  [& args]
  (let [args-m (sasori/parse-from-clj args)]
    (play args-m)
    ;; JVM will wait for `clojure-agent-send-off-pool-x` exit, so need exit
    ;; manually.
    (System/exit 0)))


; output:
;
;$ lein run -m examples.elk.core '{:parallel? true}'
;Exec: Ensure remote base path.
;v1: Success.
;
;Exec: sync-docker-elk
;v1: Success.
;
;Exec: Replace docker-compose template
;v1: Success.
;
;Exec: Build images
;v1: Success.
;
