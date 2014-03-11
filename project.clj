(defproject ring-undertow-adapter "0.1.4"
  :description "Ring Underow adapter."
  :url "http://github.com/piranha/ring-adapter-undertow"
  :license {:name "ISC License"
            :url "http://opensource.org/licenses/ISC"}
  :dependencies [[io.undertow/undertow-core "1.0.0.Beta12"]]
  :profiles {:dev {:dependencies [[clj-http "0.7.6"]]}})
