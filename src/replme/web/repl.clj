(ns replme.web.repl
  (:require [clojure.core.async :refer [go <! >! <!! >!! chan close! go-loop filter<]]
            [org.httpkit.server :refer [send! with-channel on-receive on-close open? websocket?]]
            [http.async.client :as http]
            [clojure.tools.logging :as log]
            [replme.web.resp :refer :all]
            [clojure.tools.nrepl :as repl]
            [docker.container :as container]))

(def nrepl-sentinel #"server started")

(defn- docker-attach
  [client id]
  (let [output (chan)
        url (str "http://" (:host client) "/containers/" id "/logs?stdout=1&follow=1") http-client (http/create-client)]
    (go
      (log/info "Reading logs from: " url)
      (doseq [msg (http/string (http/stream-seq http-client :get url))]
        (log/info "Container" id "logs:" msg)
        (>! output msg)))
    [output http-client]))

(defn- docker-cmd
  [args]
  (let [args (if args (vector args) [])]
    (log/info "Starting container from repo" args)
    {:Image "edpaget/lein"
     :Tty true
     :Memory "256M"
     :Cmd args}))

(defn- start-docker
  [client args]
  (let [container (container/create client (docker-cmd args))
        id (:Id container)
        logs (docker-attach client id)]
    (container/start client id)
    (log/info (str "Started container:" id))
    [id logs]))

(defn- docker-ip
  [client id]
  (-> (container/inspect client id)
      :NetworkSettings
      :IPAddress))

(defn- stop-docker
  [client id]
  (container/stop client id))

(defn- out-msg
  [destination msg]
  (pr-str {:message msg :destination destination}))

(defn- docker-repl
  [client args in-chan out-chan]
  (let [[id [stdout http-client]] (start-docker client args)
        ip (docker-ip client id)
        port 8081]
    (go-loop [msg (<! stdout)]
      (if (re-find nrepl-sentinel msg)
        (do
          (log/info (str "Connecting to docker nrepl at" ip ":" port))
          (>! out-chan (out-msg :command "REPL OK"))
          (go-loop [repl-conn (repl/connect :host ip :port port)
                    client (repl/client repl-conn 1000)
                    command (<! in-chan)]
            (->> (repl/message client {:op :eval :code command})
                 (out-msg :repl)
                 (>! out-chan))
            (recur repl-conn client (<! in-chan))))
        (do (>! out-chan (out-msg :console msg))
            (recur (<! stdout)))))
    [id http-client]))

(defn- handle-input
  [in-chan]
  (fn [data]
    (log/info (str "Received:" data))
    (>!! in-chan data)))

(defn- handle-close
  [client id http-client & chans]
  (fn [_]
    (log/info "Closing Repl Connnection")
    (stop-docker client id)
    (http/close http-client)
    (doseq [c chans] (close! c))))

(defn- handle-out
  [channel out-chan]
  (go-loop [msg (<! out-chan)]
    (send! channel msg false)
    (when (open? channel)
      (recur (<! out-chan)))))

(defn- not-empty?
  [{:keys [message]}]
  (cond
   (= "\n" message) false
   (= "\r\n" message) false
   (= "" message) false
   :else true))

(defn- format-msgs
  [msg]
  msg)

(def format-out (comp (map format-msgs) (filter not-empty?)))

(defn open-websocket
  [docker-client req repo]
  (let [in-chan (chan)
        out-chan (chan 1 format-out)]
    (with-channel req channel
      (if (websocket? channel)
        (let [[docker-id http-client] (docker-repl docker-client repo in-chan out-chan)
              send-chan (handle-out channel out-chan)]
          (log/info "Websocket Connected")
          (on-receive channel (handle-input in-chan))
          (on-close channel (handle-close docker-client docker-id http-client in-chan out-chan send-chan)))
        (send! channel (resp-bad-request))))))
