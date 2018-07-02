(ns examples.ss.centos
  (:require [sasori
             [core :as sasori]
             [dsl :as dsl]])
  (:require [examples.utils :as utils]))

(defn- change-to-yum-repos
  []
  (dsl/cmd "cd /etc/yum.repos.d/"))

(defn- safe-version
  [node]
  (if-let [version (:version node)]
    version
    (throw (RuntimeException. "Should spec version."))))

(defn install-ss
  [node _]
  (let [version (safe-version node)
        download-cmd
        (format "sudo curl -O https://copr.fedorainfracloud.org/coprs/librehat/shadowsocks/repo/epel-7/librehat-shadowsocks-epel-%s.repo"
                version)
        dsl (dsl/ssh
              (change-to-yum-repos)
              (dsl/cmd download-cmd)
              (dsl/cmd "sudo yum install -y shadowsocks-libev"))]
    (sasori/sh-dsl dsl node)))

(defn config-ss
  [node _]
  (let [opts {:sudo true}
        dsl (utils/rsync-resource-to
              "shadowsocks/config.json" "/etc/shadowsocks-libev/" opts)]
    (sasori/sh-dsl dsl node)))

(defn- fix-libmbedcrypto
  "FIX: ss-server: error while loading shared libraries: libmbedcrypto.so.0:
  cannot open shared object file: No such file or directory"
  [node _]
  (let [dsl (dsl/ssh
              (dsl/cmd "cd /usr/lib64")
              (dsl/cmd "ln -s libmbedcrypto.so.1 libmbedcrypto.so.0"))]
    (sasori/sh-dsl dsl node)))

(defn start-ss
  [node _]
  (let [dsl (dsl/ssh
              (dsl/cmd "sudo systemctl enable shadowsocks-libev")
              (dsl/cmd "sudo systemctl start shadowsocks-libev"))]
    (sasori/sh-dsl dsl node)))

(defn do-tasks
  [& [opts]]
  (let [nodes opts
        init-msgs (map sasori/make-msg nodes)
        task-vars (sasori/task-vars install-ss config-ss start-ss)]
    (sasori/parallel-tasks init-msgs task-vars)))