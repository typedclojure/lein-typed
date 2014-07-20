# 0.3.5

- Fix [CTYP-157](http://dev.clojure.org/jira/browse/CTYP-157)
  - pass a collection of namespaces to `check-ns`, instead of one at a time

# 0.3.4

- Add `(shutdown-agents)` to operations (thanks Sean Corfield)

# 0.3.3

- ClojureScript uses check-ns* to check namespaces
  - for core.typed 0.2.36+

# 0.3.1

- Add Clojurescript support with `check-cljs`
- When type checking fails or if no namespaces provided,
  process exits with an error signal
