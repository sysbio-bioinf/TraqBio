;; Copyright Fabian Schneider and Gunnar Völkel © 2014-2015
;;
;; Permission is hereby granted, free of charge, to any person obtaining a copy
;; of this software and associated documentation files (the "Software"), to deal
;; in the Software without restriction, including without limitation the rights
;; to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
;; copies of the Software, and to permit persons to whom the Software is
;; furnished to do so, subject to the following conditions:
;;
;; The above copyright notice and this permission notice shall be included in
;; all copies or substantial portions of the Software.
;;
;; THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
;; IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
;; FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
;; AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
;; LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
;; OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
;; THE SOFTWARE.

(ns traqbio.actions.diff
  (:require
    [clojure.data :as data]
    [clojure.string :as str]
    [traqbio.actions.tools :as t]
    [traqbio.db.crud :as db]))


(defn compare-steps
  [{id-1 :id, :as step-1}, {id-2 :id, :as step-2}]
  (cond
    ; sort new steps in decreasing order (not too important, but it is consistent)
    (and (neg? id-1) (neg? id-2)) (- id-2 id-1)
    ; negative ids represent2  new steps, put to the end)
    (neg? id-1) 1,
    (neg? id-2) -1,
    :else (- id-1 id-2)))


(defn step-modifications
  [new-steps, old-steps]
  (let [new-steps (mapv db/normalize-projectstep-data (sort compare-steps new-steps)),
        n (count new-steps),
        old-steps (mapv db/normalize-projectstep-data (sort compare-steps old-steps)),
        m (count old-steps)]
    (loop [i 0, j 0, added [], modified [], deleted []]
      (cond
        (and (< i n) (< j m))
          (let [new (nth new-steps i),
                new-id (:id new),
                old (nth old-steps j),
                old-id (:id old)]
            (if (= new-id old-id)
              (recur (inc i), (inc j),
                added,
                (cond-> modified
                  ; only if something changed
                  (not= new old)
                  ; add modification details with id
                  (conj (conj (vec (data/diff new, old)) old-id))),
                deleted)
              ; a step was deleted
              (recur i, (inc j),
                added,
                modified
                (conj deleted old))))
        (< i n) ; more new steps than old steps => added steps
          (recur n, j, (into added (subvec new-steps i)), modified, deleted)
        (< j m) ; more old steps than new steps => deleted steps
          (recur i, m, added, modified, (into deleted (subvec old-steps j)))        
        :else
          {:added added, :deleted deleted, :modifications modified
           :modified (mapv
                       (fn [[new-diff, _, _, old-id]]
                         (assoc new-diff :id old-id))
                       modified)}))))

(def ^:private step-attributes
  {:description "Description",
   :freetext "Free Text",
   :advisor "Advisor"})


(defn describe-attribute-modicifactions
 [[new-step, old-step, :as diff]]
 (let [step-name (some :type diff)
       renamed (when (contains? new-step :type)
                 (format "Step \"%s\" renamed to \"%s\"." (:type old-step) (:type new-step))),
       modified-attributes (reduce-kv
                             (fn [result, k, v]
                               (conj result (get step-attributes k (name k))))
                             []
                             (select-keys new-step (keys step-attributes)))]
   (when (or renamed modified-attributes)
     (str
       renamed
       (when (and renamed modified-attributes) "\n")
       (when (seq modified-attributes)
         (format "Changed step \"%s\": %s", step-name, (str/join ", " modified-attributes)))))))


(defn attribute-modified?
  [step-diff]
  (pos? (count (dissoc (first step-diff) :sequence))))


(defn position-modified?
  [step-diff]
  (contains? (first step-diff) :sequence))


(defn describe-move
  [[new-step, old-step :as diff]]
  (format "\"%s\" (%s -> %s)" (some :type diff) (:sequence old-step) (:sequence new-step)))


(defn describe-modifications
  [{:keys [added, modifications, deleted] :as diff}]
  (let [attribute-modifications (filter attribute-modified? modifications),
        moved-steps (filter position-modified? modifications)]
    (cond-> []
      (seq added)
        (conj (format "The following steps have been added:\n  %s." (str/join ", " (map :type added))))
      (seq deleted)
        (conj (format "The following steps have been deleted:\n  %s." (str/join ", " (map #(str \" (:type %) \") deleted))))
      (seq attribute-modifications)
        (into (map describe-attribute-modicifactions attribute-modifications))
      (seq moved-steps)
        (conj (format "The project step order has been modified:\n  %s"
                (->> moved-steps
                  (sort-by #(-> % second :sequence))
                  (map describe-move)
                  (str/join "\n  "))))))) 