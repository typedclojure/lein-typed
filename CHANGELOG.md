# 0.4.6 - 10 May 2018

- Remove debug printing

# 0.4.5 - 10 May 2018

- Allow user to omit namespace argument to `infer-{spec,type}`
  - .clj{c} files with the shallowest absolute path are chosen,
    and the largest file size wins ties

# 0.4.4 - 9 May 2018

- Support `nil` argument for `:test-timeout-ms`

# 0.4.3 - 18 April 2018

- Add `infer-all` to generate both types and specs more efficiently

# 0.4.2 - 12 October 2017

- add `:instrument-opts` option

# 0.4.1 - 9 September 2017

- add `:infer-opts` option
- more aggressive shutdown in `infer-{type,spec}`

# 0.4.0

- Add `infer-type` and `infer-spec`

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
