(ns replme-client.core
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]))

(defonce app-state (atom {:text "Hello Chestnut!"
                          :github-repo ""
                          :repl [{:input "(+ 1 2 3)" :output "6"}
                                 {:input "(/ 10 2)" :output "5"}]}))

(defn repl
  "Om component for new repl"
  [data owner]
  (reify
    om/IWillMount
    (will-mount [_]
      (.addEventListener js/window "keydown"
                         (fn [e] (.log js/console (.-keyCode e)))))
    om/IInitState
    (init-state [this]
      {:focus true :input "CODE GOES HERE"})
    om/IRenderState
    (render-state [_ state]
      (dom/div
       #js {:className "repl"}
       (dom/span nil
                 (:input state))
       (dom/span #js {:className "repl-cursor"
                      :dangerouslySetInnerHTML #js {:__html "&nbsp;"}})))))

(defn site-logo
  "Om component for new site-logo"
  [data owner]
  (reify
    om/IRender
    (render [_]
      (dom/a #js {:href "/"}
             (dom/img #js {:src "images/replme-logo.png"
                           :className "site-logo"})))))

(defn repo-input
  "Om component for new repo-input"
  [data owner]
  (reify
    om/IRender
    (render [_]
      (dom/input #js {:type "text"
                      :className "repo-input"
                      :placeholder "username/repo"}))))

(defn top-nav
  "Om component for new top-nav"
  [data owner]
  (reify
    om/IDisplayName
    (display-name [this] "repo-input")
    om/IRender
    (render [_]
      (dom/div #js {:className "navigation"}
               (om/build site-logo data)
               (om/build repo-input data)))))

(defn main []
  (om/root
   (fn [app owner]
     (reify
       om/IRender
       (render [_]
         (dom/div nil
                  (om/build top-nav app)
                  (om/build repl app)))))
   app-state
   {:target (. js/document (getElementById "app"))}))
