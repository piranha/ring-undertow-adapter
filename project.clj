(defproject ring-undertow-adapter "0.2.2"
  :description "Ring Underow adapter."
  :url "http://github.com/piranha/ring-adapter-undertow"
  :license {:name "ISC License"
            :url "http://opensource.org/licenses/ISC"}
  :dependencies [[io.undertow/undertow-core "1.1.0.Final"]]
  :profiles {:dev {:dependencies [[org.clojure/clojure "1.6.0"]
                                  [clj-http "1.0.1"]]}})
