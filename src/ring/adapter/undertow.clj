(ns ring.adapter.undertow
  "Adapter for the Undertow webserver."
  (:import (java.nio ByteBuffer)
           (java.io File InputStream FileInputStream)
           (io.undertow Handlers Undertow)
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

(defn- proxy-handler
  "Returns an Undertow HttpHandler implementation for the given Ring handler."
  [handler]
  (BlockingHandler.
    (reify
      HttpHandler
      (handleRequest [_ exchange]
        (let [request-map (build-exchange-map exchange)
              response-map (handler request-map)]
          (set-exchange-response exchange response-map))))))


(defn ^Undertow run-undertow
  "Start an Undertow webserver to serve the given handler according to the
  supplied options:

  :configurator   - a function called with the Undertow Builder instance
  :port           - the port to listen on (defaults to 80)
  :host           - the hostname to listen on
  :io-threads     - number of threads to use for I/O (default: number of cores)
  :worker-threads - number of threads to use for processing (default: io-threads * 8)

  Returns an Undertow server instance. To stop call (.stop server)."
  [handler options]
  (let [b (Undertow/builder)]
    (.addListener b (:port options 80)
                    (:host options "localhost"))
    (.setHandler b (proxy-handler handler))

    (when-let [io-threads (:io-threads options)]
      (.setIoThreads b io-threads))
    (when-let [worker-threads (:worker-threads options)]
      (.setWorkerThreads b worker-threads))
    (when-let [configurator (:configurator options)]
      (configurator b))

    (let [s (.build b)]
      (.start s)
      s)))
