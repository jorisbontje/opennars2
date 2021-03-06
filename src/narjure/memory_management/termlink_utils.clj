(ns narjure.memory-management.termlink-utils
  (:require
    [co.paralleluniverse.pulsar
     [core :refer :all]
     [actors :refer :all]]
    [taoensso.timbre :refer [debug info]]
    [narjure
     [global-atoms :refer :all]
     [bag :as b]
     [debug-util :refer :all]
     [control-utils :refer :all]
     [defaults :refer :all]
     [budget-functions :refer :all]]
    [narjure.memory-management
     [concept-utils :refer :all]]
    [clojure.core.unify :refer [unifier]]
    [nal.term_utils :refer [syntactic-complexity termlink-subterms]]
    [narjure.memory-management.local-inference
     [local-inference-utils :refer [get-task-id get-tasks]]
     [belief-processor :refer [process-belief]]
     [goal-processor :refer [process-goal]]
     [quest-processor :refer [process-quest]]
     [question-processor :refer [process-question]]]
    [nal.deriver
     [truth :refer [w2c t-or t-and confidence frequency expectation revision]]
     [projection-eternalization :refer [project-eternalize-to]]])
  (:refer-clojure :exclude [promise await]))

(defn get-linkable-terms
  "disallow linking to itself"
  [task]
  (filter #(not= % (:id @state)) (termlink-subterms (:statement task))))

(defn get-existing-terms-from-links
  "only use links whose target concept still exists"
  [links]
  (filter #(b/exists? @c-bag %) (keys links)))

(defn forget-termlinks []
  ;TODO test
  (doseq [[tl _] (:termlinks @state)]
    (when (not (b/exists? @c-bag tl))
      (set-state! (assoc @state :termlinks (dissoc (:termlinks @state) tl)))))
  (while (> (count (:termlinks @state)) concept-max-termlinks)
    (let [worst (apply min-key (comp first second) (:termlinks @state))]
      (set-state! (assoc @state :termlinks (dissoc (:termlinks @state) (first worst)))))))

(defn add-termlink [tl strength]
  (set-state! (assoc @state :termlinks (assoc (:termlinks @state)
                                         tl strength)))
  ;(forget-termlinks)
  )

(defn refresh-termlinks [task]
  ""
  ; :termlinks {term [budget]}
  (let [concept-prio (fn [z] (let [prio (concept-priority z)]
                               (if prio
                                 prio
                                 0.0)))
        newtermlinks (merge (apply merge
                                   (for [tl (get-linkable-terms task)] ;prefer existing termlinks strengths
                                     {tl [(* (first termlink-default-budget)
                                             (concept-prio tl)) (second termlink-default-budget)]}))
                            (:termlinks @state))
        valid-links (select-keys newtermlinks (get-existing-terms-from-links newtermlinks))];only these keys which exist in concept bag
    (set-state! (merge @state {:termlinks valid-links}))))

(defn link-feedback-handler
  [from [_ [derived-task belief-concept-id]]]                       ;this one uses the usual priority durability semantics
  (try
    ;TRADITIONAL BUDGET INFERENCE (BLINK PART)
    (let [complexity (if (:truth derived-task) (syntactic-complexity belief-concept-id) 1.0)
          truth-quality (if (:truth derived-task)
                 (truth-to-quality (:truth derived-task))
                 (w2c 1.0))
          quality (/ truth-quality complexity)
          [result-concept _]  (b/get-by-id @c-bag (:statement derived-task))
          activation (:priority result-concept)
          [p d] ((:termlinks @state) belief-concept-id)]
      (when (and p d truth-quality)
        (add-termlink belief-concept-id [(t-or p (t-or quality activation))
                                         (t-or d quality)]))
      )
    (catch Exception e () #_(println "fail"))))

(defn strengthen-termlink [link-strength]
  [(t-or (first link-strength) (first concept-selection-introduced-termlink-default-budget))
   (max (second link-strength) (second concept-selection-introduced-termlink-default-budget))])

(defn get-strengthened-termlink [link-strength]
  (if link-strength
    (strengthen-termlink link-strength)
    concept-selection-introduced-termlink-default-budget))


(defn forget-termlink [term]
  (let [[p d] ((:termlinks @state) term)
        new-budget [(* p d) d]]
    (set-state! (assoc-in @state [:termlinks term] new-budget))))

(defn get-termlink-endpoints []
  (let [initbag (b/default-bag concept-max-termlinks)]
    (try
      (reduce (fn [a b] (b/add-element a b)) initbag (for [[k v] (:termlinks @state)]
                                                      {:priority (t-or (first v)
                                                                       (:priority (first (b/get-by-id @c-bag k))))
                                                       :id       k}))
      (catch Exception e (print "")
                         ;(println (str "error in get-termlink-endpoints: " e)
                         ))))

(defn select-termlink-ref []
  ;now search through termlinks, get the endpoint concepts, and form a bag of them
  (let [initbag (b/default-bag concept-max-termlinks)
        resbag (get-termlink-endpoints)]
    ;now select an element from this bag
    (if (and resbag (pos? (b/count-elements resbag)))
      (let [[beliefconcept _] (b/get-by-index resbag (selection-fn (b/count-elements resbag)))]
        (forget-termlink (:id beliefconcept))               ;apply forgetting for termlinks only on selection
        (get-ref-from-term (:id beliefconcept)))
      nil)))