(ns leiningen.typed
  (:import (java.util.concurrent TimeoutException TimeUnit FutureTask))
  (:require [clojure.java.io :as io]
            [bultitude.core :as b]
            [leiningen.core.eval :refer [eval-in-project] :as eval]
            [leiningen.core.main :as main]
            [leiningen.test :as lt]
            [leiningen.core.project :as project]))

(defn help []
  "Type checking for Clojure.")

(defn core-typed-help []
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
" lein typed coverage nsym+   - basic type coverage for namespaces nsyms\n")
  (println
" lein typed infer-spec nsym  - infer clojure.spec's for the given namespace by running tests.")
  (println
" lein typed infer-type nsym  - infer core.typed types's for the given namespace by running tests.
                                infer-{spec,type} Options:
                                 :test-timeout-ms     The amount of time in milliseconds a given test
                                                      can run.
                                                      Default: No limit.
                                 :test-selectors      A string containing a vector of arguments normally passed to `lein test`
                                                      to narrow tests.
                                                      eg. :test-selectors \"[:integration]\"
                                                      Default: \"[:default]\"
                    
                                 :load-infer-results  If non-nil, a file to load existing inference results from. Disables unit tests.
                                                      Default: nil.
                                 :infer-opts          A string containing a map of options to be passed 
                                                      to `clojure.core.typed/spec-infer` after the :ns argument.
                                                      eg. :infer-opts \"{:debug true}\"
                                                      Default: No options.
                                 :instrument-opts     A string containing a map of options to be passed 
                                                      to `clojure.core.typed/prepare-infer-ns` after the :ns argument.
                                                      eg. :infer-opts \"{:debug true}\"
                                                      Default: No options.")
  (flush)
  )

