(ns barereplay.view-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [barereplay.view :as view]))

(def ^:private entries
  [{:event   [:submit-write {:op :delete :id 7}]
    :effects [[:write {:method "DELETE" :url "/api/tasks/7"}]]}
   {:event   [:connected {}]
    :effects []}])

(defn- disabled [writes]
  (update-vals (select-keys writes ["start" "prev" "next" "end"]) (comp :disabled :attrs)))

(deftest writes-show-live-or-replaying
  (testing "the end of the log is live"
    (let [w (view/writes entries {:n 2 :collapsed? false})]
      (is (= {:text "LIVE" :attrs {:data-live true}} (w "badge")))
      (is (= {:text "2 / 2"} (w "count")))
      (is (= {:text "connected"} (w "event")))))
  (testing "a position before the end is a replay"
    (let [w (view/writes entries {:n 1 :collapsed? false})]
      (is (= {:text "REPLAYING" :attrs {:data-live false}} (w "badge")))
      (is (= {:text "write delete #7"} (w "event")))))
  (testing "position 0 shows no event"
    (is (= {:text ""} ((view/writes entries {:n 0 :collapsed? false}) "event")))))

(deftest writes-disable-the-moves-a-position-cannot-make
  (is (= {"start" true "prev" true "next" false "end" false}
         (disabled (view/writes entries {:n 0 :collapsed? false}))))
  (is (= {"start" false "prev" false "next" false "end" false}
         (disabled (view/writes entries {:n 1 :collapsed? false}))))
  (is (= {"start" false "prev" false "next" true "end" true}
         (disabled (view/writes entries {:n 2 :collapsed? false})))))

(deftest writes-put-the-position-on-the-slider
  (is (= {:attrs {:max "2" :value "1"}} ((view/writes entries {:n 1 :collapsed? false}) "slider"))))

(deftest writes-offer-the-collapse-the-panel-is-not-in
  (let [open   (view/writes entries {:n 2 :collapsed? false})
        closed (view/writes entries {:n 2 :collapsed? true})]
    (is (= {:attrs {:data-collapsed false}} (open "panel")))
    (is (= {:attrs {:label "Collapse panel"}} (open "collapse")))
    (is (= {:attrs {:data-collapsed true}} (closed "panel")))
    (is (= {:attrs {:label "Expand panel"}} (closed "collapse")))
    (is (not= (open "collapse-icon") (closed "collapse-icon")))))

(deftest writes-show-the-request-and-response-of-a-position
  (is (= {:props {:code "{\n  \"request\": {\n    \"method\": \"DELETE\",\n    \"url\": \"/api/tasks/7\"\n  }\n}"}}
         ((view/writes entries {:n 1 :collapsed? false}) "data")))
  (is (= {:props {:code "No request/response at this step."}}
         ((view/writes entries {:n 2 :collapsed? false}) "data"))))
