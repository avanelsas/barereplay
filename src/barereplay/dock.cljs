(ns barereplay.dock
  (:require
   [barebuild.utils.url :as url]
   [baredom.utils.component :as comp]
   [baredom.utils.dom :as du]
   [baremirror.alpha.events :as events]
   [baremirror.alpha.parts :as parts]
   [baremirror.alpha.places :as places]
   [baremirror.alpha.template :as template]
   [barereplay.label :as label]
   [barereplay.reconstruct :as reconstruct]
   [barereplay.store :as store]
   [barereplay.view :as view]))

(def ^:private styles
  "
  :host { display: contents; }

  .panel::part(body) { scrollbar-gutter: stable; }

  .body {
  width: 100%;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
  }

  .status { display: flex; align-items: baseline; gap: 0.5rem; }

  .badge {
  font-weight: 700;
  font-size: 0.72rem;
  letter-spacing: 0.04em;
  padding: 0.12rem 0.45rem;
  border-radius: 0.35rem;
  color: #fff;
  background: var(--x-color-warning, #b45309);
  }

  .badge[data-live] { background: var(--x-color-success, #108c48); }

  .count {
  color: var(--x-color-text-muted, #64748b);
  font-variant-numeric: tabular-nums;
  }

  .event {
  color: var(--x-color-text-muted, #64748b);
  font-size: 0.7rem;
  line-height: 1.3;
  height: 2.6em;
  overflow: hidden;
  overflow-wrap: anywhere;
  }

  .history {
  font-size: 0.68rem;
  text-transform: uppercase;
  letter-spacing: 0.07em;
  color: var(--x-color-text-muted, #64748b);
  }

  .transport { display: flex; align-items: center; gap: 0.2rem; }
  .transport x-slider { flex: 1 1 auto; display: block; }
  .nav {
  flex: 0 0 auto;
  --x-button-padding-inline: 0.25rem;
  --x-button-height-md: 1.9rem;
  --x-button-ghost-bg-hover: rgba(127, 127, 127, 0.2);
  --x-button-ghost-bg-active: rgba(127, 127, 127, 0.32);
  }
  .icon {
  display: block;
  width: 15px;
  height: 15px;
  background-color: currentColor;
  -webkit-mask: var(--icon) center / contain no-repeat;
  mask: var(--icon) center / contain no-repeat;
  }

  .head { display: flex; align-items: center; gap: 0.5rem; }
  .head .title { flex: 1 1 auto; min-width: 0; }

  .panel[data-collapsed]::part(body) { display: none; }

  .log {
  display: block;
  max-height: 40vh;
  overflow-y: auto;
  scrollbar-gutter: stable;
  }

  .log x-timeline-item {
  --x-timeline-item-title-font-size: 0.75rem;
  --x-timeline-item-label-width: 0;
  }

  .data summary {
  cursor: pointer;
  user-select: none;
  color: var(--x-color-text-muted, #64748b);
  font-size: 0.68rem;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  }

  .data[open] summary { margin-bottom: 0.4rem; }
  .data-code { display: block; }

  @media (prefers-color-scheme: dark) {
  .count, .event, .history { color: var(--x-color-text-muted, #94a3b8); }
  }
  ")

(def ^:private tag "barereplay-dock")
(def ^:private k-state "__brDockState")
(def ^:private k-seen "__brDockSeen")
(def ^:private k-live-url "__brDockLiveUrl")

(defn- icon-rule
  "The style rule that gives the class of one icon its mask."
  [[icon path]]
  (str "." (view/icon-class icon) " { --icon: url(\"data:image/svg+xml,"
       (js/encodeURIComponent
        (str "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24'><path d='" path "'/></svg>"))
       "\"); }\n"))

(def ^:private style-node
  [:style (apply str styles (map icon-rule view/icons))])

(defn- project! [entries n]
  (doseq [{:keys [el value]} (vals (reconstruct/resources-at entries n))]
    (when (and el value)
      (.projectResource ^js el value))))

(def ^:private timeline-item
  (template/split :log-item [:x-timeline-item {:title :title :status :status}]))

(def ^:private make-timeline-item! (parts/node-maker (:fixed timeline-item)))

(defn- place-log-items!
  "Brings the items of `timeline` to one for each of `total` entries."
  [^js timeline total]
  (places/sync! {:log {:parent timeline}} {:log (mapv label/log-key (range total))}
                make-timeline-item!))

;; The items are keyed by their index, so the child at index `i` is the item of entry `i`.
(defn- write-log-item! [^js timeline entries n i]
  (parts/write-item! (.item (.-children timeline) i) timeline-item (label/log-item entries n i)))

(defn- scroll-active! [^js timeline n]
  (when (pos? n)
    (when-let [^js item (.item (.-children timeline) (dec n))]
      (.scrollIntoView item #js {:block "nearest"}))))

(defn- render-log!
  "Brings the log from `prev` to position `n`, writing only the items that change."
  [refs entries prev n]
  (let [^js timeline (refs view/log-part)
        total        (count entries)]
    (when-not (= total (:total prev))
      (place-log-items! timeline total))
    (run! (partial write-log-item! timeline entries n)
          (label/changed-log-indices prev total n))
    (scroll-active! timeline n)))

(defn- reconstructed-url
  "Reconstruct the current replay url from its pathname and params"
  [entries n]
  (url/build-scoped-url (.-search js/location)
                        (.-pathname js/location)
                        (update-vals (reconstruct/resources-at entries n)
                                     (comp :url-intent :value))))

(def url-changed-event
  "Dispatched on window after the dock rewrites the address bar, so anything drawing URL-derived
  state can re-read it. A consumer cannot learn this from its own resource: a render-key names
  facts the view carries, and a scrub that only moves the url moves no view. Deliberately not a
  popstate, which <server-resource> answers with a live refetch."
  "barereplay-url-changed")

(defn- replace-url!
  "Rewrite the address bar and tell the page it moved."
  [href]
  (.replaceState js/history nil "" href)
  (.dispatchEvent js/window (js/CustomEvent. url-changed-event)))

(defn- restore-live-url!
  "Put the url the replay borrowed back, if it is still holding one."
  [^js el]
  (when-let [saved (du/getv el k-live-url)]
    (replace-url! saved)
    (du/setv! el k-live-url nil)))

(defn- sync-url!
  "Update the URL during a replay. If n>=total we are back at the live url."
  [^js el entries n total]
  (if (>= n total)
    (restore-live-url! el)
    (do
      (when-not (du/getv el k-live-url)
        (du/setv! el k-live-url (str (.-pathname js/location) (.-search js/location))))
      (replace-url! (reconstructed-url entries n)))))

(defn- scrub-move?
  "True when the step moved to a real replay position, not merely the live tail advancing
   as new events arrive"
  [prev n total]
  (and (not= (:n prev) n)
       (not (and (= (:n prev) (:total prev)) (= n total)))))

(defn- render!
  "Draws the dock for one position."
  [^js el refs entries n]
  (let [total      (count entries)
        collapsed? (boolean (:collapsed? (du/getv el k-state)))
        seen       {:n n :total total :collapsed? collapsed?}
        prev       (du/getv el k-seen)]
    (when (not= seen prev)
      (when (scrub-move? prev n total)
        (sync-url! el entries n total)
        (project! entries n))
      (du/setv! el k-seen seen)
      ;; The collapse is written before the log, whose scroll cannot land while the body is
      ;; display:none.
      (parts/write-parts! refs (view/writes entries seen))
      (render-log! refs entries prev n))))

(defn- apply-at! [^js el refs entries n pinned?]
  (du/setv! el k-state (assoc (du/getv el k-state) :n n :pinned? pinned?))
  (render! el refs entries n))

(defn- go-in!
  "Moves the dock to position `n` of `entries`, clamped to them."
  [^js el refs entries n]
  (let [total (count entries)
        n'    (label/clamp n 0 total)]
    (apply-at! el refs entries n' (= n' total))))

(defn- go! [^js el refs n]
  (go-in! el refs (store/entries) n))

(defn- position-top-right! [^js panel]
  (js/requestAnimationFrame
   (fn []
     (let [margin   8
           ^js part (some-> (.-shadowRoot panel) (.querySelector "[part=panel]"))
           w        (if part (.-width (.getBoundingClientRect part)) 352)
           x        (max margin (- (.-innerWidth js/window) w margin))]
       (du/set-attr! panel "x" (str x))
       (du/set-attr! panel "y" (str margin))))))

(defn mount!
  "Mounts the replay dock"
  []
  (when-not (.querySelector js/document tag)
    (let [add! (fn [] (.appendChild (.-body js/document)
                                    (.createElement js/document tag)))]
      (if (.-body js/document)
        (add!)
        (.addEventListener js/document "DOMContentLoaded" add! #js {:once true})))))

(defn unmount!
  "unmounts the replay dock"
  []
  (when-let [^js el (.querySelector js/document tag)]
    (.remove el)))

(defn- current-n [^js el]
  (:n (du/getv el k-state)))

(defn- follow!
  "Redraws the dock for a changed log."
  [^js el refs entries]
  (let [{:keys [n pinned?]} (du/getv el k-state)]
    (go-in! el refs entries (if pinned? (count entries) n))))

(defn- toggle-collapsed!
  "Flip the collapse fact and redraw from it, the same shape every other gesture
   takes: move the state value, then let `render!` decide what the DOM owes it."
  [^js el refs]
  (du/setv! el k-state (update (du/getv el k-state) :collapsed? not))
  (render! el refs (store/entries) (current-n el)))

(defn- perform! [^js el refs [meaning :as message]]
  (if (= meaning :collapse)
    (toggle-collapsed! el refs)
    (when-some [n (view/position-for message (current-n el) (count (store/entries)))]
      (go! el refs n))))

(defn- wire! [^js el ^js panel]
  (let [refs (parts/read-parts panel)]
    (events/listen! panel {:dispatch! (partial perform! el refs) :events (:events view/dock)})
    (position-top-right! panel)
    (go! el refs (count (store/entries)))
    (store/subscribe! (partial follow! el refs))))

(defn- connected! [^js el]
  (let [root  (.attachShadow el #js {:mode "open"})
        panel (parts/make-node! (:fixed view/dock))]
    (.append root (parts/make-node! style-node) panel)
    (wire! el panel)))

(defn- disconnected! [^js el]
  (restore-live-url! el)
  (store/unsubscribe!))

(def ^:private element-opts
  "Declarative class options passed to component/register!. `:internal? true`
  keeps the dev-tool lifecycle hook from firing on the dock's own
  connect/disconnect. This prevents those records from polluting every trace.
  There are no observed attributes. The no-op attribute-changed-fn is mandatory but does not do anything
  (the callback will never fire)."
  {:internal?            true
   :observed-attributes  #js []
   :connected-fn         connected!
   :disconnected-fn      disconnected!
   :attribute-changed-fn (fn [_ _ _ _] nil)})

(defn register! []
  (comp/register! "barereplay-dock"
                  element-opts))
