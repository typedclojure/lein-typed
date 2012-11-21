(ns leiningen.typed
  (:require [leiningen.core.eval :refer [eval-in-project]]))

(defn typed
  "Type check a namespace"
  [project & [nstr & args]]
  (eval-in-project project
                   `(if ~nstr
                      (do
                        (typed.core/check-ns (read-string ~nstr))
                        (println "Checking: " ~nstr))
                      (throw (Exception. "Must provide namespace symbol")))
                   '(require '[typed.core :refer [check-ns]])))
