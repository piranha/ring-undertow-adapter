(ns ring.adapter.undertow
  "Adapter for the Undertow webserver."
  (:import (java.nio ByteBuffer)
           (java.io File InputStream FileInputStream)
           (io.undertow Handlers Undertow Undertow$Builder)
           (io.undertow.io Sender)
           (io.undertow.server HttpHandler HttpServerExchange)
           (io.undertow.util HeaderMap HttpString HeaderValues Headers)
           (io.undertow.server.handlers BlockingHandler))
  (:require [clojure.java.io :as io]
            [clojure.string :as str]))
(set! *warn-on-reflection* true)

;; Parsing request

(defn- get-headers
  [^HeaderMap header-map]
  (persistent!
   (reduce
    (fn [headers ^HeaderValues entry]
      (let [k (.. entry getHeaderName toString toLowerCase)
            val (if (> (.size entry) 1)
                  (str/join "," (iterator-seq (.iterator entry)))
                  (.get entry 0))]
        (assoc! headers k val)))
    (transient {})
    header-map)))

(defn- build-exchange-map
  [^HttpServerExchange exchange]
  (let [headers (.getRequestHeaders exchange)
        ctype (.getFirst headers Headers/CONTENT_TYPE)]
    {:server-port        (-> exchange .getDestinationAddress .getPort)
     :server-name        (-> exchange .getHostName)
     :remote-addr        (-> exchange .getSourceAddress .getAddress .getHostAddress)
     :uri                (-> exchange .getRequestURI)
     :query-string       (-> exchange .getQueryString)
     :scheme             (-> exchange .getRequestScheme .toString .toLowerCase keyword)
     :request-method     (-> exchange .getRequestMethod .toString .toLowerCase keyword)
     :headers            (-> exchange .getRequestHeaders get-headers)
     :content-type       ctype
     :content-length     (-> exchange .getRequestContentLength)
     :character-encoding (or (when ctype (Headers/extractTokenFromHeader ctype "charset"))
                             "ISO-8859-1")
     :body               (.getInputStream exchange)}))

;; Updating response

(defn- set-headers
  [^HeaderMap header-map headers]
  (reduce-kv
   (fn [^HeaderMap header-map ^String key val-or-vals]
     (let [key (HttpString. key)]
       (if (string? val-or-vals)
         (.put header-map key ^String val-or-vals)
         (.putAll header-map key val-or-vals)))
     header-map)
   header-map
   headers))

(defn- ^ByteBuffer str-to-bb
  [^String s]
  (ByteBuffer/wrap (.getBytes s "utf-8")))

(defprotocol RespondBody
  (respond [_ ^HttpServerExchange exchange]))

(extend-protocol RespondBody
  String
  (respond [body ^HttpServerExchange exchange]
    (.send ^Sender (.getResponseSender exchange) body))

  InputStream
  (respond [body ^HttpServerExchange exchange]
    (with-open [^InputStream b body]
      (io/copy b (.getOutputStream exchange))))

  File
  (respond [f exchange]
    (respond (io/input-stream f) exchange))

  clojure.lang.ISeq
  (respond [coll ^HttpServerExchange exchange]
    (reduce
     (fn [^Sender sender i]
       (.send sender (str-to-bb i))
       sender)
     (.getResponseSender exchange)
     coll))

  nil
  (respond [_ exc]))

(defn- set-exchange-response
  [^HttpServerExchange exchange {:keys [status headers body]}]
  (when-not exchange
    (throw (Exception. "Null exchange given.")))
  (when status
    (.setResponseCode exchange status))
  (set-headers (.getResponseHeaders exchange) headers)
  (respond body exchange))

;;; Adapter stuff

(defn- undertow-handler
  "Returns an Undertow HttpHandler implementation for the given Ring handler."
  [handler non-blocking]
  (reify
    HttpHandler
    (handleRequest [_ exchange]
      (when-not non-blocking
        (.startBlocking exchange))
      (let [request-map (build-exchange-map exchange)
            response-map (handler request-map)]
        (set-exchange-response exchange response-map)))))

(defn- on-io-proxy
  [handler]
  (undertow-handler handler false))

(defn- dispatching-proxy
  [handler]
  (BlockingHandler. (undertow-handler handler true)))

(defn ^Undertow run-undertow
  "Start an Undertow webserver to serve the given handler according to the
  supplied options:

  :configurator   - a function called with the Undertow Builder instance
  :port           - the port to listen on (defaults to 80)
  :host           - the hostname to listen on
  :io-threads     - number of threads to use for I/O (default: number of cores)
  :worker-threads - number of threads to use for processing (default: io-threads * 8)
  :dispatch?      - dispatch handlers off the I/O threads (default: true)

  Returns an Undertow server instance. To stop call (.stop server)."
  [handler {:keys [host port dispatch?]
            :or   {host "localhost" port 80 dispatch? true}
            :as   options}]
  (let [^Undertow$Builder b (Undertow/builder)
        handler-proxy (if dispatch? dispatching-proxy on-io-proxy)]
    (.addListener b port host)
    (.setHandler b (handler-proxy handler))

    (let [{:keys [io-threads worker-threads]} options]
      (when io-threads     (.setIoThreads b io-threads))
      (when worker-threads (.setWorkerThreads b worker-threads)))

    (when-let [configurator (:configurator options)]
      (configurator b))

    (let [^Undertow s (.build b)]
      (.start s)
      s)))
