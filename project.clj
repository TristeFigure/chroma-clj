(defproject chroma-clj "0.1.0"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
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