(defn ^:private check* [project impl args]
  {:pre [(#{:clj :cljs} impl)]}
  (let [nsyms (or ; manually provided
                  (when args 
                    (map symbol args)) 
                  ; default to project.clj entry
                  (-> project 
                      :core.typed 
                      (get (case impl 
                             :clj :check
                             :cljs :check-cljs))))
        _ (assert (and (coll? nsyms)
                       (every? symbol? nsyms))
                  (str
                    (case impl 
                      :clj :check
                      :cljs :check-cljs)
                    " entry must be a vector of symbols"))
        check-fn-sym (case impl
                       :clj `clojure.core.typed/check-ns
                       :cljs `cljs.core.typed/check-ns*)
        exit-code (eval-in-project project
                                   `(if-let [nsyms# (seq '~nsyms)]
                                      (let [res# (try (~check-fn-sym nsyms#)
                                                      (catch Exception e#
                                                        (println (.getMessage e#))
                                                        (flush)))]
                                        (when-not (#{:ok} res#)
                                          (println "Found errors")
                                          (flush)
                                          (System/exit 1))
                                        (prn res#)
                                        (shutdown-agents))
                                      (do (println 
                                            (str "No namespaces provided in project.clj. "
                                                 "Add namespaces in :core.typed {" 
                                                 ~(case impl 
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
                          (do (var-coverage# nsyms#)
                              (shutdown-agents))
                          (println "Coverage only supported with core.typed 0.2.3+"))
                        (do (println "No namespaces provided in project.clj. Add namespaces in :core.typed {:check [...]}")
                            (flush)))
                     '(require '[clojure.core.typed]))))

;; copied from leiningen.test
(defn- convert-to-ns [possible-file]
  (if (and (re-matches #".*\.cljc?" possible-file) (.exists (io/file possible-file)))
    (str (second (b/ns-form-for-file possible-file)))
    possible-file))

(defn- split-selectors [args]
  (let [[nses selectors] (split-with (complement keyword?) args)]
    [nses
     (loop [acc {} [selector & selectors] selectors]
       (if (seq selectors)
         (let [[args next] (split-with (complement keyword?) selectors)]
           (recur (assoc acc selector (list 'quote args))
                  next))
         (if selector
           (assoc acc selector ())
           acc)))]))

(defn- partial-selectors [project-selectors selectors]
  (for [[k v] selectors
        :let [selector-form (k project-selectors)]
        :when selector-form]
    [selector-form v]))

(def ^:private only-form
  ['(fn [ns & vars]
      ((set (for [v vars]
              (-> (str v)
                  (.split "/")
                  (first)
                  (symbol))))
       ns))
   '(fn [m & vars]
      (some #(let [var (str "#'" %)]
               (if (some #{\/} var)
                 (= var (-> m ::lt/var str))
                 (= % (ns-name (:ns m)))))
            vars))])

(defn read-args [args project]
  (let [args (->> args (map convert-to-ns) (map read-string))
        [nses given-selectors] (split-selectors args)
        nses (or (seq nses)
                 (sort (b/namespaces-on-classpath
                        :classpath (map io/file (distinct (:test-paths project)))
                        :ignore-unreadable? false)))
        selectors (partial-selectors (merge {:all '(constantly true)}
                                            {:only only-form}
                                            (:test-selectors project))
                                     given-selectors)
        selectors (if (and (empty? selectors)
                           (:default (:test-selectors project)))
                    [[(:default (:test-selectors project)) ()]]
                    selectors)]
    (when (and (empty? selectors)
               (seq given-selectors))
      (main/abort "Please specify :test-selectors in project.clj"))
    [nses selectors]))

(defn- form-for-select-namespaces [namespaces selectors]
  `(reduce (fn [acc# [f# args#]]
             (if (vector? f#)
               (filter #(apply (first f#) % args#) acc#)
               acc#))
           '~namespaces ~selectors))

(def form-for-suppressing-unselected-tests
  "A function that figures out which vars need to be suppressed based on the
  given selectors, moves their :test metadata to :leiningen/skipped-test (so
  that clojure.test won't think they are tests), runs the given function, and
  then sets the metadata back."
  `(fn [namespaces# selectors# func#]
     (let [copy-meta# (fn [var# from-key# to-key#]
                        (if-let [x# (get (meta var#) from-key#)]
                          (alter-meta! var# #(-> % (assoc to-key# x#) (dissoc from-key#)))))
           vars# (if (seq selectors#)
                   (->> namespaces#
                        (mapcat (comp vals ns-interns))
                        (remove (fn [var#]
                                  (some (fn [[selector# args#]]
                                          (let [sfn# (if (vector? selector#)
                                                       (second selector#)
                                                       selector#)]
                                            (apply sfn#
                                                   (merge (-> var# meta :ns meta)
                                                          (assoc (meta var#) ::lt/var var#))
                                                   args#)))
                                        selectors#)))))
           copy# #(doseq [v# vars#] (copy-meta# v# %1 %2))]
       (copy# :test :leiningen/skipped-test)
       (try (func#)
            (finally
              (copy# :leiningen/skipped-test :test))))))

(defn- form-for-nses-selectors-match [selectors ns-sym]
  `(distinct
     (for [ns# ~ns-sym
           [_# var#] (ns-publics ns#)
           :when (some (fn [[selector# args#]]
                         (apply (if (vector? selector#)
                                  (second selector#)
                                  selector#)
                                (merge (-> var# meta :ns meta)
                                       (assoc (meta var#) ::lt/var var#))
                                args#))
                       ~selectors)]
       ns#)))

(defn form-for-testing-namespaces
  "Return a form that when eval'd in the context of the project will test each
  namespace and print an overall summary."
  [{:keys [infer-nsym types-or-specs test-timeout-ms infer-opts namespaces selectors
           load-infer-results instrument-opts]}]
   {:pre [(symbol? infer-nsym)
          (#{:type :spec} types-or-specs)
          (or (integer? test-timeout-ms)
              (nil? test-timeout-ms))
          (or (nil? load-infer-results)
              (string? load-infer-results))]}
   (let [run-tests? (not load-infer-results)
         ns-sym (gensym "namespaces")]
     `(try
        (let [~ns-sym ~(form-for-select-namespaces namespaces selectors)]
          (when ~run-tests?
            (when (seq ~ns-sym)
              (apply require :reload ~ns-sym))
            (if-let [prepare-infer-fn# (resolve 'clojure.core.typed/prepare-infer-ns)]
              (prepare-infer-fn# :ns '~infer-nsym
                                 ~@(apply concat instrument-opts))
              (do (println "Runtime inference only supported with core.typed 0.4.0 or higher.")
                  (System/exit 1)))
            (let [selected-namespaces# ~(form-for-nses-selectors-match selectors ns-sym)
                  ;; from https://github.com/flatland/clojail/blob/master/src/clojail/core.clj#L40
                  thunk-timeout#
                  (fn [thunk# ms#]
                    (let [task# (FutureTask. thunk#)
                          thr# (Thread. task#)]
                      (try
                        (.start thr#)
                        (let [v# (.get task# ms# TimeUnit/MILLISECONDS)]
                          (.cancel task# true)
                          (.stop thr#) 
                          v#)
                        (catch TimeoutException e#
                          (.cancel task# true)
                          (.stop thr#) 
                          (throw (TimeoutException. "Execution timed out.")))
                        (catch Exception e#
                          (.cancel task# true)
                          (.stop thr#)
                          (throw e#)))))
                  summary# (binding [clojure.test/*test-out* *out*]
                             (~form-for-suppressing-unselected-tests
                               selected-namespaces# ~selectors
                               #(let []
                                  (binding [~'clojure.test/test-var
                                            (fn [v#]
                                              (when-let [t# (:test (meta v#))]
                                                (binding [~'clojure.test/*testing-vars* (conj ~'clojure.test/*testing-vars* v#)]
                                                  (~'clojure.test/do-report {:type :begin-test-var, :var v#})
                                                  (~'clojure.test/inc-report-counter :test)
                                                  (try (if-some [timeout# ~test-timeout-ms]
                                                         (thunk-timeout# t# timeout#)
                                                         (t#))
                                                       (catch Throwable e#
                                                         (~'clojure.test/do-report
                                                           {:type :error, :message "Uncaught exception, not in assertion."
                                                            :expected nil, :actual e#})))
                                                  (~'clojure.test/do-report {:type :end-test-var, :var v#}))))]
                                    (when ~test-timeout-ms
                                      (println (str "Testing with " ~test-timeout-ms "ms timeout")))
                                    (apply ~'clojure.test/run-tests selected-namespaces#)))))]
              ))
          (let [infer-fn# ~(case types-or-specs
                             :type `(resolve 'clojure.core.typed/runtime-infer)
                             :spec `(resolve 'clojure.core.typed/spec-infer))
                _# (assert infer-fn# "Cannot find core.typed inference function")
                _# (println (str "Inferring" ~(case types-or-specs
                                                :type " types "
                                                :spec " specs ")
                                 "for " '~infer-nsym " ..."))
                do-infer# (infer-fn# :ns '~infer-nsym
                                     :load-infer-results ~load-infer-results
                                     ~@(when infer-opts
                                         (apply concat infer-opts)))]
            (println (str "Finished inference, output written to " '~infer-nsym))
            (System/exit 0)))
            ;; FIXME Long lag? Shutdown agents doesn't seem to help. I assume
            ;; it has something to do with all those cancelled futures.
            ;(shutdown-agents)
            ;do-infer#
        (catch Throwable e#
          ;; seems better than hanging forever..
          (clojure.repl/pst e#)
          (System/exit 0)))))

(defn infer [project types-or-specs & args]
  (let [[infer-nsym args] [(some-> (first args) symbol)
                           (next args)]
        _ (assert (symbol? infer-nsym) "Namespace to infer must be provided as first argument.")
        _ (assert (even? (count args)) (str "Even number of keyword argument expected. "
                                            args))
        args (into {}
                   (map (fn [[k v]]
                          [(read-string k) v]))
                   (partition 2 args))
        {:keys [test-selectors test-timeout-ms infer-opts instrument-opts load-infer-results] :as args} args
        infer-opts (when infer-opts
                     (let [infer-opts (read-string infer-opts)
                           _ (assert (map? infer-opts) ":infer-opts must be a string containing a map.")]
                       infer-opts))
        instrument-opts (when instrument-opts
                          (let [instrument-opts (read-string instrument-opts)
                                _ (assert (map? infer-opts) ":instrument-opts must be a string containing a map.")]
                            instrument-opts))
        test-selectors (when test-selectors
                         (let [test-selectors (read-string test-selectors)
                               _ (assert (vector? test-selectors)  ":test-selectors must be a string containing a vector.")]
                           ;; read-args expects a collection of read'able strings
                           (map pr-str test-selectors)))
        project (project/merge-profiles project [:leiningen/test :test])
        [nses selectors] (read-args test-selectors project)
        _ (eval/prep project)
        form (form-for-testing-namespaces
               {:infer-nsym infer-nsym
                :load-infer-results load-infer-results
                :types-or-specs types-or-specs 
                :test-timeout-ms (some-> test-timeout-ms Long/parseLong)
                :infer-opts infer-opts
                :namespaces nses 
                :selectors (vec selectors)
                :instrument-opts instrument-opts})]
    ;(spit "out-pprint" (with-out-str (clojure.pprint/pprint form)))
    (eval/eval-in-project project form
                          '(require 'clojure.test
                                    'clojure.core.typed
                                    'clojure.repl)))
  nil)

(defn typed
  "Type check a namespace"
  [project & [mode & args]]
  (case mode
    "check" (apply check project args)
    "check-cljs" (apply check-cljs project args)
    "coverage" (apply coverage project args)
    "infer-type" (apply infer project :type args)
    "infer-spec" (apply infer project :spec args)
    (core-typed-help)))
