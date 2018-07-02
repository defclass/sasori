(ns sasori.color)

(defn- gen-mark [n]
  (str "\033[38;5;" n "m"))

(def ^:private clear-mark "\033[0m")

(defn- assemble-color [start-mark s]
  (str start-mark s clear-mark))

(defn- wrap-mark [color-index s opts]
  (let [opts (merge {:color true} opts)]
    (if-not (:color opts)
     s
     (-> (gen-mark color-index)
         (assemble-color s)))))

(defn get-cached-color-generator []
  (let [color-count (atom 0)
        start-marks (atom {})]
    (fn [k opts]
      (let [opts (merge {:color true} opts)]
        (if-not (:color opts)
         k
         (let [start-mark
               (if-let [exist-mark (get @start-marks k)]
                 exist-mark
                 (let [n (swap! color-count inc)
                       new-color-mark (gen-mark n)]
                   (swap! start-marks assoc k new-color-mark)
                   new-color-mark))]
           (assemble-color start-mark k)))))))

(def wrap-host (get-cached-color-generator))

(def wrap-red (partial wrap-mark 1))
(def wrap-green (partial wrap-mark 2))
(def wrap-yellow (partial wrap-mark 3))
(def wrap-blue (partial wrap-mark 4))
(def wrap-magenta (partial wrap-mark 5))
(def wrap-cyan (partial wrap-mark 6))
(def wrap-gray (partial wrap-mark 7))
(def wrap-dark-grey (partial wrap-mark 8))
(def wrap-light-red (partial wrap-mark 9))
(def wrap-light-green (partial wrap-mark 10))
(def wrap-light-yellow (partial wrap-mark 11))
(def wrap-light-blue (partial wrap-mark 12))
(def wrap-light-magenta (partial wrap-mark 13))
(def wrap-light-cyan (partial wrap-mark 14))
(def wrap-light-gray (partial wrap-mark 15))
