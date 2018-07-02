(ns sasori.protocols)

(defprotocol ICmd
  (plain [_ opts] "Transform to string form.")
  (exit? [_] "Whether exit when failed"))

(defprotocol ICmdStatus
  (ok? [_] "Success or failed."))

(defprotocol ITaskReturn
  (merge-to-msg [this msg] "Merge new state from task return to msg."))
