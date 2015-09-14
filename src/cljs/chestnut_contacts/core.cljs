(ns chestnut-contacts.core
  (:require-macros
   [cljs.core.async.macros :as asyncm :refer (go go-loop)])

  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [cljs.core.async :as async :refer (<! >! put! chan)]
            [taoensso.sente  :as sente :refer (cb-success?)]
            ))

(enable-console-print!)

(let [{:keys [chsk ch-recv send-fn state]}
      (sente/make-channel-socket! "/chsk" ; Note the same path as before
                                  {:type :auto ; e/o #{:auto :ajax :ws}
                                   })]
  (def chsk       chsk)
  (def ch-chsk    ch-recv) ; ChannelSocket's receive channel
  (def chsk-send! send-fn) ; ChannelSocket's send API fn
  (def chsk-state state)   ; Watchable, read-only atom
  )

(defonce app-state (atom {:text "Hello Chestnut!"
                          :contacts []}))

(defn contacts []
  (om/ref-cursor (:contacts (om/root-cursor app-state))))

(defn editable-text-view [state owner {:keys [state-key]}]
  (reify
    om/IInitState
    (init-state [_]
      {:editable false
       :temp-value nil})
    om/IDidUpdate
    (did-update [_ _ {:keys [editable]}]
      (when (and (not editable)
                 (om/get-state owner :editable))
        (let [node (om/get-node owner)]
          (.focus node)
          (.setSelectionRange node 0 (count (state-key state))))))

    om/IRenderState
    (render-state [_ {:keys [editable temp-value]}]
      (let [on-blur (fn []
                      (om/set-state! owner :editable false))
            commit (fn []
                   (om/update! state state-key temp-value)
                   (om/set-state! owner :editable false))
            on-change (fn [e]
                        (om/set-state! owner :temp-value (.. e -target -value)))
            on-key-down (fn [e]
                          (let [key (.-key e)]
                            (case key
                              "Escape" (on-blur)
                              "Enter" (commit)
                              nil)))
            ]
        (if editable
          (dom/input #js {:value temp-value
                          :onChange on-change
                          :onKeyDown on-key-down
                          :onBlur on-blur})
          (dom/div #js {:onClick (fn [_]
                                   (om/set-state! owner :temp-value (state-key state))
                                   (om/set-state! owner :editable true))
                        :style #js {:textDecoration "underline"
                                    :cursor "pointer"}}
                   (state-key state)))))))

(defn contact-view [contact owner]
  (om/component
   (dom/div nil
            (om/build editable-text-view contact {:opts {:state-key :name}})
            (om/build editable-text-view contact {:opts {:state-key :email}})
            (dom/button #js {:onClick (fn [_]
                                        (om/transact! (contacts)
                                                      (fn [cs]
                                                        (vec (remove #(= (:id %) (:id contact))
                                                                     cs)))))}
                        "delete"))))

(defn contacts-list-view [contacts owner]
  (om/component
   (dom/div nil
            (om/build-all contact-view contacts)
            (dom/button #js {:onClick (fn [e]
                                        (chsk-send! [:contacts/fetch nil]
                                                    1000
                                                    (fn [response]
                                                      (println "CALLBACK")
                                                      (if (= response :chsk/timeout)
                                                        (println "SERVER DIDN'T RESPONDE IN TIME.")
                                                        (do
                                                          (om/transact! contacts
                                                                        (fn [_]
                                                                          response)))
                                                        ))))}
                        "Fetch contacts"))))

(defn app-view [state owner]
  (om/component
   (dom/div nil
            (om/build contacts-list-view (:contacts state)))))

(defn main []
  (om/root
   app-view
   app-state
   {:target (. js/document (getElementById "app"))}))