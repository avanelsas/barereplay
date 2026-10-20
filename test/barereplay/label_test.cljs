(ns barereplay.label-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [barereplay.label :as label]))

(deftest labels
  (is (= "response accepted" (label/event->label [:response {:outcome :accepted}])))
  (is (= "write delete #7"  (label/event->label [:submit-write {:op :delete :id 7}]))))

(deftest detail-at-extracts-request-and-response
  (let [entries [{:event   [:submit-write {:op :delete :id 7}]
                  :effects [[:notify-consumers {}] [:write {:method "DELETE" :url "/api/tasks/7"}]]}
                 {:event   [:response {:outcome :accepted}]
                  :effects [[:notify-consumers {}]]}
                 {:event   [:connected {}]
                  :effects [[:notify-consumers {}]]}]]
    (is (nil? (label/detail-at entries 0)))
    (is (= {:request {:method "DELETE" :url "/api/tasks/7"}} (label/detail-at entries 1)))
    (is (= {:response {:outcome :accepted}} (label/detail-at entries 2)))
    (is (nil? (label/detail-at entries 3)))))

(deftest item-status-marks-past-present-future
  (is (= "complete" (label/item-status 1 3)))
  (is (= "active" (label/item-status 3 3)))
  (is (= "pending" (label/item-status 4 3))))

(deftest log-item-titles-and-marks-an-entry
  (let [entries [{:event [:connected {}]} {:event [:response {:outcome :accepted}]}]]
    (is (= {:title "connected" :status "complete"} (label/log-item entries 2 0)))
    (is (= {:title "response accepted" :status "active"} (label/log-item entries 2 1)))))

(deftest changed-log-indices-names-the-items-a-move-changes
  (testing "every index with no previous view"
    (is (= [0 1 2] (seq (label/changed-log-indices nil 3 3)))))
  (testing "every index when the log shrinks"
    (is (= [0 1] (seq (label/changed-log-indices {:n 5 :total 5} 2 2)))))
  (testing "the items between the two positions on a move"
    (is (= [2 3 4] (seq (label/changed-log-indices {:n 3 :total 9} 9 5))))
    (is (= [2 3 4] (seq (label/changed-log-indices {:n 5 :total 9} 9 3)))))
  (testing "the old active item and the new items on an append at live"
    (is (= [4 5] (seq (label/changed-log-indices {:n 5 :total 5} 6 6)))))
  (testing "only the new items on an append away from live"
    (is (= [5] (seq (label/changed-log-indices {:n 2 :total 5} 6 2)))))
  (testing "no item when nothing moves"
    (is (empty? (label/changed-log-indices {:n 2 :total 5} 5 2))))
  (testing "a move from the start"
    (is (= [0] (seq (label/changed-log-indices {:n 0 :total 5} 5 1))))))

(deftest clamp-bounds
  (is (= 0 (label/clamp -2 0 5)))
  (is (= 5 (label/clamp 9 0 5)))
  (is (= 3 (label/clamp 3 0 5))))
