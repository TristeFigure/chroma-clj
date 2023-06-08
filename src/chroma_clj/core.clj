(ns chroma-clj.core
  "A Clojure library that provides a simple interface to interact with ChromaDB, a
   document-oriented database.

   The library includes functions to manage collections, perform CRUD operations on
   chroma documents, and utilize a special `ChromaMap` hashmap implementation with fuzzy key
   matching."
  (:use clojure.pprint)
  (:refer-clojure :exclude [count update get peek munge])
  (:require [libpython-clj2.python    :refer [call-attr-kw
                                              initialize!
                                              import-module
                                              from-import
                                              $c py. ]
                                      :as    py]
            [libpython-clj2.python.fn :as    py-fn]
            [clojure.walk             :refer [keywordize-keys]]
            [clojure.core             :as    c]
            [clojure.set              :as    set]
            [clojure.string           :as    str]))

(comment
  (defmacro observe [& body]
    (let [exp `(do ~@(map macroexpand body))]
      (pprint exp)
      exp))
  )


(if (find-ns 'codox.main)
  (do (def chroma-db nil)
      (def Client nil)
      (def Settings nil))
  (do (initialize!)
      (def ^:private chroma-db (import-module "chromadb"))
      (from-import "chromadb" Client)
      (from-import "chromadb.config" Settings)))

(defn client
  "Creates a new ChromaDB client instance.
  Usage: (client), (client settings)"
  ([]     ($c Client))
  ([opts] (py. chroma-db Client (Settings opts))))

(def ^:dynamic *client*
  "The dynamic var holding the current ChromaDB client."
  nil)

(defn set-client! [c]
  "Sets the root value of the *client*."
  (alter-var-root #'*client* (constantly c)))

(defmacro with-client
  "Binds the *client* dynamic var to the specified client 'client'
   and evaluates the body forms in that context."
  [client & body]
  (binding [*client* ~client]
    ~@body))

(defmacro ^:private $apply-a [item attr args]
  `(let [[pos-args# kw-args#] (py-fn/args->pos-kw-args ~args)]
     (call-attr-kw ~item ~(py-fn/key-sym-str->str attr)
                   pos-args# kw-args#)))

(defn- callable-python? [x]
  (satisfies? libpython-clj2.python.protocols/PyCall x))


(defn- preserve-shape [orig s]
  (cond (string?  orig) (str s)
        (symbol?  orig) (symbol s)
        (keyword? orig) (keyword (apply str (rest s)))
        :else           s))

(defn-          munge [s] (preserve-shape s (str/replace (str s) "-" "_")))
(defn-        unmunge [s] (preserve-shape s (str/replace (str s) "_" "-")))

(defmacro ^:private gen-py-fns [default fn-names]
  (let [fn-names (map #(if (sequential? %) % (vector %)) fn-names)]
    `(do ~@(for [[nme transform-f] fn-names]
             (let [nme   (symbol nme)
                   args' (gensym "args'")
                   obj'  (gensym "obj'")]
               `(defn ~nme [& ~'args']
                  (let [head#              (first ~'args')
                        [~'obj' & ~'args'] (if (callable-python? head#)
                                             ~'args'
                                             (cons ~default ~'args'))
                        f#                ~(if transform-f
                                             `(~transform-f #($apply-a %1 ~(-> nme munge) %&))
                                             `#($apply-a %1 ~(-> nme munge) %&))]
                    (apply f# ~'obj' ~'args'))))))))

(gen-py-fns *client* [list-collections
                      create-collection
                      get-collection
                      get-or-create-collection
                      delete-collection
                      reset
                      heartbeat])

(def ^:dynamic *collection*
  "The dynamic var holding the current ChromaDB collection."
  nil)

(defmacro on-collection
  "Binds the *collection* dynamic var to the specified collection 'collection'
   and evaluates the body forms in that context."
  [collection & body]
  `(binding [*collection* ~collection]
     (py/->jvm (do ~@body))))

(defn- mapmap [fns data]
  (let [default-fn (or (::default fns) identity)]
    (->> (for [[k v] data
               :let [f (c/get fns k default-fn)]]
         [k (f v)])
       (into {}))))

(defn- mapmapflat [fns data]
  (apply concat (mapmap fns (partition 2 data))))

(defn- ->|
  "Returns a function that behaves like `comp` but composes functions
  from left to right."
  [& fns]
  (apply comp (reverse fns)))

(defn- when|
  "Returns a function that will run the `fns` in order when `pred`
  succeeds or return the value that was passed in otherwise."
  [pred & fns]
  (let [chained-fns (apply ->| fns)]
    #(if (apply pred %&)
       (apply chained-fns %&)
       (if (= (c/count %&) 1)
         (first %&)
         %&))))

(defn- if|
  "Returns a function that will run `f` when `pred` succeeds or
  otherwise run `else` if it is provided (returns the passed value
  by default)."
  ([pred f]
   (if| pred f #(if (= 1 (c/count %&))
                  (first %&)
                  %&)))
  ([pred f else]
   #(if (apply pred %&)
      (apply f %&)
      (apply else %&))))

(def ^:private adapt-embeddings|
  (if| (->| first vector?)
       #(->> % (map py/->py-list) py/->py-list)
       py/->py-list))

(defn- empty->nil [x]
  (if (empty? x) nil x))

(defn- adapt-args| [f]
  (fn [collection & args]
    (->> args
         (partition 2)
         (mapcat (fn [[k v]]
                   [(munge k) v]))
         (mapmapflat
          {:documents        (when| vector? py/->python)
           :embeddings       adapt-embeddings|
           :query_embeddings adapt-embeddings|
           :where            py/->py-dict
           :where_document   py/->py-dict
           ::default         (when| vector? py/->py-list)})

         (apply f collection)

         (map (fn [[k v]]
                [(unmunge k) v]))
         (into {})
         empty->nil)))

(defn- keywordize-output-keys| [f]
  (fn [& args]
    (->> (apply f args)
         py/->jvm
         keywordize-keys)))

(gen-py-fns *collection* [modify
                          count
                          [add    adapt-args|]
                          [update adapt-args|]
                          [upsert adapt-args|]
                          [get    (->| adapt-args| keywordize-output-keys|)]
                          [peek   keywordize-output-keys|]
                          [query  (->| adapt-args| keywordize-output-keys|)]
                          [delete adapt-args|]
                          create-index])


;;  --- Hashmap with fuzzy key matching

(definterface ^:private ISimilarLookup
  (similarKey [k]))

;; An associative datatype that behaves like a normal Clojure hashmap, but with fuzzy
;; key interpretation (via embeddings similarity).
;; • When associng a key-value pair, it creates a document with the key as the name and
;;   content.
;; • When dissocing, it deletes the document with the same name as the key.
;; • When getting the key, it first queries ChromaDB with it and gets the first matching
;;   document. Then it queries the map using the document name as the key and returns the
;;   value.
(deftype ChromaMap [id collection m index]
  ISimilarLookup
  (similarKey [_ k]
    (if (empty? m)
      nil
      (-> (query collection :query_texts (py/->py-list [(str k)]) :n_results 1)
          :metadatas ffirst :id (->> (c/get index)))))

  clojure.lang.IPersistentMap
  (assoc [_ k v]
    (let [kid (name (gensym "chroma-map-key-"))]
      (add collection
           :documents (py/->python  [(str k)])
           :metadatas (py/->py-list [{:id kid}])
           :ids       (py/->py-list [(str k)]))
      (ChromaMap. id collection (assoc m k v) (assoc index kid k))))

  (without [_ k]
    ;; Add logic for deleting the document with the same name as the key.
    (delete collection :ids (py/->py-list [(str k)]))
    (ChromaMap. id collection
                (dissoc m k)
                (dissoc index (c/get (set/map-invert index) k))))

  clojure.lang.Counted
  (count [_] (c/count m))

  clojure.lang.Seqable
  (seq [_] (seq m))

  clojure.lang.ILookup
  (valAt [this k not-found]
    ;; Add logic for querying chromadb with the key and get the first matching document.
    (or (.valAt m k)
        (if-let [found-k (.similarKey this k)]
          (c/get m found-k not-found)
          not-found)))
  (valAt [this key] (.valAt this key nil))

  clojure.lang.IPersistentCollection
  (cons [this o]
    (if (map? o)
      (reduce (fn [acc [k v]] (.assoc acc k v))
              this
              o)
      (throw (IllegalArgumentException. "Can only conj maps onto ChromaMap"))))

  (empty [_]
    (ChromaMap. id (doto collection (delete)) {} {}))

  (equiv [_ o]
    (and (instance? ChromaMap o)
         (= m (.-m ^ChromaMap o)))))

(defn chroma-map
  "Creates a new ChromaMap instance with an optional 'id' and initial key-value pairs
   from 'm'.
   Usage: (chroma-map m)
          (chroma-map id m)"
  ([m]    (chroma-map (gensym "chroma-map-") m))
  ([id m] (assert *client* "No chromadb client int *client*.")
          (-> (ChromaMap. id (create-collection (str (gensym))) {} {})
              (merge m))))

;;  --- Set with fuzzy key matching

(deftype ChromaSet [id collection by s index]
  ISimilarLookup
  (similarKey [_ v]
    (if (empty? s)
      nil
      (-> (query collection :query_texts (py/->py-list [(str (by v))]) :n_results 1)
          :documents ffirst)))

  clojure.lang.IPersistentSet
  (disjoin [_ v]
    (let [txt (str (by v))]
      (delete collection :ids (py/->py-list [txt]))
      (ChromaSet. id collection by
                  (disj s v)
                  (dissoc index txt))))

  (contains [_ v]
    (contains? s v))
  
  (get [this v]
    (if (empty? s)
      nil
      (when-let [k (.similarKey this v)]
        (c/get s (c/get index k)))))

  clojure.lang.IPersistentCollection
  (cons [_ v]
    (let [txt   (str (by v))]
      (add collection
           :documents [txt]
           :ids       [txt])
      (ChromaSet. id collection by
                  (conj s v)
                  (assoc index txt v))))

  (empty [_]
    (ChromaSet. id (doto collection (delete)) by #{} {}))

  (equiv [_ o]
    (and (instance? ChromaSet o)
         (= s (.-s ^ChromaSet o))))

  clojure.lang.Seqable
  (seq [_]
    (seq s)))

(defn chroma-set
  "Creates a new ChromaSet instance with an optional 'id' and initial values from 's'.
   Usage: (chroma-set s)
          (chroma-set id s)"
  ([s]       (chroma-set (gensym "chroma-set-") s))
  ([id s]    (chroma-set id s identity))
  ([id s by] (assert *client* "No chromadb client in var *client*.")
             (-> (ChromaSet.  id  (create-collection (str (gensym)))  by  #{}  {})
                 (into s))))
