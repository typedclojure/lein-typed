(defproject lein-typed "0.4.5-SNAPSHOT"
  :description "Type checking with Typed Clojure"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :url "https://github.com/typedclojure/lein-typed"
  :dependencies [[org.clojure/tools.namespace "0.2.11"]]
  :deploy-repositories [["releases" :clojars]
                        ["snapshots" :clojars]]
  :eval-in-leiningen true)
