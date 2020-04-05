(ns carbon-intensity.core
  (:gen-class)
  (:require [carbon-intensity.db :as db]
            [clj-http.client :as http]
            [clojure.data.json :as json]
            [taoensso.timbre :as log])
  (:import [java.time Duration Instant]
           java.time.temporal.ChronoUnit))

(defonce api-url "https://api.carbonintensity.org.uk/intensity")
(defonce polling-interval-millis (* 30 60 1000))
(defonce metric-name "carbon-intensity")

(defn get-carbon-intensity
  "Return actual carbon intensity for time.
  This function returns `nil` if the value for time instant is not
  available yet. If the server fails to service the request, we retry
  with an exponential backoff strategy."
  [ts]
  ;; Time to use for exponential backoff retries.
  (let [max-wait-interval 60000
        min-wait-interval 1000]
    (loop [waiting-period min-wait-interval]
      (let [{:keys [status body]} (http/get (format "%s/%s" api-url ts))]
        (if (= status 200)
          ;; Will be `nil` if the value for `ts` is not available yet.
          (-> (json/read-str body)
              (get "data")
              first
              (get-in ["intensity" "actual"]))
          (do (log/info "Server response: " status body)
              (log/info "Sleeping for" waiting-period "and retrying.")
              (Thread/sleep waiting-period)
              (recur (if (= max-wait-interval waiting-period)
                       min-wait-interval
                       (min max-wait-interval (* 2 waiting-period))))))))))

(defn start-ts
  "Return the time Instant from which to start querying the carbon
  intensity every half an hour (the maximum granularity supported)."
  [influxdb]
  (or
   ;; We have stored some data already in the past.
   (:ts (db/last-data-point influxdb metric-name))
   ;; Or we start querying from some time in the past to have a few
   ;; points from the beginning.
   (.minus (.truncatedTo (Instant/now) ChronoUnit/HOURS)
           ;; Arbitrarily chosen number of past days to have some data
           ;; that can be graphed.
           (Duration/ofDays 5))))

(defn ts-stream
  "Return a lazy stream/sequence of time instants for which to query the
  carbon intensity API."
  [interval-millis start-ts]
  (iterate (fn [ts]
             (.plus ts (Duration/ofMillis interval-millis)))
           start-ts))


(defn poll-for-ts-stream
  "Poll the carbon intensity API forever to get values for time stamps
  in the input ts-stream.  If the timestamp is the past, query
  immediately, otherwise wait for a suitable period before querying again."
  [influxdb ts-stream]
  (doseq [ts ts-stream]
    (log/info "Querying Carbon Intensity for:" (str ts))
    (loop [carbon-intensity (get-carbon-intensity ts)]
      (if (some? carbon-intensity)
        (do
          (db/add-data-point! influxdb ts carbon-intensity)
          (log/info "Added: " (str ts) "|" carbon-intensity))
        (do
          (log/info "Carbon intensity for " ts " not yet available.")
          (log/info "Sleeping for" polling-interval-millis " millis.")
          (Thread/sleep polling-interval-millis)
          (recur (get-carbon-intensity ts)))))))

(defn -main
  [& args]
  (let [influxdb (db/new-influxdb (System/getenv "INFLUXDB_URI")
                                  (System/getenv "INFLUXDB_ORG")
                                  (System/getenv "INFLUXDB_BUCKET")
                                  (System/getenv "INFLUXDB_TOKEN"))]
    (->> (start-ts influxdb)
         (ts-stream polling-interval-millis)
         (poll-for-ts-stream influxdb))))
