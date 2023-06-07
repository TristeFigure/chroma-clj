(ns chroma-clj.core-test
  (:use clojure.pprint)
  (:require [clojure.test    :refer :all]
            [chroma-clj.core :as    chroma :reload true]
            [libpython-clj2.python :as py]))

(def ^:dynamic *test-coll*)

(defn fixture-chroma-map [f]
  (binding [chroma/*client* (chroma/client)]
    (chroma/reset)
    (binding [*test-coll* (chroma/chroma-map {:cat :meow, :dog :bark, :bird :tweet})]
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

(deftest test-lookup
  (testing "Lookup values in the ChromaMap"
    (is (= (get *test-coll* :cat) :meow))
    (is (= (get *test-coll* :dog) :bark))
    (is (= (get *test-coll* :bird) :tweet))))

(deftest test-assoc
  (testing "Assoc a value in the ChromaMap"
    (let [new-coll (assoc *test-coll* :fish :splash)]
      (is (= (get new-coll :fish) :splash)))))

(deftest test-dissoc
  (testing "Dissoc a value in the ChromaMap"
    (let [new-coll (dissoc *test-coll* :bird)]
      (is (and (not= (get new-coll :bird) :tweet)
               (nil? (get (.m new-coll) :bird)))))))

(deftest test-count
  (testing "Count of entries in the ChromaMap"
    (is (= (count *test-coll*) 3))))

(deftest test-similar-key
  (testing "Finding a similar key in the ChromaMap"
    ;; assuming the chroma-db returns ":cat" for "cat-like"
    (is (= (get *test-coll* "cat-like") :meow))))