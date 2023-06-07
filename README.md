# ChromaDB Clojure Bindings

This project provides a Clojure interface to the [ChromaDB](https://docs.trychroma.com/) document database using [libpython-clj](https://github.com/clj-python/libpython-clj). 

## Setup

1. Ensure you have installed Clojure and Leiningen.
2. Clone this repository.
3. Install the Python dependencies:
   - `pip install chromadb`
4. Add this dependency to your project: `[chroma-clj "0.1.0"]`

## API Doc

TODO

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
(def my-client (client {:chroma-db-impl    "duckdb+parquet"
                        :persist-directory "/path/to/directory"}))
```

This client can then be used to create a collection:

```clojure
(create-collection my-client "my-collection")
```

## Client Methods

You can interact with ChromaDB at a high level using the following functions. Each of these functions accepts an optional first argument that is the client instance to use. If you don't provide it, they use the client in the `*client*` dynamic var.

```clojure
(with-client my-client
  (list-collections)
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

## A HashMap Implementation with Fuzzy Key Interpretation

```clojure
(let [my-map (chroma-map {:key1 "value1" :key2 "value2" :key3 "value3"})] ...)
```

Notable behaviors:

- `assoc` adds key-value pairs to the map and creates corresponding documents in ChromaDB.
- `dissoc` removes key-value pairs from the map and deletes corresponding documents in ChromaDB.
- `get` queries ChromaDB for the most similar key before retrieving values from the map.

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