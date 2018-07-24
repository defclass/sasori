(ns sasori.dsl
  "About host and hostname, follow ~/.ssh/config:
  host: host is for the nickname of the host, represent a entry in ~/.ssh/config.
  hostname: hostname is for the actual hostname."
  (:require [clojure.string :as str])
  (:require [sasori
             [utils :as u]
             [protocols :as protocols]]))

(defrecord HostInfo [host hostname port username])

(defn make-host-info [{:keys [host hostname port username]}]
  (when-not (or (every? string? [hostname username])
                (some? host))
    (u/error! "Host or `hostname && username` should be set."))
  (->HostInfo host hostname port username))

(defrecord GlobalOpts [verbose in env dir clear-env color])

(defn make-global-opts [m]
  (assert (map? m) "Opts should be map.")
  (map->GlobalOpts m))

(defrecord Node [host-info global-opts])

(defn make-node
  ([]
   (make-node nil))
  ([{:keys [host-info global-opts]}]
   (let [host (when host-info (make-host-info host-info))
         global-opts (when global-opts (make-global-opts global-opts))]
     (->Node host global-opts))))

(defn make-nodes [{:keys [host-infos global-opts]}]
  {:pre (sequential? host-infos)}
  (let [host-infos (map make-host-info host-infos)
        global-opts (when global-opts (make-global-opts global-opts))]
    (mapv #(->Node % global-opts) host-infos)))

(defn node? [node]
  (instance? Node node))

(defn assert-node! [node]
  (assert (node? node) "Should be node."))

(defn cmd? [x]
  (satisfies? protocols/ICmd x))

(defrecord Cmd [cmd-seq opts]
  protocols/ICmd
  (plain [_ _] (str/join " " cmd-seq))
  (exit? [_] (:exit? opts)))

(defn- parse-opts [opts]
  (assert (even? (count opts)) "Opts should be satisfy even?")
  (let [opts (apply hash-map opts)]
    (assert (every? keyword? (keys opts)) "Opts keys should be keyword.")
    opts))

(defn cmd [& args]
  (let [[str-seq local-opts] (split-with string? args)
        local-opts (parse-opts local-opts)
        local-opts (merge {:exit? true} local-opts)]
    (->Cmd str-seq local-opts)))

(defrecord Cmds [seq-cmds local-opts]
  protocols/ICmd
  (plain [_ node]
    (assert-node! node)
    (loop [acc "" [f & r] seq-cmds]
      (if (nil? f)
        acc
        (let [next-fragment
              (cond
                (nil? r)
                (protocols/plain f node)

                (protocols/exit? f)
                (str (protocols/plain f node) " &&")

                (not (protocols/exit? f))
                (str (protocols/plain f node) " ;")

                :else
                (u/error! "Unknown error: "
                          {:seq-cmds seq-cmds
                           :acc acc}))
              acc (u/join-not-blank acc next-fragment)]
          (recur acc r)))))
  (exit? [_] (:exit? local-opts)))

(defn cmds [& args]
  (let [[cmds local-opts] (split-with cmd? args)
        local-opts (parse-opts local-opts)]
    (->Cmds cmds local-opts)))

(defn escape-cmd
  "https://stackoverflow.com/questions/1250079/how-to-escape-single-quotes-within-single-quoted-strings"
  [s]
  (str/escape s {\' "'\"'\"'"}))

(defn build-ssh-conn
  [host]
  (instance? HostInfo host)
  (let [{:keys [host hostname port username]} host]
    (if host
      host
      (let [conn-info (format "%s@%s" username hostname)
            port (when port (str "-p " port))]
        (u/join-not-blank port conn-info)))))

(defrecord Ssh [seq-cmds local-opts]
  protocols/ICmd
  (plain [_ node]
    (assert-node! node)
    (let [conn-info (build-ssh-conn (:host-info node))]
      (->> (protocols/plain seq-cmds node)
           (escape-cmd)
           (format "ssh %s '%s'" conn-info))))
  (exit? [_] (:exit? local-opts)))

(defn ssh [& args]
  (let [[seq-cmds local-opts] (split-with cmd? args)
        local-opts (parse-opts local-opts)
        seq-cmd-obj (if (and (= 1 (count seq-cmds))
                             (instance? Cmds (first seq-cmds)))
                      (first seq-cmds)
                      (apply cmds seq-cmds))]
    (->Ssh seq-cmd-obj local-opts)))

(defrecord Sudo [cmd local-opts]
  protocols/ICmd
  (plain [_ node]
    (assert-node! node)
    ;; sudo sh -c would fit `sudo cmd > some-file-need-privilege`
    (format "sudo sh -c '%s'" (protocols/plain cmd node)))
  (exit? [_] (:exit? local-opts)))

(defn sudo
  "Not work on macos at present."
  [cmd & [local-opts]]
  (->Sudo cmd local-opts))

(defn- assemble-excludes [excludes]
  (let [complete-param (fn [path] (str "--exclude=" (str/trim path)))]
    (->> (map complete-param excludes)
         (str/join " "))))

(defn- build-rsync-dest
  [host-info dest-path]
  (assert (instance? HostInfo host-info) "Should be HostInfo instance.")
  (when-not (string? dest-path) (u/error! "Dest-path should be string."))
  (let [{:keys [host hostname username]} host-info]
    (if host
      (str host ":" dest-path)
      (str username "@" hostname ":" dest-path))))

(defrecord Rsync [src dest local-opts]
  protocols/ICmd
  (plain [this node]
    (let [{:keys [verbose]} (:global-opts node)
          {:keys [compress progress recursive delete human excludes
                  archive sudo]} local-opts
          dest (build-rsync-dest (:host-info node) dest)
          rsync-path (if sudo
                       "sudo `which rsync`"
                       "`which rsync`")
          params-in-s (u/cond-join
                        delete "--delete"

                        (u/not-blank? rsync-path)
                        (format "--rsync-path='%s'" rsync-path)

                        archive "-a"
                        verbose "-v"
                        compress "-z"
                        excludes (assemble-excludes excludes)
                        recursive "-r"
                        progress "--progress"
                        human "-h"
                        true src
                        true dest)]
      (u/join-not-blank "rsync" params-in-s)))
  (exit? [_] (:exit? local-opts)))

(defn rsync
  [src dest & [opts]]
  (let [opts opts]
    (let [{:keys [excludes]} opts
          default-opts {:verbose true :human true :progress true}]
      (when excludes
        (assert (sequential? excludes) "Excludes should match sequential?"))
      (->Rsync src dest (merge default-opts opts)))))

(defn- build-scp-dest
  [host dest-path]
  (assert (instance? HostInfo host) "Should be HostInfo instance.")
  (when-not (string? dest-path) (u/error! "dest-path should be string."))
  (let [{:keys [host hostname username]} host]
    (if host
      (str host ":" dest-path)
      (format "%s@%s:%s" username hostname dest-path))))

(defrecord Scp [src dest local-opts]
  protocols/ICmd
  (protocols/plain [this node]
    (let [{:keys [recursive]} local-opts
          {:keys [verbose]} (:global-opts node)
          {:keys [port] :as host} (:host-info node)
          dest (build-scp-dest host dest)
          params-in-s (u/cond-join
                        verbose "-vv"
                        recursive "-r"
                        port (u/join "-P" port)
                        true src
                        true dest)]
      (u/join-not-blank "scp" params-in-s)))
  (exit? [_] (:exit? local-opts)))

(defn scp
  [src dest & [local-opts]]
  (->Scp src dest local-opts))

(defn emit [cmd & [node]]
  (assert (cmd? cmd))
  (let [node (if (some? node) node (make-node {}))]
    (protocols/plain cmd node)))
