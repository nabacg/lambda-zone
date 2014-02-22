(ns lambda-zone.client
  (:require [chord.client :refer [ws-ch]]
            [cljs.core.async :refer [<! >! put! take! close! timeout]]
            [dommy.core :as d]
            [goog.string.format :as gformat])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [dommy.macros :refer [node sel1]]))


(defn initial-board []
  [\r \n \b \q \k \b \n \r
   \p \p \p \p \p \p \p \p
   \- \- \- \- \- \- \- \-
   \- \- \- \- \- \- \- \-
   \- \- \- \- \- \- \- \-
   \- \- \- \- \- \- \- \-
   \P \P \P \P \P \P \P \P
   \R \N \B \Q \K \B \N \R])

(defn c1dto2d [i]
  (vector (int (/ i 8)) (mod i 8)))

(defn char2state [pieces-list]
  (into {} (filter #(not= \- (second %)) (map #(vector (c1dto2d %1) %2 ) (range 64) pieces-list))))

(def piece2img
  {
   "B" "WB"
   "K" "WK"
   "Q" "WQ"
   "N" "WN"
   "P" "WP"
   "R" "WR"
   "b" "BB"
   "k" "BK"
   "q" "BQ"
   "n" "BN"
   "p" "BP"
   "r" "BR"
   })

(defn render-piece [piece]
  (let [img (piece2img piece)]
    (if (nil? img)
      [:img { :width "32" :height "32"}]
      [:img {:src (str "/images/" img ".png") :alt "white bishop" :width "32" :height "32"}])))

(defn render-board [board-state]
  (node
   [:div
    (let [line "+------+------+------+------+------+------+------+------+"
          pieces-pos (char2state board-state)         ;(into {} board-state)
          ]
      (list
       [:div line]
       [:div (map #(let [pos (c1dto2d (dec %))
                           c (get pieces-pos pos "-")]
                       (if (zero? (mod % 8))
                         (list "| " (render-piece c) "|" [:div line])
                         (list "| " (render-piece c) )
                         ;;(gformat "| %s |\n%s\n" c line)
                         ;;(gformat "| %s " c)
                         )) (range 1 65))]))]))

(defn render-page [bind-input! bind-list!]
  (node
   (list
    [:div
     [:h3 "Send a message to the server:"]
     (doto (node [:input {:type :text :size 50}])
       bind-input!)]
    [:div

     [:h3 "Messages from the server:"]
     (doto (node [:div])
       bind-list!)])))

(defn deserialize-msg [msg]
  ((js->clj (JSON/parse (:message msg))) "msg"))

(defn render-board-old [board]
  (let [b (partition 8 board)]
    (for [col b]
      [:li (pr-str col) ]
      )))

(defn render-list [msgs]
  (node
   [:ul
    (if (seq msgs)
      (let [msg (first msgs)
            d-msg (deserialize-msg msg)]
        (render-board (d-msg "board")))
      (render-board (initial-board))
      ;[:li "None yet."]
      )]))

(defn render-list-old [msgs]
  (node
   [:ul
    (if (seq msgs)
      (for [msg msgs]
        [:li (pr-str msg)])
      [:li "None yet."])]))

(defn list-binder [msgs]
  (fn [$list]
    (add-watch msgs ::list
               (fn [_ _ _ msgs]
                 (->> (reverse msgs)
                      (take 10)
                      (render-list)
                      (d/replace-contents! $list))))))

(defn input-binder [ch]
  (fn [$input]
    (d/listen! $input :keyup
               (fn [e]
                 (when (= 13 (.-keyCode e))
                   (put! ch (d/value $input))
                   (d/set-value! $input ""))))
    (go (<! (timeout 200)) (.focus $input))))

(defn bind-msgs [ch msgs]
  (go
   (loop []
     (when-let [msg (<! ch)]
       (swap! msgs conj msg)
       (recur)))))

(set! (.-onload js/window)
      (fn []
        (go
         (let [msgs (atom [])
               ws (<! (ws-ch "ws://localhost:3000/ws"))]
           (bind-msgs ws msgs)
           (d/replace-contents! (sel1 :#content)
                                (render-page (input-binder ws)
                                             (list-binder msgs)))
           (reset! msgs [])))))
