(ns carbon-intensity.core
  (:gen-class)
  (:require [clj-http.client :as http]))

(defonce carbon-intensity "https://api.carbonintensity.org.uk/intensity")

(defn -main
  [& args]
  (println (str (java.time.Instant/now) "\n"
                (System/getenv "INFLUXDB_URI") "\n"
                (System/getenv "INFLUXDB_TOKEN") "\n"
                (System/getenv "INFLUXDB_ORG") "\n"
                (System/getenv "INFLUXDB_BUCKET"))))
