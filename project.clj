(defproject replme-frontend "0.1.0-SNAPSHOT"
  :description "REPLME Frontend"
  :url "replme.clojurecup.com"

  :license {:name "GNU Affero General Public License v3"
            :url "https://www.gnu.org/licenses/agpl-3.0.html"}

  :dependencies [[org.clojure/clojure "1.7.0-alpha1"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [org.clojure/clojurescript "0.0-2342"]
                 [markdown-clj "0.9.53"]
                 [jayq "2.5.2"]]

  :plugins [[lein-cljsbuild "1.0.3"]]
  :cljsbuild {
              :builds {:prod {:source-paths ["src"]
                              :compiler {:preamble ["libs/jquery-1.10.2.min.js"
                                                    "libs/jquery.console.js"]
                                         :externs ["libs/jquery-1.10.2.min.js"
                                                   "libs/jquery.console.js"]
                                         :output-to "resources/public/replme.js"
                                         :output-dir "resources/public/out"
                                         :pretty-print false
                                         :optimizations :advanced
                                         :closure-warnings {:externs-validation :off
                                                            :non-standard-jsdoc :off}}}
                       :dev {:source-paths ["src"]
                             :compiler {
                                        :output-to "resources/public/replme.js"
                                        :optimizations :none
                                        :source-map true}}}})
