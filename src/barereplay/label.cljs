(ns barereplay.label)

(defn event->label
  "A short readable label for a recorded event."
  [[k payload]]
  (case k
    :connected       "connected"
    :intent-patch    (str "intent " (:query-patch payload))
    :url-changed     (str "url " payload)
    :response        (str "response " (name (:outcome payload)))
    :submit-write    (str "write " (name (:op payload))
                          (when (:id payload) (str " #" (:id payload))))
    :write-ack       (str "write-ack " (name (:outcome payload)))
    :write-failed    "write failed"
    :network-failed  "network failed"
    :protocol-failed "protocol failed"
    :disconnected    "disconnected"
    (str k)))

(defn status [entries n]
  (let [total (count entries)]
    {:live? (>= n total) :n n :total total
     :event (when (pos? n) (:event (nth entries (dec n))))}))

(defn detail-at [entries n]
  (when (pos? n)
    (let [{:keys [event effects]} (nth entries (dec n))
          [k payload] event
          request  (some (fn [[fx m]] (when (or (= fx :write) (= fx :fetch)) m)) effects)
          response (when (or (= k :response) (= k :write-ack)) payload)]
      (cond-> nil
        request  (assoc :request request)
        response (assoc :response response)))))

(defn clamp [n lo hi]
  (max lo (min hi n)))

(defn item-status [idx n]
  (cond (= idx n) "active"
        (< idx n) "complete"
        :else     "pending"))

(defn log-key
  "The key of the log item at index `i`."
  [i]
  (str i))

(defn log-item
  "The title and status of the log item at index `i`, at position `n`."
  [entries n i]
  {:title  (event->label (:event (nth entries i)))
   :status (item-status (inc i) n)})

(defn- moved-indices
  "The indices of the log items whose status a move from position `a` to `b` changes."
  [a b]
  (if (= a b)
    []
    (range (max 0 (dec (min a b))) (max a b))))

(defn changed-log-indices
  "The indices of the log items that differ between `prev` and position `n` of
   `total` entries, in order. With no `prev`, or fewer entries than `prev`, every index."
  [{prev-n :n prev-total :total} total n]
  (if (or (nil? prev-total) (< total prev-total))
    (range total)
    (into (sorted-set) (concat (moved-indices prev-n n) (range prev-total total)))))
