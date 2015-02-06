(defproject fun-utils "0.5.3"
            :description "Clojure utility functions that come up time and again while developing clojure software"
            :url "https://github.com/gerritjvv/fun-utils"
            :license {:name "ECLIPSE PUBLIC LICENSE"
                      :url  "http://www.eclipse.org/legal/epl-v10.html"}

            :javac-options ["-target" "1.6" "-source" "1.6" "-Xlint:-options"]

            :plugins [
                       [lein-rpm "0.0.5"] [lein-midje "3.0.1"] [lein-marginalia "0.7.1"]
                       [lein-kibit "0.0.8"] [no-man-is-an-island/lein-eclipse "2.0.0"]
                       ]
            :warn-on-reflection true

            :dependencies [
                            [midje "1.6-alpha2" :scope "test"]
                            [clj-tuple "0.1.4"]
                            [org.clojure/tools.logging "0.3.0"]
                            [org.clojure/core.async "0.1.267.0-0d7780-alpha"]
                            [org.clojure/clojure "1.6.0" :scope "provided"]
                            [com.google.guava/guava "18.0"]
                            [potemkin "0.3.11"]])
