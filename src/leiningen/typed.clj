(ns leiningen.typed
  (:require [leiningen.core.eval :refer [eval-in-project]]))

(defn help []
  (println "lein-typed: Type checking for Clojure")
  (println "Usage:")
  (println 
" lein typed check        - type check all namespaces declared in project.clj, 
                           via :core.typed {:check [...]}")
  (println " lein typed check nsym+ - only type check namespaces nsyms")
  (println 
" lein typed coverage - basic type coverage for all namespaces declared in project.clj, 
                       via :core.typed {:check [...]}")
  (println " lein typed coverage nsym+ - basic type coverage for namespaces nsyms")
  (flush))

(defn check [project & args]
  (let [nsyms (or (when args (map symbol args)) (-> project :core.typed :check))]
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

(defn coverage [project & args]
  (let [nsyms (or (when args (map symbol args)) (-> project :core.typed :check))]
    (eval-in-project project
                     `(if-let [nsyms# (seq '~nsyms)]
                        (if-let [var-coverage# (resolve 'clojure.core.typed/var-coverage)]
                          (var-coverage# nsyms#)
                          (println "Coverage only supported with core.typed 0.2.3+"))
                        (do (println "No namespaces provided in project.clj. Add namespaces in :core.typed {:check [...]}")
                            (flush)))
                     '(require '[clojure.core.typed]))))

(defn typed
  "Type check a namespace"
  [project & [mode & args]]
  (case mode
    "check" (apply check project args)
    "coverage" (apply coverage project args)
    (help)))
