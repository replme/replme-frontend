(ns replme-client.core
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [clojure.string :as str]
            [cljs.core.async :refer [<! >! put! chan]])
  (:require-macros [cljs.core.async.macros :refer [go-loop]]))

(enable-console-print!)

;; TODO:
;; refactor the repl component and break apart pure fns
;; remove event listeners on compoment teardown

(def app-state (atom {:github-repo ""
                      :namespace "user"
                      :repl [{:input "(+ 1 2 3)" :output "6"}
                             {:input "(/ 10 2)" :output "5"}]}))

(def move-keys {37 :left
                39 :right
                38 :up
                8  :delete
                32 :space
                13 :enter})

(defn repl-log
  [data owner]
  (reify
    om/IWillMount
    (will-mount [_]
      (println (:input data)))
    om/IRender
    (render [_]
      (dom/div nil
               (dom/p #js {:className "repl-log-input"}
                      (:input data))
               (dom/p #js {:className "repl-log-output"}
                      (:output data))))))

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
                               (when (some #{key-code} (keys move-keys))
                                 (.preventDefault e)
                                 (put! key-chan key-code)))))

        (.addEventListener js/window "keypress"
                           (fn [e]
                             (let [key-code (.-which e)]
                               (put! key-chan key-code))))

        (go-loop [key (<! key-chan)]
                 (let [pre-input (om/get-state owner :pre-input)
                       post-input (om/get-state owner :post-input)]
                   (case (move-keys key) ;; TODO: move these to separate handlers
                     :left ((om/set-state! owner :pre-input
                                           (drop-last pre-input))
                            (om/set-state! owner :post-input
                                           (cons (last pre-input) post-input)))

                     :right ((om/set-state! owner :post-input
                                            (rest post-input))
                             (om/set-state! owner :pre-input
                                            (concat pre-input (first post-input))))

                     :delete (om/set-state! owner :pre-input
                                            (drop-last pre-input))

                     :enter (let [code-to-eval (apply str (concat pre-input post-input))]
                              (om/transact! data :repl
                                            #(conj % {:input code-to-eval
                                                      :output "^^^ eval that here"}))
                              (om/set-state! owner :pre-input nil)
                              (om/set-state! owner :post-input nil))

                     :up (.log js/console "implement history here")

                     (om/set-state! owner :pre-input
                                    (concat pre-input (str/split (char key) "")))))
                 (recur (<! key-chan)))))

    om/IInitState
    (init-state [this]
      {:focus true
       :pre-input []
       :post-input []
       :key-chan (chan)})

    om/IRenderState
    (render-state [_ state]
      (dom/div
       #js {:className "repl" :style #js {:whiteSpace "pre"}}
       (dom/span #js {:className "namespace"}
                 (str (:namespace data) "=>"))
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
   (fn [data owner]
     (reify
       om/IRender
       (render [_]
         (dom/div nil
                  (om/build top-nav data)
                  (apply dom/div nil
                         (om/build-all repl-log (:repl data)))
                  (om/build repl data)))))
   app-state
   {:target (. js/document (getElementById "app"))}))

