(ns leiningen.typed
  (:require [leiningen.core.eval :refer [eval-in-project]]
            [leiningen.core.main :as main]))

(defn help []
  (println "lein-typed: Type checking for Clojure")
  (println "Usage:")
  (println
" lein typed check            - type check all Clojure namespaces declared in project.clj,
                                via :core.typed {:check [...]}")
  (println 
" lein typed check nsym+      - only type check Clojure namespaces nsyms")
  (println
" lein typed check-cljs       - type check all Clojurescript namespaces declared in project.clj,
                                via :core.typed {:check-cljs [...]}")
  (println 
" lein typed check-cljs nsym+ - only type check Clojurescript namespaces nsyms")
  (println
" lein typed coverage         - basic type coverage for all namespaces declared in project.clj,
                                via :core.typed {:check [...]}")
  (println 
" lein typed coverage nsym+   - basic type coverage for namespaces nsyms")
  (flush))

(defn ^:private check* [project impl args]
  {:pre [(#{:clj :cljs} impl)]}
  (let [nsyms (or (when args (map symbol args)) (-> project :core.typed 
                                                    (case impl 
                                                      :clj :check
                                                      :cljs :check-cljs)))
        check-fn-sym (case impl
                       :clj `clojure.core.typed/check-ns
                       :cljs `cljs.core.typed/check-ns)
        exit-code (eval-in-project project
                                   `(if-let [nsyms# (seq '~nsyms)]
                                      (let [errors# (doall
                                                      (for [nsym# nsyms#]
                                                        (try (~check-fn-sym nsym#)
                                                             (catch Exception e#
                                                               (println (.getMessage e#))
                                                               (flush)
                                                               false))))]
                                        (when-not (every? #{:ok} errors#)
                                          (System/exit 1)))
                                      (do (println 
                                            (str "No namespaces provided in project.clj." 
                                                 "Add namespaces in :core.typed {" 
                                                 (case impl 
                                                   :clj :check 
                                                   :cljs :check-cljs) 
                                                 " [...]}"))
                                          (flush)))
                                   (case impl
                                     :clj '(require '[clojure.core.typed])
                                     :cljs '(require '[cljs.core.typed])))]
    (when (and (number? exit-code) (pos? exit-code))
      (main/exit exit-code))))

(defn check [project & args]
  (check* project :clj args))

(defn check-cljs [project & args]
  (check* project :cljs args))

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
    "check-cljs" (apply check-cljs project args)
    "coverage" (apply coverage project args)
    (help)))
