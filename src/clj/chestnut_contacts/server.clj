(ns chestnut-contacts.server
  (:require [clojure.java.io :as io]
            [chestnut-contacts.dev :refer [is-dev? inject-devmode-html browser-repl start-figwheel]]
            [compojure.core :refer [GET defroutes POST]]
            [compojure.route :refer [resources]]
            [net.cgrand.enlive-html :refer [deftemplate]]
            [net.cgrand.reload :refer [auto-reload]]
            [ring.middleware.reload :as reload]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
            [ring.middleware.browser-caching :refer [wrap-browser-caching]]
            [ring.middleware.gzip :refer [wrap-gzip]]
            [environ.core :refer [env]]
            [org.httpkit.server :as httpkit]
            [taoensso.sente :as sente]

            [taoensso.sente.server-adapters.http-kit      :refer (sente-web-server-adapter)])
  (:gen-class))

(deftemplate page (io/resource "index.html") []
  [:body] (if is-dev? inject-devmode-html identity))

(let [{:keys [ch-recv send-fn ajax-post-fn ajax-get-or-ws-handshake-fn
              connected-uids]}
      (sente/make-channel-socket! sente-web-server-adapter {})]
  (def ring-ajax-post                ajax-post-fn)
  (def ring-ajax-get-or-ws-handshake ajax-get-or-ws-handshake-fn)
  (def ch-chsk                       ch-recv) ; ChannelSocket's receive channel
  (def chsk-send!                    send-fn) ; ChannelSocket's send API fn
  (def connected-uids                connected-uids) ; Watchable, read-only atom
  )


(defonce router (atom nil))


(defn stop-router! []
  (when-let [stop-f @router]
    (stop-f)
    (reset! router nil)))

(defn event-msg-handler [{:keys [id ?reply-fn]}]
  (when (= id :contacts/fetch)
    (println "event-msg-handler msg id " id)
    (when ?reply-fn
      (?reply-fn [{:id 0
                   :name "Mike"
                   :email "mike@email.com"}
                  {:id 1
                   :name "Jim"
                   :email "jim@email.com"}
                  {:id 2
                   :name "Jane"
                   :email "jane@email.com"}
                  {:id 3
                   :name "Tom"
                   :email "tom@email.com"}]))))

(defn start-router! []
  (reset! router (sente/start-chsk-router! ch-chsk event-msg-handler)))

(defroutes routes
  (resources "/")
  (resources "/react" {:root "react"})
  (GET  "/chsk" req (ring-ajax-get-or-ws-handshake req))
  (POST "/chsk" req (ring-ajax-post                req))
  (GET "/*" req (page))
  )

(defn- wrap-browser-caching-opts [handler] (wrap-browser-caching handler (or (env :browser-caching) {})))

(def http-handler
  (cond-> routes
    true (wrap-defaults api-defaults)
    is-dev? reload/wrap-reload
    true wrap-browser-caching-opts
    true wrap-gzip))

(defn run-web-server [& [port]]
  (let [port (Integer. (or port (env :port) 10555))]
    (println (format "Starting web server on port %d." port))
    (httpkit/run-server http-handler {:port port :join? false})))

(defn run-auto-reload [& [port]]
  (auto-reload *ns*)
  (start-figwheel))

(defn run [& [port]]
  (when is-dev?
    (run-auto-reload))
  (run-web-server port))

(defn -main [& [port]]
  (run port))



;; (run)
;; (start-router!)
;; (comment :optional browser-repl)