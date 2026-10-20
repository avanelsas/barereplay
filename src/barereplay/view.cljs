(ns barereplay.view
  "What the dock shows at a position, as writes by part name."
  (:require [barereplay.label :as label]))

(def ^:private svg-collapse "M6 13h12v-2H6z")
(def ^:private svg-expand   "M12 15.5L5.5 9 7 7.5l5 5 5-5L18.5 9z")

(defn collapse-affordance
  "What the collapse button offers next, given the panel's current state."
  [collapsed?]
  (if collapsed?
    {:label "Expand panel"   :path svg-expand}
    {:label "Collapse panel" :path svg-collapse}))

(defn- data-json [entries n]
  (if-let [d (label/detail-at entries n)]
    (js/JSON.stringify (clj->js d) nil 2)
    "No request/response at this step."))

(defn- status-writes [entries n]
  (let [{:keys [live? total event]} (label/status entries n)]
    {"badge" {:text (if live? "LIVE" "REPLAYING") :attrs {:data-live live?}}
     "count" {:text (str n " / " total)}
     "event" {:text (if event (label/event->label event) "")}}))

(defn- collapse-writes [collapsed?]
  (let [{text :label path :path} (collapse-affordance collapsed?)]
    {"panel"         {:attrs {:data-collapsed collapsed?}}
     "collapse"      {:attrs {:label text}}
     "collapse-icon" {:attrs {:d path}}}))

(defn- transport-writes [n total]
  (let [at-start? (<= n 0)
        at-end?   (>= n total)]
    {"start"  {:attrs {:disabled at-start?}}
     "prev"   {:attrs {:disabled at-start?}}
     "next"   {:attrs {:disabled at-end?}}
     "end"    {:attrs {:disabled at-end?}}
     "slider" {:attrs {:max (str total) :value (str n)}}}))

(defn writes
  "The writes that bring the dock to position `n` of `entries`, by part name."
  [entries {:keys [n collapsed?]}]
  (merge (status-writes entries n)
         (collapse-writes collapsed?)
         (transport-writes n (count entries))
         {"data" {:props {:code (data-json entries n)}}}))
