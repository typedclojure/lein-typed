# lein-typed

Type checking for Clojure with Clojure core.typed.

## Installation

A core.typed [dependency](https://github.com/clojure/core.typed) must be specified.

Use this for user-level plugins:

Put `[lein-typed "0.4.6"]` into the `:plugins` vector of your
`:user` profile, or if you are on Leiningen 1.x do `lein plugin install
lein-typed 0.4.6`.

Use this for project-level plugins:

Put `[lein-typed "0.4.6"]` into the `:plugins` vector of your project.clj.

## Quickstart

For type checking, see `lein typed check` below. 

For automatically generating types and specs, see `lein typed infer-type`
and `lein typed infer-spec`.

## Clojure Checking

To type check many namespaces, add namespaces to your project.clj like

    :core.typed {:check [my.ns1 my.ns2]}

Then run

    $ lein typed check 
    ...

To type check speific namespaces, provide one or more namespace symbols to `check`:

    $ lein typed check typed-demo.core
    Initializing core.typed ...
    "Elapsed time: 5705.247176 msecs"
    core.typed initialized.
    Start collecting typed-demo.core
    Finished collecting typed-demo.core
    Collected 1 namespaces in 6039.963178 msecs
    Start checking typed-demo.core
    Checked typed-demo.core in 94.891391 msecs
    Checked 1 namespaces (approx. 7 lines) in 6138.087383 msecs

## ClojureScript Checking

To type check many ClojureScript namespaces, add namespaces to your project.clj like

    :core.typed {:check-cljs [my.ns1 my.ns2]}

Then run

    $ lein typed check-cljs
    ...

Requires core.typed 0.2.36 or later.

## Type coverage

`lein typed coverage` is used the same as `check`, except information on type annotation coverage
is given. Namespaces are not checked.

Requires core.typed version 0.2.3 or later.

    $ lein typed coverage
    Initializing core.typed ...
    "Elapsed time: 4505.279024 msecs"
    core.typed initialized.
    Start collecting typed-demo.core
    Finished collecting typed-demo.core
    Collected 1 namespaces in 4619.570363 msecs
    Checked 0 namespaces (approx. 0 lines) in 4621.945525 msecs
    Start collecting typed-demo.nil
    Finished collecting typed-demo.nil
    Collected 1 namespaces in 20.48361 msecs
    Checked 0 namespaces (approx. 0 lines) in 20.645363 msecs
    Found 2 annotated vars out of 2 vars
    100% var annotation coverage

## Type and spec inference

`lein typed infer-spec nsym` and `lein typed infer-type nsym` infer clojure.spec specs
and core.typed types respectively for namespace `nsym`. Keyword options are described below.
A hybrid command `lein typed infer-all nsym` outputs both types and specs, but 
an output directory must be provided via
`:infer-opts "{:out-dir \"your-dir\"}"` (two subfolders will be created, `types` and `specs`
for the generated types and specs respectively).


Requires `core.typed` 0.4.0 or later, with Clojure 1.9.0-alpha17 or later.

| Options:       | |
| --- | --- |
| `:test-timeout-ms` |  Restricts each deftest to a specific time quanta before being aborted, in milliseconds (an integer). If the test suite is excessively slow during instrumentation, try lowering this option. 
|                    |  Default: No timeout  |
| `:test-selectors` |  A string containing a vector of arguments normally passed to `lein test` to narrow tests. |
| `:infer-opts`     |  A string containing a map of options to be passed to `clojure.core.typed/{runtime,spec}-infer` after the :ns argument. 
|                   |  eg. :infer-opts "{:debug :all, :out-dir \\\"out\\\"}" |
|                   |  Default: No options. |

This command first instruments the provided namespace before then running your
test suite. It then summarizes runtime observations based on your test suite in
either specs or types.

Note that these specs or types will reuse exist namespace require's. We recommend
requiring and aliasing `clojure.spec.alpha` or `clojure.core.typed` as needed.

eg. `(:require [[clojure.spec.alpha :as s]])`

### Example

Let's infer the specs for `math.combinatorics`. First we clone the repo.

```
git clone git@github.com:clojure/math.combinatorics.git
cd math.combinatorics
```

This is a Maven project, but we can easily convert it to Leiningen. 
Create a `project.clj` file in the project directory and paste:

```clojure
(defproject org.clojure/math.combinatorics "0.0.1-SNAPSHOT"
  :source-paths ["src/main/clojure"]
  :test-paths ["src/test/clojure"]
  :dependencies [[org.clojure/clojure "1.9.0-alpha17"]
                 ;; insert latest clojure + core.typed versions
                 [org.clojure/core.typed "0.4.0"]
                 [org.clojure/test.check "0.9.0"]]
  ;; insert latest lein-typed version
  :plugins [[lein-typed "0.4.0"]])
```

Now let's run the tests and infer some specs. The `math.combinatorics`
tests take a very long time with instrumentation, so we restrict each test
to 3 seconds each. 

So, execute the following in your terminal.

```bash
lein typed infer-spec clojure.math.combinatorics :test-timeout-ms 3000
```

Specs will be generated in the `clojure.math.combinatorics` source file
between two special `Start` and `End` comments at the beginning of the
file like this:

```clojure
;; Start: Generated by clojure.core.typed - DO NOT EDIT
(s/def
  ::alias__17018
  (s/or
    :G__17805
    (partial instance? clojure.lang.IAtom)
    :char?
    char?
    :keyword?
    keyword?
    :int?
    int?))
(s/fdef
  all-different?
  :args
  (s/cat :s (s/coll-of ::alias__17018))
  :ret
  boolean?)
(s/fdef
  bounded-distributions
  :args
  (s/cat :m (s/coll-of int?) :t int?)
  :ret
  (s/coll-of (s/coll-of (s/tuple int? int? int?))))
...
...
;; End: Generated by clojure.core.typed - DO NOT EDIT
```

To generate core.typed types, run this command instead:

```bash
lein typed infer-type clojure.math.combinatorics :test-timeout-ms 3000
```

Notice that in addition to top-level annotation, inline annotations are
also provided.

```clojure
;; Start: Generated by clojure.core.typed - DO NOT EDIT
(declare)
(t/ann
  all-different?
  [(t/Coll (t/U t/Int (t/Atom1 t/Int) ':a ':b Character)) :-> Boolean])
(t/ann
  bounded-distributions
  [(t/Vec t/Int) t/Int :-> (t/Coll (t/Vec '[t/Int t/Int t/Int]))])
...
...
;; End: Generated by clojure.core.typed - DO NOT EDIT

...
(defn- multi-comb
  "Handles the case when you want the combinations of a list with duplicate items."
  [l t]
  (let [f (frequencies l),
        v (vec (distinct l)),
        domain (range (count v))
        m (vec (for ^{::t/ann t/Int} [^{::t/ann t/Int} i domain] (f (v i))))
        qs (bounded-distributions m t)]
    (for ^{::t/ann (t/Coll (t/U t/Int Character))} [^{::t/ann (t/Vec '[t/Int t/Int t/Int])} q qs]
      (apply concat
             (for ^{::t/ann (t/Coll (t/U t/Int Character))} [^{::t/ann '[t/Int t/Int t/Int]} [index this-bucket _] q]
               (repeat this-bucket (v index)))))))
```

Enjoy!

## License

Copyright © 2017 Ambrose Bonnaire-Sergeant

Distributed under the Eclipse Public License, the same as Clojure.
