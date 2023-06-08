# ChromaDB Clojure Bindings

This project provides a Clojure interface to the [ChromaDB](https://docs.trychroma.com/) document database using [libpython-clj](https://github.com/clj-python/libpython-clj) as well as fuzzy map and set implementations relying on embeddings proximity.

## Setup

1. Ensure you have installed Clojure and Leiningen.
2. Clone this repository.
3. Install the Python dependencies:
   - `pip install chromadb`
4. Add this dependency to your project: `[chroma-clj "0.1.0"]`

## [API Doc](https://tristefigure.github.io/chroma-clj/index.html)

## Usage

First, initialize the library and create a new ChromaDB client:

```clojure
(ns my.namespace
  (:require [chroma-clj.core :as chroma]))

(chroma/set-client! (chroma/client))
```

## Creating a Client

You can create a ChromaDB client with the `client` function:

```clojure
(def my-client (chroma/client {:chroma-db-impl    "duckdb+parquet"
                               :persist-directory "/path/to/directory"}))
```

or use

```clojure
(chroma/set-client! (chroma/client))
```

This client can then be used to create a collection:

```clojure
(chroma/create-collection my-client "my-collection")
;; or
(chroma/with-client my-client
  (chroma/create-collection "my-collection"))
;; or after (chroma/set-client! (chroma/client))
(chroma/create-collection "my-collection")
```

## Client Methods

You can interact with ChromaDB at a high level using the following functions. Each of these functions accepts an optional first argument that is the client instance to use. If you don't provide it, they use the client in the `*client*` dynamic var.

```clojure
(with-client my-client
  (list-collections) ;; or (list-collections my-client) and so on ...
  (create-collection "testname")
  (get-collection "testname")
  (get-or-create-collection "testname")
  (delete-collection "testname")
  (reset)
  (heartbeat))
```

## Collection Methods

Once you have a collection, in the same manner, relying on the collection in the `*collection*` dynamic var or passing it directly.

```clojure
(on-collection my-collection
  (count)
  (add {:ids "id1"
        :embeddings [1.5 2.9 3.4]
        :metadatas {"source" "my-source"}
        :documents "This is a document"})
  (add {:ids ["uri9" "uri10"]
        :embeddings [[1.5 2.9 3.4] [9.8 2.3 2.9]]
        :metadatas [{"style" "style1"} {"style" "style2"}]
        :documents ["This is a document" "That is a document"]})
  (upsert {:ids "id1"
           :embeddings [1.5 2.9 3.4]
           :metadatas {"source" "my-source"}
           :documents "This is a document"}))

;; Or alternatively
(get my-collection)
(query my-collection
       {:query-embeddings [[1.1 2.3 3.2] [5.1 4.3 2.2]]
        :n-results 2
        :where {"style" "style2"}})
(delete my-collection :ids ["id1"])

;; Convenience. Get first 5 items.
(peek my-collection)
;; Advanced: manually create the embedding search index
(create-index my-collection))
```

As a reminder, the `with-client` and `on-collection` macros bind their argument to the `*client*` and `*collection*` dynamic vars, respectively. This allows you to omit the client or collection argument in calls to the functions in their body.

Sure, here's a more succinct version:

---

## Fuzzy datastructures

### Hashmap

```clojure
(let [my-map (chroma-map {:key1 "value1" :key2 "value2" :key3 "value3"})]
  ...)
```

Notable behaviors:

- `assoc` adds key-value pairs to the map and creates corresponding documents in ChromaDB.
- `dissoc` removes key-value pairs from the map and deletes corresponding documents in ChromaDB.
- `get` queries ChromaDB for the most similar key before retrieving values from the map.

### Set

```clojure
(let [my-set (chroma-set #{"value1" "value2" "value3"})]
  ...)
```

Notable behaviors:

- `conj` adds values to the set and creates corresponding documents in ChromaDB.
- `disj` removes values from the set and deletes corresponding documents in ChromaDB.
- `get` queries ChromaDB for the most similar value and returns the matching value from the set.
- `contains?` checks if a value is present in the set without querying ChromaDB.

In addition, the by argument can be used for indexing hashmap values by one of their keys. Here's a brief example:

```clojure
(let [my-map-set (chroma-set [{:id 1 :sentence "hello world"}
                              {:id 2 :sentence "open sesame"}
                              {:id 3 :sentence "abracadabra"}]
                             :by :sentence)]
    (get my-map-set "hello")       ; => {:id 1 :sentence "hello world"}
    (get my-map-set "open")        ; => {:id 2 :sentence "open sesame"}
    (get my-map-set "abracadabra") ; => {:id 3 :sentence "abracadabra"}
    (get my-map-set "abracadab")   ; => {:id 3 :sentence "abracadabra"}
  ...)

```

## Testing

Run the tests with:

```
lein test
```

## License
This project is licensed under the Apache 2.0 license. Please see the LICENSE file for more details.

## Contributing
We welcome contributions to improve and enhance this library! To contribute, please follow these guidelines:

Fork the repository and create your branch from main.
Make your changes and ensure that tests pass.
Write clear, concise commit messages.
Push your branch to your forked repository.
Submit a pull request, describing the changes you made and any relevant details.
We appreciate your efforts to improve this project and will review your pull request as soon as possible. Your contributions are greatly valued!