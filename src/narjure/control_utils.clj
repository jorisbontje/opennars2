(ns narjure.control-utils
  (:require
    [narjure.bag :as b]
    [clojure.math.numeric-tower :as math]))

(def selection-parameter 3)
(defn selection-fn
  ""
  [bag]
  (* (math/expt (rand) selection-parameter) (b/count-elements bag)))

(defn forget-element [el]                                   ;TODO put in control-utils
  (let [budget (:budget (:id el))
        new-priority (* (:priority el) (second budget))
        new-budget  [new-priority (second budget)]]
    (assoc el :priority new-priority
              :id (assoc (:id el) :budget new-budget))))

(comment
  ; this does not work
  ; (make-evidence [9 7 5 3 1] [18 16 14 12])
  ; => (9 18 16 7 5 14 12 3 1)

  (defn make-ev-helper [e2 e1 sofar]
    (let [r1 (first e1)
          r2 (first e2)]
      (case [(= nil r1) (= nil r2)]
        [true true] sofar
        [true false] (make-ev-helper [] (rest e2) (concat [r2] sofar))
        [false true] (make-ev-helper (rest e1) [] (concat [r1] sofar))
        [false false] (make-ev-helper (rest e1) (rest e2) (concat [r1] [r2] sofar)))))

  (defn make-evidence [e1 e2]
    (take max-evidence (reverse (make-ev-helper e1 e2 []))))
  )

(def max-evidence 50)

; this does work
; (make-evidence [9 7 5 3 1] [18 16 14 12])
; => (9 18 7 16 5 14 3 12)

(defn make-evidence [e1 e2]
  (take max-evidence (interleave e1 e2)))