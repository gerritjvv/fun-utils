(defproject fun-utils "0.1.0"
  :description "Clojure utility functions that come up time and again while developing clojure software"
  :url "https://github.com/gerritjvv/fun-utils"
  :license {:name "ECLIPSE PUBLIC LICENSE"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :plugins [
         [lein-rpm "0.0.5"] [lein-midje "3.0.1"] [lein-marginalia "0.7.1"]
         [lein-kibit "0.0.8"] [no-man-is-an-island/lein-eclipse "2.0.0"]
           ]
  :warn-on-reflection true

  :dependencies [
                 [midje "1.6-alpha2" :scope "test"]
                 [org.clojure/core.async "0.1.242.0-44b1e3-alpha"]
                 [org.clojure/clojure "1.5.1"]])
