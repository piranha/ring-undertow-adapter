# ring-undertow-adapter

ring-undertow-adapter is a Ring server built with
[Undertow](http://undertow.io).

## Installation

It's not finished yet.

## Usage

```clojure
(require '[ring.adapter.undertow :refer [run-undertow]])

(defn handler [req]
  {:status 200
   :body "Hello world"})

(run-undertow handler {:port 8080})
```

## License

Distributed under ISC License.
