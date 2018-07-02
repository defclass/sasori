(ns examples.ss.ubuntu
  "Ubuntu 1604.4"
  (:require [sasori
             [core :as sasori]
             [dsl :as dsl]])
  (:require [examples.utils :as utils]))

(defn install-ss
  [node _]
  (let [dsl (dsl/ssh
              (dsl/cmd "sudo apt-get update")
              (dsl/cmd "sudo apt-get -y install python-m2crypto ")
              (dsl/cmd "sudo apt-get -y install shadowsocks"))]
    (sasori/sh-dsl dsl node)))

(defn config-ss
  [node _]
  (let [opts (merge node {:sudo true})]
    (utils/rsync-resource-to
      "shadowsocks/ubuntu/config.json" "/etc/shadowsocks/" opts)))

(defn install-systemd
  [node _]
  (let [opts (merge node {:sudo true})]
    (utils/rsync-resource-to
      "shadowsocks/ubuntu/shadowsocks-server.service"
      "/etc/systemd/system/"
      opts)))

(defn start-ss
  [node _]
  (let [dsl (dsl/ssh
              (dsl/cmd "sudo systemctl enable shadowsocks-server")
              (dsl/cmd "sudo systemctl start shadowsocks-server"))]
    (sasori/sh-dsl dsl node)))

(defn do-tasks
  [& [opts]]
  (let [nodes opts
        init-msgs (map sasori/make-msg nodes)
        task-vars (sasori/task-vars
                    install-ss
                    config-ss
                    install-systemd
                    start-ss)]
    (sasori/parallel-tasks init-msgs task-vars)))