(ns factorio-blueprint-tools.core
  (:require [factorio-blueprint-tools.tile :as tile]
            [factorio-blueprint-tools.mirror :as mirror]
            [factorio-blueprint-tools.upgrade :as upgrade]
            [factorio-blueprint-tools.preview :as preview]
            [factorio-blueprint-tools.serialization :as ser]
            [antizer.rum :as ant]
            [rum.core :as rum]
            [citrus.core :as citrus]))

(enable-console-print!)

(def ta-no-spellcheck
  {:autoComplete "off"
   :autoCorrect "off"
   :autoCapitalize "off"
   :spellCheck "false"})

(def blueprint-state
  {::blueprint-error nil ; maybe an error
   ::blueprint nil ; the blueprint, unless there is an error
   })

(defn build-blueprint-watch
  [watch-name blueprint-string-atom blueprint-target-atom]
  (add-watch blueprint-string-atom watch-name
             (fn [_ _ _ blueprint-string]
               (letfn [(update-fn
                         [blueprint error]
                         (swap! blueprint-target-atom assoc
                                ::blueprint blueprint
                                ::blueprint-error error))]
                 (if (seq blueprint-string)
                   (try
                     (update-fn (ser/decode blueprint-string) nil)
                     (catch :default e
                       (update-fn nil (str "Could not load blueprint.  Please make sure to copy and paste the whole string from Factorio. (Error: " e ")"))))
                   (update-fn nil nil))))))

