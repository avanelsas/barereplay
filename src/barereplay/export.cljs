(ns barereplay.export
  "The recorded log as a value that can leave the page."
  (:require [barereplay.store :as store]))

;; A step carries `:el`, the live element it drove. An element is not a value, so
;; a step holding one cannot be written down.
(defn without-elements
  "`steps`, each without its live element."
  [steps]
  (mapv (fn [step] (dissoc step :el)) steps))

;; EDN, not JSON. The protocol vocabulary is keywords and opaque domain keys are
;; strings, a distinction `JSON.stringify` over `clj->js` erases.
;;
;; No `^:export` here. Exporting a symbol pins it and everything it reaches
;; against elimination, so an app that never calls this would still carry it.
(defn log
  "The recorded log as EDN, without the live elements."
  []
  (pr-str (without-elements (store/entries))))
