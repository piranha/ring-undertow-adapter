(ns ring.adapter.undertow
  "Adapter for the Undertow webserver."
  (:import (java.nio ByteBuffer)
           (java.io File InputStream FileInputStream)
           (io.undertow Handlers Undertow)
           (io.undertow.server HttpHandler HttpServerExchange)
           (io.undertow.util HeaderMap HttpString HeaderValues Headers))
  (:require [clojure.java.io :as io]
            [clojure.string :as s]))

;; Parsing request

(defn- get-headers
  [^HeaderMap header-map]
  (reduce
   (fn [headers ^HttpString name]
     (assoc headers
       (.. name toString toLowerCase)
       (->> (.get header-map name)
            .iterator
            iterator-seq
            (s/join ","))))
   {}
   (.getHeaderNames header-map)))

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
                             "UTF-8")
     :body               (.getInputStream exchange)}))

;; Updating response

(defn- set-headers
  [^HeaderMap header-map headers]
  (doseq [[key val-or-vals] headers
          :let [^HttpString hs (HttpString. key)]]
    (if (string? val-or-vals)
      (.add header-map hs val-or-vals)
      (doseq [val val-or-vals]
        (.add header-map hs val)))))

(defn- str-to-bb
  [^String s]
  (ByteBuffer/wrap (.getBytes s "utf-8")))

(defn- set-body
  [^HttpServerExchange exchange body]
  (cond
   (string? body)
     (-> (.getResponseSender exchange)
         (.send body))

   (seq? body)
     (let [sender (.getResponseSender exchange)]
       (doseq [chunk body]
         (.send sender (str-to-bb chunk))))

   (instance? InputStream body)
     (with-open [^InputStream b body]
       (io/copy b (.getOutputStream exchange)))

   (instance? File body)
     (let [^File f body]
       (with-open [stream (FileInputStream. f)]
          (set-body exchange stream)))

   (nil? body)
     nil

   :else (throw (Exception. ^String (format "Unrecognized body: %s" body)))))

(defn- set-exchange-response
  [^HttpServerExchange exchange {:keys [status headers body]}]
  (when-not exchange
    (throw (Exception. "Null exchange given.")))
  (when status
    (.setResponseCode exchange status))
  (set-headers (.getResponseHeaders exchange) headers)
  (set-body exchange body))

;;; Adapter stuff

(defn- proxy-handler
  "Returns an Undertow HttpHandler implementation for the given Ring handler."
  [handler]
  (proxy [HttpHandler] []
    (handleRequest [^HttpServerExchange exchange]
      (.startBlocking exchange)
      (let [request-map (build-exchange-map exchange)
            response-map (handler request-map)]
        (set-exchange-response exchange response-map)))))


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
    (.addListener b (options :port 80)
                    (options :host "localhost"))
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
