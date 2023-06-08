(ns chroma-clj.core-test
  (:use clojure.pprint)
  (:require [clojure.test    :refer :all]
            [chroma-clj.core :as    chroma :reload true]
            [libpython-clj2.python :as py]))

(def ^:dynamic *test-map*)
(def ^:dynamic *test-set*)
(def ^:dynamic *test-set-by*)

(defn fixture-chroma-map [f]
  (binding [chroma/*client* (chroma/client)]
    (chroma/reset)
    (binding [*test-map*    (chroma/chroma-map {:cat :meow, :dog :bark, :bird :tweet})
              *test-set*    (chroma/chroma-set #{"cat" "dog" "bird"})
              *test-set-by* (chroma/chroma-set [{:id 1 :sentence "hello world"}
                                                {:id 2 :sentence "open sesame"}
                                                {:id 3 :sentence "abracadabra"}])]
      (f))))

(use-fixtures :each fixture-chroma-map)

(defn chroma-collection? [x]
  (let [x (py/->jvm x)]
    (and (map? x) (-> x :type (= :collection)))))

(deftest test-client-fns
  (testing "ChromaDB Collection Functions"
    (chroma/create-collection "testname")
    (is (sequential?        (chroma/list-collections)))
    (is (chroma-collection? (chroma/get-collection "testname")))
    (is (chroma-collection? (chroma/get-or-create-collection "testname")))
    (is (nil?               (chroma/delete-collection "testname")))
    (is (number?            (chroma/heartbeat)))
    (is (nil?               (chroma/reset)))))

(deftest test-collection-fns
  (testing "ChromaDB Collection Functions"
    (chroma/on-collection
     (chroma/create-collection "testname")

     (is (= (chroma/count) 0))

     (let [cnt (chroma/count)]
       ;; either one at a time
       (chroma/add :ids "id1"
                   :embeddings [1.5 2.9 3.4]
                   ;; Have to wrap metadatas in a vector otherwise
                   ;; ValueError: Expected metadatas to be a list, got {"source" "my-source"}
                   ;; Chromadb/api/types.py error ?
                   :metadatas [{"source" "my-source"}]
                   :documents "This is a document")
       (is (= (chroma/count) (inc cnt))))

     (let [cnt (chroma/count)]
       (chroma/add :ids ["uri9" "uri10"]
                   :embeddings [[1.5 2.9 3.4] [9.8 2.3 2.9]]
                   :metadatas [{"style" "style1"} {"style" "style2"}]
                   :documents ["This is a document" "That is a document"])
       (is (= (chroma/count) (+ 2 cnt))))

     (let [cnt (chroma/count)]
       (chroma/upsert :ids ["id1"]
                      :embeddings [[1.5 2.9 3.4]]
                      :metadatas [{"source" "my-source"}]
                      :documents ["This is a document"])
       (is (= (chroma/count) cnt)))

     (is (= (-> (chroma/get :where          {"style" "style2"}
                            :where-document {"$contains" "That"}
                            :limit          1
                            :offset         0)
                :ids first)
            "uri10"))

     (is (= (-> (chroma/query :query-embeddings [[1.1 2.3 3.2]]
                              :n-results 1
                              :where {"style" "style2"})
                :ids first)
            ["uri10"]))

     (is (nil? (chroma/delete :ids ["id1"])))

     (is (= (-> (chroma/peek) :ids)
            ["uri9" "uri10"])))))

;; -- Fuzzy hashmap
(deftest test-map-lookup
  (testing "Lookup values in the ChromaMap"
    (is (= (get *test-map* :cat) :meow))
    (is (= (get *test-map* :dog) :bark))
    (is (= (get *test-map* :bird) :tweet))))

(deftest test-map-assoc
  (testing "Assoc a value in the ChromaMap"
    (let [new-coll (assoc *test-map* :fish :splash)]
      (is (= (get new-coll :fish) :splash)))))

(deftest test-map-dissoc
  (testing "Dissoc a value in the ChromaMap"
    (let [new-coll (dissoc *test-map* :bird)]
      (is (and (not= (get new-coll :bird) :tweet)
               (nil? (get (.m new-coll) :bird)))))))

(deftest test-map-count
  (testing "Count of entries in the ChromaMap"
    (is (= (count *test-map*) 3))))

(deftest test-map-similar-key
  (testing "Finding a similar key in the ChromaMap"
    ;; assuming the chroma-db returns ":cat" for "cat-like"
    (is (= (get *test-map* "cat-like") :meow))))

;; -- Fuzzy set
(deftest test-set-contains
  (testing "Check if a value is present in the ChromaSet"
    (is      (contains? *test-set* "cat"))
    (is      (contains? *test-set* "dog"))
    (is      (contains? *test-set* "bird"))
    (is (not (contains? *test-set* "wolf")))))

(deftest test-set-disjoin
  (testing "Remove a value from the ChromaSet"
    (is (contains? *test-set* "cat"))
    (let [new-set (disj *test-set* "cat")]
      (is (not (contains? new-set "cat")))
      (is      (contains? new-set "dog"))
      (is      (contains? new-set "bird")))))

(deftest test-set-conj
  (testing "Add a value to the ChromaSet"
    (is (contains? *test-set* "cat"))
    (let [new-set (conj *test-set* "wolf")]
      (is (contains? new-set "cat"))
      (is (contains? new-set "dog"))
      (is (contains? new-set "bird"))
      (is (contains? new-set "wolf")))))

(deftest test-set-empty
  (testing "Empty the ChromaSet"
    (is (contains? *test-set* "cat"))
    (let [new-set (empty *test-set*)]
      (is (not (contains? new-set "cat")))
      (is (not (contains? new-set "dog")))
      (is (not (contains? new-set "bird"))))))

(deftest test-set-equiv
  (testing "Check equivalence of ChromaSets"
    (let [set1 (chroma/chroma-set #{"cat" "dog" "bird"})
          set2 (chroma/chroma-set #{"bird" "dog" "cat"})]
      (is (.equiv set1 set2)))))

(deftest test-set-similar-key
  (testing "Finding a similar key in the ChromaSet"
    ;; assuming the chroma-db returns "cat" for "minitiger"
    (is (not (contains? *test-set* "minitiger")))
    (is (= (get *test-set* "minitiger")
           "cat"))))

(deftest test-set-by
  (testing "Comparison by"
    (is (not (contains?      *test-set-by* "hello world")))
    (is (not (contains?      *test-set-by* "open sesame")))
    (is (not (contains?      *test-set-by* "abracadabra")))
    (is (not (contains?      *test-set-by* "abracadab")))

    (is (= (get *test-set-by* "hello")       {:id 1 :sentence "hello world"}))
    (is (= (get *test-set-by* "open")        {:id 2 :sentence "open sesame"}))
    (is (= (get *test-set-by* "abracadabra") {:id 3 :sentence "abracadabra"}))
    (is (= (get *test-set-by* "abracadab")   {:id 3 :sentence "abracadabra"}))))

(run-tests)