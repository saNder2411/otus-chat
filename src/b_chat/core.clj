(ns b-chat.core
  (:gen-class)
  (:require [org.httpkit.server :as server]
            [cheshire.core :as cheshire]
            [compojure.core :as compojure])
  (:import [org.httpkit.server AsyncChannel]))


(defonce chat-state (atom {:users [] :messages []}))

(comment
  @chat-state
  (reset! chat-state {:users [] :messages []}))

(defn broadcast-messages [{:keys [users messages]}]
  (doseq [[^AsyncChannel channel] users]
    (server/send! channel (cheshire/generate-string {:messages messages}))))


(defn start-tick []
  (future
    (while true
      (Thread/sleep 1000)
      (try
        (broadcast-messages @chat-state)
        (catch Throwable t
          (println t))))))

(defn chat-handler [request]
  (let [{:keys [user-name]} request]
    (server/as-channel request
                {:on-open (fn [channel]
                            (swap! chat-state update :users conj [channel user-name]))

                 :on-receive (fn [_ message]
                               (swap! chat-state update :messages conj (cheshire/parse-string message true)))

                 :on-close (fn [channel]
                             (swap! chat-state update :users (fn [users]
                                                               (remove #(= channel (first %)) users))))})))

(compojure/defroutes api
  (compojure/GET "/ws" [] chat-handler))

(defn chat-server []
  {:tick (start-tick)
   :ws (server/run-server #'api {:port 8080})})


(comment
  (def chat (chat-server)))
