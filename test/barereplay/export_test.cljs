(ns barereplay.export-test
  "The export, over recorded steps."
  (:require [cljs.test :refer [deftest is use-fixtures]]
            [cljs.reader :as reader]
            [barereplay.export :as export]
            [barereplay.store :as store]))

(use-fixtures :each (fn [t] (store/clear!) (t) (store/clear!)))

(def ^:private envelope
  {:request/id "r-1"
   :revision   "tasks:v1"
   :query      {:search "a"}
   :value      [{"id" 1 "title" "Audit" "status" "todo"}]
   :page-info  {:page 1 :page-size 10 :total-pages 1 :total-count 1}
   :shape      {:id-key "id"
                :fields [{:key "title" :type :string :required true}
                         {:key "status" :type :string :enum ["todo" "done"]
                          :options [{:value "todo" :label "To do"}]}]}})

(defn- step
  "A recorded step. `js/Math` stands in for the live element."
  [event]
  {:resource/id "tasks"
   :el          js/Math
   :event       event
   :before      {:url-intent {} :request-count 0}
   :after       {:last-accepted envelope :request-count 1}
   :effects     [[:notify-consumers {:resource/id "tasks"}]]})

(def ^:private recorded
  [(step [:response envelope])
   (step [:network-failed {:kind :http-status :status 404}])
   (step [:intent-patch {:query-patch {:sort "title"} :gesture-class :refinement}])])

(deftest a-step-leaves-its-live-element-behind
  (let [s (first (export/without-elements recorded))]
    (is (not (contains? s :el)))
    (is (= "tasks" (:resource/id s)))
    (is (= [:response envelope] (:event s)))))

(deftest every-member-of-every-step-survives-pr-str-and-read-string
  (run! store/record! recorded)
  (is (= (export/without-elements recorded)
         (reader/read-string (export/log)))))

(deftest a-keyword-stays-a-keyword-and-a-domain-key-stays-a-string
  (run! store/record! recorded)
  (let [read-back (first (reader/read-string (export/log)))
        shape     (get-in read-back [:after :last-accepted :shape])]
    (is (= "id" (:id-key shape)))
    (is (= :string (:type (first (:fields shape)))))
    (is (= "Audit" (get (first (get-in read-back [:after :last-accepted :value])) "title")))))

(deftest an-unrecorded-log-exports-as-an-empty-vector
  (is (= [] (reader/read-string (export/log)))))
