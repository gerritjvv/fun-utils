(defproject fun-utils "0.7.0"
  :description "Clojure utility functions that come up time and again while developing clojure software"
  :url "https://github.com/gerritjvv/fun-utils"
  :license {:name "ECLIPSE PUBLIC LICENSE"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}

  :javac-options ["-target" "1.8" "-source" "1.8" "-Xlint:-options"]

  :global-vars {*warn-on-reflection* true
                *assert*             false}

  :plugins [
            [lein-rpm "0.0.5"] [lein-midje "3.0.1"] [lein-marginalia "0.7.1"]
            [lein-kibit "0.0.8"] [no-man-is-an-island/lein-eclipse "2.0.0"]
            [lein-ancient "0.6.15"]
            ]

  :dependencies [
                 [midje "1.9.8" :scope "test"]
                 [org.jctools/jctools-core "2.1.2"]
                 [org.clojure/tools.logging "0.5.0-alpha.1"]
                 [org.clojure/core.async "0.4.490"]
                 [org.clojure/clojure "1.10.0" :scope "provided"]
                 [com.google.guava/guava "23.0"]
                 [potemkin "0.4.5"]])
