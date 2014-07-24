# ring-undertow-adapter

ring-undertow-adapter is a [Ring](https://github.com/ring-clojure/ring) server built with
[Undertow](http://undertow.io).

## Installation

Put this in `:dependencies` vector of your `project.clj`:

    [ring-undertow-adapter "0.1.7"]

### Tracking Undertow versions

If you find that `ring-undertow-adapter` has a dependency on an older Undertow,
you can override that in your own `project.clj` by putting those lines inside of
`:dependencies`:

```
[ring-undertow-adapter "0.1.7" :exclusions [io.undertow/undertow-core]]
[io.undertow/undertow-core "Version-You-Want"]
```


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
