# ring-undertow-adapter

ring-undertow-adapter is a Ring server built with
[Undertow](http://undertow.io).

## Installation

Put this in `:dependencies` vector of your `project.clj`:

    [ring-undertow-adapter "0.1.0"]

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
