(ns barereplay.view
  "The dock as a template, and what it shows at a position."
  (:require [baremirror.alpha.template :as template]
            [barereplay.label :as label]))

(def icons
  "The path of each icon of the dock, by name."
  {:start    "M6 6h2v12H6zm3.5 6l8.5 6V6z"
   :prev     "M15.41 16.59L10.83 12l4.58-4.59L14 6l-6 6 6 6z"
   :next     "M8.59 16.59L13.17 12 8.59 7.41 10 6l6 6-6 6z"
   :end      "M6 18l8.5-6L6 6v12zM16 6v12h2V6z"
   :collapse "M6 13h12v-2H6z"
   :expand   "M12 15.5L5.5 9 7 7.5l5 5 5-5L18.5 9z"})

(defn icon-class
  "The class that shows the icon `icon`."
  [icon]
  (str "icon-" (name icon)))

(def log-part
  "The part name of the log."
  "timeline")

(defn- nav-button
  "A ghost icon button, the part `part`, disabled by the hole `disabled-hole`."
  [part label icon disabled-hole]
  [:x-button {:class "nav" :variant "ghost" :data-x-part part :label label :disabled disabled-hole}
   [:span {:class (str "icon " (icon-class icon))}]])

(def dock
  (template/split
   :dock
   [:x-floating-panel {:class          "panel"
                       :open           true
                       :label          "BareReplay"
                       :data-x-part    "panel"
                       :data-collapsed :collapsed?}
    [:div {:class "head" :slot "header"}
     [:span {:class "title"} "BareReplay"]
     [:x-button {:class        "nav"
                 :variant      "ghost"
                 :data-x-part  "collapse"
                 :data-no-drag true
                 :label        :collapse-label}
      [:span {:data-x-part "collapse-icon" :class :collapse-icon}]]]
    [:div {:class "body"}
     [:div {:class "status"}
      [:span {:class "badge" :data-x-part "badge" :data-live :live?} :badge]
      [:span {:class "count" :data-x-part "count"} :count]]
     [:div {:class "event" :data-x-part "event"} :event]
     [:div {:class "history"} "History"]
     [:div {:class "transport"}
      (nav-button "start" "Jump to start" :start :at-start?)
      (nav-button "prev" "Step back" :prev :at-start?)
      [:x-slider {:class       "slider"
                  :data-x-part "slider"
                  :min         "0"
                  :step        "1"
                  :aria-label  "History"
                  :max         :max
                  :value       :value}]
      (nav-button "next" "Step forward" :next :at-end?)
      (nav-button "end" "Jump to live" :end :at-end?)]
     [:x-timeline {:class "log" :data-x-part log-part :position "end"}]
     [:details {:class "data"}
      [:summary "Data"]
      [:x-code {:class       "data-code"
                :data-x-part "data"
                :language    "json"
                :show-copy   true
                :wrap        true
                :max-lines   "12"
                :props       {:code :code}}]]]]))

(defn- collapse-affordance
  "What the collapse button offers next, given the panel's current state."
  [collapsed?]
  (if collapsed?
    {:label "Expand panel"   :icon :expand}
    {:label "Collapse panel" :icon :collapse}))

(defn- data-json [entries n]
  (if-let [d (label/detail-at entries n)]
    (js/JSON.stringify (clj->js d) nil 2)
    "No request/response at this step."))

(defn item
  "What the dock shows at position `n` of `entries`, as one flat value for `dock`."
  [entries {:keys [n collapsed?]}]
  (let [{:keys [live? total event]} (label/status entries n)
        {text :label icon :icon}    (collapse-affordance collapsed?)]
    {:live?          live?
     :badge          (if live? "LIVE" "REPLAYING")
     :count          (str n " / " total)
     :event          (if event (label/event->label event) "")
     :collapsed?     collapsed?
     :collapse-label text
     :collapse-icon  (str "icon " (icon-class icon))
     :at-start?      (<= n 0)
     :at-end?        (>= n total)
     :max            (str total)
     :value          (str n)
     :code           (data-json entries n)}))

(defn writes
  "The writes that bring the dock to position `n` of `entries`, by part name."
  [entries seen]
  (template/writes dock (item entries seen)))
