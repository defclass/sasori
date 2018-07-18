(ns sasori.core
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.walk :as walk]
            [clojure.set :as set])
  (:require [sasori
             [utils :as u]
             [exec :as exec]
             [dsl :as dsl]
             [log :as log]
             [color :as color]
             [protocols :as protocols]]))

;;;; Exec

(defn sh-string
  [cmd & [args]]
  {:pre [(or (nil? args) (map? args))]}
  (let [args-in-seq (flatten (seq args))]
    (when (:verbose args)
      (log/info cmd))
    (->> (into ["/bin/sh" "-c" cmd] args-in-seq)
         (apply exec/sh))))

(defn sh-dsl
  [dsl node]
  (let [cmd-str (dsl/emit dsl node)]
    (sh-string cmd-str (:global-opts node))))

;;;; Tools

(defn get-home [node]
  (-> (dsl/ssh (dsl/cmd "pwd"))
      (sh-dsl node)
      :out
      (first)))

(defn get-host [node]
  (-> (dsl/ssh (dsl/cmd "hostname -s"))
      (sh-dsl node)
      :out
      (first)))

(defn get-ip [node]
  (-> (dsl/ssh (dsl/cmd "ip route get 1 | awk '{print $NF;exit}'"))
      (sh-dsl node)
      :out
      (first)))

(defn scp-str-to-file [s path node]
  (let [f (io/as-file path)
        directory (.getParent f)
        filename (.getAbsolutePath f)
        main-cmd (format "mkdir -p %s ; cat - > %s" directory filename)
        dsl (dsl/ssh (dsl/cmd main-cmd))
        node (update node :global-opts assoc :in s)]
    (sh-dsl dsl node)))

;;;; Compose tasks

(defn- gen-asserts
  [m ks]
  (let [f (fn [k]
            `(when-not (contains? ~m ~k)
               (u/error! (format "Safe-let: Key %s is not exist in %s" ~k ~m))))]
    `(do ~@(map f ks))))

(defn- insert-validator
  [bindings]
  (let [f (fn [[left right]]
            (if (and (map? left) (some? (:safe-keys left)))
              (let [syms (:safe-keys left)
                    ks (mapv keyword syms)]
                (vector '_ (gen-asserts right ks)
                        (set/rename-keys left {:safe-keys :keys}) right))
              [left right]))]
    (vec (mapcat f bindings))))

(defmacro safe-let
  [bindings & body]
  (assert (vector? bindings) "Binding is not a vector.")
  (assert (even? (count bindings)) "An even number of forms in binding vector.")
  (let [binding-entries (partition 2 bindings)
        new-bindings (insert-validator binding-entries)]
    `(let ~new-bindings
       ~@body)))

(defrecord Context [value]
  protocols/ITaskReturn
  (merge-to-msg [this msg]
    (let [ks (keys this)]
      (doseq [k ks]
        (when (contains? (:context msg) k)
          (u/error! (format "Key %s is exists in context." k))))
      (update msg :context merge (:value this)))))

(defn make-context [& [m]]
  (assert ((some-fn map? nil?) m))
  (->Context m))

;; Msg passed between tasks
(defrecord Msg [node context error])

(defn make-msg [node & [init-content]]
  (->Msg node init-content nil))

(defn msg? [x]
  (instance? Msg x))

(defn- error? [^Msg msg]
  (assert (msg? msg) "Msg is not Msg instance.")
  (some? (:error msg)))

(defn- gen-doc-logger
  "Return a log fn which receive a var. This fn will println var's doc or name."
  [fmt]
  (fn [v opts]
    (let [m (meta v)
          description (or (:doc m) (:name m))
          msg (format fmt description)]
      (log/info msg opts))))

(def start-logger (gen-doc-logger "Starting: %s"))
(def done-logger (gen-doc-logger "Done: %s"))

(defn- do-task [task-var node context]
  (let [verbose (get-in node [:global-opts :verbose])]
    (when verbose
      (start-logger task-var node))
    (let [resp (task-var node context)]
      (when verbose
        (done-logger task-var node))
      resp)))

(defn- task [task-var {:keys [node context] :as msg}]
  (if (error? msg)
    msg
    (try
      (let [return (do-task task-var node context)]
        (cond
          (satisfies? protocols/ITaskReturn return)
          (protocols/merge-to-msg return msg)

          :else
          (throw
            (RuntimeException.
              "Return didn't satisfied sasori.protocols/ITaskReturn"))))
      (catch Throwable t
        (assoc msg :error (into [t] (map str (.getStackTrace t))))))))

(defn- print-stats
  [task-var msgs]
  (let [{:keys [doc name]} (meta task-var)
        title (format "Exec: %s" (or doc name))]
    (->> (color/wrap-cyan title (:node (first msgs)))
         (println))
    (doseq [msg msgs]
      (let [node (:node msg)
            colored-host (log/build-host-info node)
            success-or-failed
            (if (error? msg)
              (color/wrap-red "Failed." (:global-opts node))
              (color/wrap-green "Success." (:global-opts node)))]
        (println (format "%s: %s" colored-host success-or-failed))))
    (println)))

(defn- do-tasks [map-f init-msgs tasks]
  {:pre [(every? msg? init-msgs)]}
  (let [results
        (reduce (fn [pre-msgs task-var]
                  (let [task-f (partial task task-var)
                        new-msgs (map-f task-f pre-msgs)]
                    (print-stats task-var new-msgs)
                    new-msgs))
                init-msgs
                tasks)]
    (doseq [r results]
      (when (error? r)
        (println (format "[%s] Found Error: " (log/build-host-info (:node r))))
        (println (str/join "\n" (:error r)))))
    results))

(defmacro task-vars
  "Generate task vars from sequence of symbols."
  [& syms]
  {:pre [(every? symbol? syms)]}
  `(vector
     ~@(map (fn [task-sym#] `(var ~task-sym#))
            syms)))

(defn build-init-msgs
  [host-infos global-opts & [context-m]]
  (let [nodes (dsl/make-nodes {:host-infos host-infos
                               :global-opts global-opts})]
    (map #(make-msg % context-m) nodes)))

(defn- exec-task
  [map-f task-vars host-infos global-opts context]
  {:pre [(and (sequential? task-vars)
              (every? var? task-vars)
              (sequential? host-infos)
              (u/maybe-map? global-opts)
              (u/maybe-map? context))]}
  (let [init-msgs (build-init-msgs host-infos global-opts context)]
    (do-tasks map-f init-msgs task-vars)))

(defn parallel-tasks
  [task-vars host-infos & {:keys [global-opts context]}]
  (exec-task pmap task-vars host-infos global-opts context))

(defn sequence-tasks
  [task-vars host-infos & {:keys [global-opts context]}]
  (exec-task map task-vars host-infos global-opts context))

;;;; Read from cli

(defn parse-from-clj
  "From cmd line:
  lein run -m ns/fn '{:hostname \"hostname\" :username \"some-username\"}'

  Return:

  {:hostname \"hostname\" :username \"some-username\"}"
  [args]
  (when (seq args)
    (read-string (first args))))

(defn parse-from-seq
  "From cmd line:
  lein run -m ns/fn hostname hostname username some-username

  Return:
  {:hostname \"hostname\" :username \"some-username\"}"
  [args]
  (-> (apply array-map args)
      (walk/keywordize-keys)))
