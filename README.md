# Clj-midstack

A Clojure library for explicit, alterable, pausable, storable, restartable call STACKs. It is suitable for MIDdleware
and doing al kinds of things while MID execution.

## Usage

Wait for this README and tests to be completed. In the mean time, try this:

```clj
(use 'clj-midstack.core)
;=> nil

(defn lang [_]
  (println "Function lang evaluated!")
  {:lang "Clojure"})
;=> #'user/lang

(complete-run [{:in lang
                :out (fn [{:keys [lang what] :as context}]
                       {:value (str lang " is " what "!")})}
               {:in :user/what
                :out nil}])    ; both :in and :out are optional
;=> Function lang evaluated!
;=> IllegalArgumentException Could not find var for keyword frame function: user/what

(defn what [_] {:what "awesome"})
;=> #'user/what

(retry *e)
;=> {:value "Clojure is awesome!", :what "awesome", :lang "Clojure"}
```

## License

Copyright © 2015 Functional Bytes

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
