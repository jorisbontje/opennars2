(ns nal.deriver.projection-eternalization
  (:require
    [nal.deriver.truth :refer [w2c frequency confidence]]))

;temporally project task to ref task (note: this is only for event tasks!!)"
(defn project-to [target-time t cur-time]
  (when (= :eternal (:occurrence t))
    (println "ERROR: Project called on eternal task!!"))
  (let [source-time (:occurrence t)
        dist (fn [a b] (Math/abs (- a b)))
        a 10.0]
    (if (= target-time source-time)
      t
      (assoc t
       :truth [(frequency t)
               (* (confidence t)
                  (- 1 (/ (dist source-time target-time)
                          (+ (dist source-time cur-time)
                             (dist target-time cur-time)
                             a))))]
       :occurrence target-time))))

;eternalize an event task to a task of eternal occurrence time
(defn eternalize [t]
  (when (= (:occurrence t) :eternal)
    (println "error: eternalization on eternal task"))
  (if (or (= (:task-type t) :belief) (= (:task-type t) :goal))
    (assoc t :truth [(frequency t) (w2c (confidence t))]
            :occurrence :eternal)
    (assoc t :occurrence :eternal)))

;temporally projecting/eternalizing a task to ref time
(defn project-eternalize-to [target-time t cur-time]
  (if (= nil t)
    nil
    (let [source-time (:occurrence t)
         get-eternal (fn [x] (if (= x :eternal) :eternal :temporal))]
     (case [(get-eternal target-time) (get-eternal source-time)]
       [:eternal :eternal] t
       [:temporal :eternal] (assoc t :occurrence target-time)
       [:eternal :temporal] (eternalize t)
       [:temporal :temporal] (project-to target-time t cur-time) #_(let [t-eternal (eternalize t)
                                   t-project (project-to target-time t cur-time)]
                               (if (> (confidence t-eternal)
                                      (confidence t-project))
                                 (assoc t-eternal :occurrence target-time)
                                 t-project))))))