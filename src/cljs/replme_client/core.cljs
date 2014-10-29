(ns replme-client.core
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [clojure.string :as str]
            [cljs.core.async :refer [<! >! put! chan]])
  (:require-macros [cljs.core.async.macros :refer [go-loop]]))

(enable-console-print!)

(defonce app-state (atom {:text "Hello Chestnut!"
                          :github-repo ""
                          :repl [{:input "(+ 1 2 3)" :output "6"}
                                 {:input "(/ 10 2)" :output "5"}]}))

(def key-map {37 :left
              39 :right
              38 :up
              8  :delete})

(defn repl
  "Om component for new repl"
  [data owner]
  (reify
    om/IWillMount
    (will-mount [_]
      (let [key-chan (om/get-state owner :key-chan)]
        (.addEventListener js/window "keydown"
                           (fn [e]
                             (let [key-code (.-which e)]
                               (when (= (key-map key-code) :delete) (.preventDefault e))
                               (put! key-chan key-code))))
        (go-loop [key (<! key-chan)]
                 (let [pre-input (om/get-state owner :pre-input)
                       post-input (om/get-state owner :post-input)]
                   (case (key-map key) ;; TODO: move these to separate handlers
                     :left ((om/set-state! owner :pre-input
                                           (drop-last pre-input))
                            (om/set-state! owner :post-input
                                           (cons (last pre-input) post-input)))
                     :right ((om/set-state! owner :post-input
                                            (rest post-input))
                             (om/set-state! owner :pre-input
                                            (concat pre-input (first post-input))))
                     :delete (om/set-state! owner :pre-input
                                            (drop-last (om/get-state owner :pre-input)))
                     :up (.log js/console "implement history here")

                     (om/set-state! owner :pre-input ;; TODO: why are all the letters uppercased???
                                                     ;; (.fromCharCode js/String key) the same...
                                    (concat pre-input (str/split (char key) "")))))
                 (recur (<! key-chan)))))
    om/IInitState
    (init-state [this]
      {:focus true
       :pre-input ["c" "o" "d" "e" "." "." "."]
       :post-input ["a" "f" "t" "e" "r"]
       :key-chan (chan)})
    om/IRenderState
    (render-state [_ state]
      (dom/div
       #js {:className "repl"}
       (dom/span #js {:className "pre-input"}
                 (apply str (:pre-input state)))
       (dom/span #js {:className "repl-cursor"
                      :dangerouslySetInnerHTML #js {:__html "&nbsp;"}})
       (dom/span #js {:className "post-input"}
                 (apply str (:post-input state)))))))

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

