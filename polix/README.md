# Polix

Polix is a dsl for writing declarative *policies* -- simple terminating programs that evalute to a boolean true/false value. Polix operates against a set of *URIs of Resources* and a *Document* a key-value data structure. A policy asserts that a given *Document* resolves to full set of *URIs of Resources* when the provided policy is applied, returning true and returning false if it does not. 

Polix can evalute policies in a bidirectional manner. Polix can describe the document that would resolve to the provided URI given a policy, and Polix can describe the set of resources that a given document and policy would resolve to. 

The *Document* provides an key-value interface but can be implemented either as a static datastructure or by mapping to another data store like a sql database. The *Document* can also be composed from one or more documents. 

```clojure
(defprotocol Document
  (project [this & keys])
  (get [this key])
  (store [this key])
  (merge [this other-document]))
```

A document describes it's schema -- right now what keys are available. Merging two documents operates from left-to-right.

A *Policy* a combination of a schema -- what document keys are necessary to fulfill this policy -- and a a policy to apply to the document. Policy documents are written as a vector DSL similar to honeysql or malli. 

``` clojure
(defpolicy MyGreatPolicy
  "Docstring"
  [:or [:= :doc/actor-role "admin"]
   [:match :uri/uri "myprotcol:" :doc/actor-name "/*"]])
```

Policies can be evaluated with three functions:

``` clojure
(asserts? MyGreatPolicy Document "myprotocol:edpaget/polix") ;; => returns true if MyGreatPolicy implies "myprotocol:edpaget/polix"

(implied MyGreatPolicy Document) ;; => returns a #{} of uris implied by the policy and document

(implied MyGreatPolicy "myprotocol:edpaget/polix") ;; => returns the Document implied by the uri
```

An `Evaluator` is a function can be supplied as the first argument to any of these functions to customize the behavior the policy engine. The *default-evaluator* dynamic var will be used when an evalutor is not supplied.

## Usage

```clojure
(require '[polix.core :as polix])

(polix/hello "World")
;; => "Hello, World!"
```

## Development

```bash
# Run tests
clojure -X:test

# Start REPL
clojure -M:repl

# Lint code
clojure -M:lint
```
