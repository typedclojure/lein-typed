(ns leiningen.typed
  (:require [leiningen.core.eval :refer [eval-in-project]]))

(defn help []
  (println "lein-typed: Type checking for Clojure")
  (println "Usage:")
  (println 
" lein typed check        - type check all namespaces declared in project.clj, 
                             via :core.typed {:check [...]}")
  (println " lein typed check-ns nsym - type check namespace nsym")
  (flush))

(defn check-ns [project & [nstr & args]]
  (eval-in-project project
                   `(if ~nstr
                      (try (clojure.core.typed/check-ns (read-string ~nstr))
                           (catch Exception e#
                             (println (.getMessage e#))
                             (flush)))
                      (do (println "Must provide namespace symbol after check-ns:")
                          (help)
                          (flush)))
                   '(require '[clojure.core.typed])))

(defn check-all [project & args]
  (let [nsyms (-> project :core.typed :check)]
    (eval-in-project project
                     `(if-let [nsyms# (seq '~nsyms)]
                        (doseq [nsym# nsyms#]
                          (try (clojure.core.typed/check-ns nsym#)
                               (catch Exception e#
                                 (println (.getMessage e#))
                                 (flush))))
                        (do (println "No namespaces provided in project.clj. Add namespaces in :core.typed {:check [...]}")
                            (flush)))
                     '(require '[clojure.core.typed]))))


(defn typed
  "Type check a namespace"
  [project & [mode & args]]
  (case mode
    "check-ns" (apply check-ns project args)
    "check" (apply check-all project args)
    (help)))
