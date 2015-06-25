# ring-undertow-adapter

ring-undertow-adapter is a [Ring](https://github.com/ring-clojure/ring) server built with
[Undertow](http://undertow.io).

I'm not really spending a lot of time on this, and I think you should look at
using
[immutant-web](http://immutant.org/documentation/current/apidoc/guide-web.html)
rather than this code. It's probably going to be better. :)

## Installation

Put this in `:dependencies` vector of your `project.clj`:

[![ring-undertow-adapter latest version](https://clojars.org/ring-undertow-adapter/latest-version.svg)]
(https://clojars.org/ring-undertow-adapter)

### Tracking Undertow versions

If you find that `ring-undertow-adapter` has a dependency on an older Undertow,
you can override that in your own `project.clj` by putting those lines inside of
`:dependencies`:

```clojure
; Replace "x.y.z" with your version of ring-undertow-adapter
[ring-undertow-adapter "x.y.z" :exclusions [io.undertow/undertow-core]]
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
