(defproject org.clojars.tristefigure/chroma-clj "0.1.0"
  :description "Clojure wrapper for ChromaDB"
  :url "https://github.com/TristeFigure/chroma-clj"
  :license {:name "Apache 2.0"
            :url "https://www.apache.org/licenses/LICENSE-2.0"}
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [clj-python/libpython-clj "2.024"]]
  :repl-options {:init-ns chroma-clj.core}
  :profiles
  {:dev {:jvm-opts ["--add-modules" "jdk.incubator.foreign,jdk.incubator.vector"
                    "--enable-native-access=ALL-UNNAMED"]
         :dependencies [[codox-theme-rdash "0.1.2"]]}}
  :plugins [[lein-codox "0.10.8"]]
  :codox {:source-uri "https://github.com/TristeFigure/chroma-clj/blob/{version}/{filepath}#L{line}"
          :metadata   {:doc/format :markdown}
          :themes     [:rdash]})