(defn blueprint-state-cursors
  "Create rum/cursors for ::blueprint, ::blueprint-error, and each optional key passed and returns as vector in that order"
  [state & ks]
  (let [ks (concat [::blueprint ::blueprint-error] ks)]
    (mapv #(rum/cursor state %) ks)))

(defn form-item-input-blueprint
  [state]
  (ant/form-item {:label "Blueprint string"
                  :help "Copy a blueprint string from Factorio and paste it in this field"}
                 (ant/input-text-area (assoc ta-no-spellcheck
                                             :value (rum/react state)
                                             :onChange #(reset! state (-> % .-target .-value))
                                             :onFocus #(.select (-> % .-target))))))

(defn form-item-output-blueprint
  [state]
  (ant/form-item {:label "Result"
                  :help "Copy this blueprint string and import in from the blueprint library in Factorio"}
                 (ant/input-text-area (assoc ta-no-spellcheck
                                             :value (rum/react state)
                                             :onFocus #(.select (-> % .-target))))))

(defn alert-error
  [error-message]
  (ant/alert {:message error-message
              :showIcon true
              :type "error"}))

;;; About

(rum/defc ContentAbout < rum/static
  []
  (ant/layout-content
   {:style {:padding "1ex 1em"}}
   [:h2 "Random tools to manipulate Factorio blueprint strings"]
   [:p "While there are already some of those functions built as mods to the game, one can not use mods while playing for the achievements"]
   [:h3 "Instructions"]
   [:p "Pick a tool on the left hand side in the menu:"]
   [:ul
    [:li [:em "Tile"] ": Arrange copies of the blueprint in a grid.  E.g. take a six electric miner blueprint and tile 15x15 to cover even the biggest resource fields"]
    [:li [:em "Mirror"] ": Mirror the blueprint either vertically or horizontally"]
    [:li [:em "Upgrade"] ": Decide what common upgradeable entities (e.g. inserters) to upgrade.  Also supports downgrading (e.g. you have a great blueprint but not the tech yet)"]]
   [:p "Then paste the blueprint string either from the game or from a different place into the input field, adjust the settings, and finally copy the final blueprint and import it into Factorio"]))

;;; Settings 

(rum/defc ContentSettings < rum/static
  []
  (ant/layout-content
   {:style {:padding "1ex 1em"}}
   [:h2 "Settings"]
   (ant/alert {:message "Currently there is no way to change or add mods etc. for the sizes occupied by the entities."
               :showIcon true
               :type "warning"})
   (ant/form
    (ant/form-item {:label "Factorio entities"}
                   (ant/select {:value "vanilla-0.16"}
                               (ant/select-option {:key "vanilla-0.16"} "Vanilla 0.16"))))))

;;; Tile

(defonce blueprint-tile-state
  (atom ""))

(defonce tile-settings-state
  (atom
   (assoc blueprint-state
          ::tile-x 2 ; initial values for the tiling
          ::tile-y 2)))

(defonce update-blueprint-tile-watch
  (build-blueprint-watch ::update-blueprint-tile blueprint-tile-state tile-settings-state))

(defonce tile-result-state
  (rum/derived-atom [tile-settings-state] ::tile-result
                    (fn [{::keys [blueprint tile-x tile-y] :as tile-settings}]
                      (some-> blueprint (tile/tile tile-x tile-y)))))

(defonce tile-result-serialized-state
  (rum/derived-atom [tile-result-state] ::tile-result-serialized #(some-> % ser/encode)))

(rum/defcs ContentTile <
  rum/reactive
  []
  (let [[blueprint blueprint-error tile-x tile-y] (blueprint-state-cursors tile-settings-state ::tile-x ::tile-y)]
    (ant/layout-content
     {:style {:padding "1ex 1em"}}
     [:h2 "Tile a blueprint"]
     (ant/form
      (form-item-input-blueprint blueprint-tile-state)
      (when-let [error-message (rum/react blueprint-error)]
        (alert-error error-message)))
     (when (rum/react blueprint)
       [:div
        (ant/form
         (ant/form-item {:label "Tiles on X axis"}
                        (ant/input-number {:value (rum/react tile-x)
                                           :onChange #(reset! tile-x %)
                                           :min 1}))
         (ant/form-item {:label "Tiles on Y axis"}
                        (ant/input-number {:value (rum/react tile-y)
                                           :onChange #(reset! tile-y %)
                                           :min 1}))
         (form-item-output-blueprint tile-result-serialized-state))
        (preview/preview (rum/react tile-result-state))]))))

;;; Mirror

(defonce blueprint-mirror-state
  (atom ""))

(defonce mirror-settings-state
  (atom
   (assoc blueprint-state
          ::direction :vertically)))

(defonce update-blueprint-mirror-watch
  (build-blueprint-watch ::update-blueprint-mirror blueprint-mirror-state mirror-settings-state))

(defonce mirror-result-state
  (rum/derived-atom [mirror-settings-state] ::mirror-result
                    (fn [{::keys [blueprint direction] :as mirror-settings}]
                      (some-> blueprint (mirror/mirror direction)))))

(defonce mirror-result-serialized-state
  (rum/derived-atom [mirror-result-state] ::mirror-result-serialized #(some-> % ser/encode)))

(rum/defcs ContentMirror <
  rum/reactive
  []
  (let [[blueprint blueprint-error direction] (blueprint-state-cursors mirror-settings-state ::direction)]
    (ant/layout-content
     {:style {:padding "1ex 1em"}}
     [:h2 "Mirror a blueprint"]
     (ant/form
      (form-item-input-blueprint blueprint-mirror-state)
      (when-let [error-message (rum/react blueprint-error)]
        (alert-error error-message)))
     (when (rum/react blueprint)
       [:div
        (ant/form
         (ant/form-item {:label "Direction"}
                        (ant/radio-group {:value (rum/react direction)
                                          :onChange #(reset! direction (-> % .-target .-value keyword))}
                                         (for [[option label] [[:vertically "Vertically"] [:horizontally "Horizontally"]]]
                                           (ant/radio {:key option :value option} label))))
         (form-item-output-blueprint mirror-result-serialized-state))
        (preview/preview (rum/react mirror-result-state))]))))

;;; Upgrade

(defonce blueprint-upgrade-state
  (atom ""))

(defonce upgrade-settings-state
  (atom
   (assoc blueprint-state
          ::upgrade-config upgrade/default-upgrade-config)))

(defonce update-blueprint-upgrade-watch
  (build-blueprint-watch ::update-blueprint-upgrade blueprint-upgrade-state upgrade-settings-state))

(defonce upgrade-result-state
  (rum/derived-atom [upgrade-settings-state] ::upgrade-result
                    (fn [{::keys [blueprint upgrade-config] :as upgrade-settings}]
                      (some->> blueprint (upgrade/upgrade-blueprint upgrade-config)))))

(defonce upgrade-result-serialized-state
  (rum/derived-atom [upgrade-result-state] ::upgrade-result-serialized #(some-> % ser/encode)))

(rum/defcs ContentUpgrade <
  rum/reactive
  []
  (let [[blueprint blueprint-error upgrade-config] (blueprint-state-cursors upgrade-settings-state ::upgrade-config)]
    (ant/layout-content
     {:style {:padding "1ex 1em"}}
     [:h2 "Upgrade (or downgrade) a blueprint"]
     (ant/form
      (form-item-input-blueprint blueprint-upgrade-state)
      (when-let [error-message (rum/react blueprint-error)]
        (alert-error error-message)))
     (when-let [blueprint (rum/react blueprint)]
       [:div
        (let [upgradable (upgrade/upgradeable-from-blueprint blueprint)
              order (filter upgradable upgrade/upgrades-order)
              cfg (rum/react upgrade-config)]
          (ant/form
           (for [from order]
             (ant/form-item {:label (upgrade/upgrades-names from)}
                            (ant/radio-group {:value (cfg from)
                                              :onChange #(swap! upgrade-config assoc from (-> % .-target .-value))}
                                             (for [option (upgrade/upgrades-by-key from)]
                                               (ant/radio {:key option :value option} (upgrade/upgrades-names option))))))
           (form-item-output-blueprint upgrade-result-serialized-state)))
        (preview/preview (rum/react upgrade-result-state))]))))

;;; Main

(def navigations
  [{:key "about" :icon "info-circle-o" :title "About" :component ContentAbout}
   {:key "tile" :icon "appstore-o" :title "Tile" :component ContentTile}
   {:key "mirror" :icon "swap" :title "Mirror" :component ContentMirror}
   {:key "upgrade" :icon "tool" :title "Upgrade" :component ContentUpgrade}
   {:key "settings " :icon "setting" :title "Settings" :component ContentSettings}])

(def navigations-by-key
  (into {} (map (juxt :key identity)) navigations))

;;; Controller (might end up in a differnt file

; Navigation

(defmulti navigation identity)

(defmethod navigation :init []
  {:state (-> navigations first :key)})

(defmethod navigation :goto [_ [target] _]
  {:state target})

;

(defonce reconciler
  (citrus/reconciler
   {:state (atom {})
    :controllers {:navigation navigation}}))

;;; Main content

(defn- menu-item
  [{:keys [key icon title]}]
  (ant/menu-item {:key key} [:span (ant/icon {:type icon}) title]))

(rum/defc AppHeader < rum/static []
  (ant/layout-header
   {:style {:padding-left "24px"}}
   [:h1
    {:style {:color "white"}}
    (ant/icon {:type "setting"})
    "Factorio Blueprint Tools"]))

(rum/defc AppFooter < rum/static []
  (ant/layout-footer
   {:style {:text-align "center"}}
   [:span
    "Copyright © 2018 Christoph Frick"
    " "
    [:a {:href "https://github.com/christoph-frick/factorio-blueprint-tools"} "https://github.com/christoph-frick/factorio-blueprint-tools"]]))

(rum/defc App < rum/reactive
  [r]
  (ant/layout {:style {:min-height "100vh"}}
              (AppHeader)
              (ant/layout (ant/layout-sider
                           {:theme "light"}
                           (ant/menu {:theme "light"
                                      :mode "inline"
                                      :selectedKeys [(rum/react (citrus/subscription r [:navigation]))]
                                      :onSelect #(citrus/dispatch! r :navigation :goto (.-key %))
                                      :style {:min-height "calc(100vh-64px)"}}
                                     (map menu-item navigations)))
                          (ant/layout
                           (let [nav-key (rum/react (citrus/subscription r [:navigation]))]
                             (if-let [nav-item (navigations-by-key nav-key)]
                               ((:component nav-item))
                               (do
                                 (ContentAbout)
                                 (ant/message-error (str "Unknown navigation target: " nav-key)))))
                           (AppFooter)))))

(defonce init-ctrl
  (citrus/broadcast-sync! reconciler :init))

(defn init!
  []
  (rum/mount (App reconciler) (js/document.getElementById "app")))

(init!)

(defn on-js-reload [])
