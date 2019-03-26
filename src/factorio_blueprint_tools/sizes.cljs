(ns factorio-blueprint-tools.sizes
  (:require-macros [factorio-blueprint-tools.macros :as m]))

(def selection-boxes
  (m/load-edn "selection-boxes.edn"))

(def selection-box-fallback [[-0.5 -0.5] [0.5 0.5]])

(defn selection-box
  [entity-name]
  (let [entity-kw (keyword entity-name)]
    (if (contains? selection-boxes entity-kw)
      (selection-boxes entity-kw)
      (do
        (js/console.warn "Unknown entity" entity-name)
        selection-box-fallback))))
