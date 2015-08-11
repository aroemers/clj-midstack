(ns clj-midstack.core
  "The core namespace of explicit stacks."
  (:import (clojure.lang IFn Keyword)))


;;; The main reactor

(defprotocol FrameFn
  (call [this context]
    "By implementing this, the type can be used for calling a stack
     frame function."))

(defrecord State [stack index direction context])

(defn reactor
  "Given a state record/map, executes the stack frame it points to
   and calculates the next state.

   The state consists of four parts. The stack, the index, the
   direction and the context. The stack is a sequence of of maps,
   i.e. stack frames. The reactor takes the value under the key
   corresponding to the current direction -- either :in or :out --
   from the map at the given index. If the value is non-nil, it
   should be a function taking a context map.

   The result of the function is merged with this context map. The
   new state contains this merged context, and also contains an
   updated index or direction. The direction is automatically flipped
   to :out when the last stack frame was executed in the :in
   direction (in that case the index is unaltered). When the last
   :out frame was executed, the index will be -1, and a :done? entry
   will be set.

   Whenever an exception is thrown by a stack frame function, the
   new state is still calculated. An ExceptionInfo is thrown,
   containing both the :before and :after states. These may be used
   to retry from the point where the exception was thrown, or
   continue with the next frame, respectively."
  [{:keys [stack index direction context] :as state}]
  (if (= index -1)
    (assoc state :done? true)
    (let [frame (get stack index)
          frame-fn (get frame direction)
          [thrown new-context] (if frame-fn
                                 (try
                                   [nil (merge context (call frame-fn context))]
                                   (catch Exception ex
                                     [ex context]))
                                 [nil context])
          [new-direction new-index] (condp = [direction index]
                                      [:out index] [:out (dec index)]
                                      [:in (dec (count stack))] [:out index]
                                      [:in index] [:in (inc index)])
          done? (= new-index -1)
          new-state (merge state
                           {:direction new-direction
                            :index new-index
                            :context new-context}
                           (when done? {:done? true}))]
      (if thrown
        (throw (ex-info "Error while calling stack-frame in reactor." {:before state :after new-state} thrown))
        new-state))))


(defn mk-state
  "Create a new state based on the given stack, possibly with an
   initial map for the context."
  ([stack]
    (mk-state stack nil))
  ([stack init-context]
   (map->State {:stack stack
                :index 0
                :direction :in
                :context init-context})))


;;; Dispatch for stack frame execution

(extend-protocol FrameFn
  IFn
  (call [f context]
    (f context))
  Keyword
  (call [kw context]
    (if-let [nspace (namespace kw)]
      (if-let [fnvar (ns-resolve (symbol nspace) (symbol (name kw)))]
        (fnvar context)
        (throw (IllegalArgumentException. (str "Could not find var for keyword frame function" kw))))
      (throw (IllegalArgumentException. (str "Keyword frame function " kw " must be fully qualified")))))
  Object
  (call [this _]
    (throw (IllegalArgumentException. (str "Unrecognized type for frame function: " (class this))))))


;;; Utility and example functions

(defn complete-run*
  "Given a state, runs all frames in and out until done."
  [state]
  (loop [state state]
    (if (:done? state)
      (:context state)
      (recur (reactor state)))))

(defn complete-run
  "Given a stack, runs all frames in and out until done."
  [stack]
  (complete-run* (mk-state stack)))


(defn continue
  "Given an ExceptionInfo exception, continues to run all frames
   after where the exception was thrown."
  [exinfo]
  (complete-run* (-> exinfo ex-data :after)))


(defn retry
  "Given an ExceptionInfo, retries to run all frames from the point
   where the exception was thrown."
  [exinfo]
  (complete-run* (-> exinfo ex-data :before)))


(defn until-value
  "An example of a more custom \"driver\", which switches to the
   :out direction as soon as a :value entry is set in the context,
   skipping any frames after the current index."
  [stack]
  (loop [{:keys [done? context index direction] :as state} (mk-state stack)]
    (if done?
      context
      (if (contains? context :value)
        (complete-run* (merge state {:direction :out
                                     :index (if (= direction :in) (dec index) index)}))
        (recur (reactor state))))))
