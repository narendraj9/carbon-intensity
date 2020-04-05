(defproject carbon-intensity "0.1.0"
  :description "Poll National Grid's Carbon Density API to collect data on gCO2/kwH."
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url  "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/data.json "1.0.0"]
                 [org.clojure/data.csv "1.0.0"]
                 [com.taoensso/timbre "4.10.0"]
                 [clj-http "3.10.0"]]
  :main ^:skip-aot carbon-intensity.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
