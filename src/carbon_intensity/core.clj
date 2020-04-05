(ns carbon-intensity.core
  (:gen-class)
  (:require [carbon-intensity.db :as db]
            [clj-http.client :as http]
            [clojure.data.json :as json]
            [taoensso.timbre :as log])
  (:import [java.time Duration Instant]))

(defonce api-url "https://api.carbonintensity.org.uk/intensity")
(defonce polling-interval-mins 30)
(defonce metric-name "carbon-intensity")

(defn get-carbon-intensity
  "Return actual carbon intensity for time.
  This function sleeps until the actual value of carbon intensity for
  a future or too recent ts is yet not available."
  [ts]
  ;; Time to use for exponential backoff retries.
  (let [max-wait-interval 60000
        min-wait-interval 1000]
    (loop [waiting-period min-wait-interval]
      (let [{:keys [status body]}
            (http/get (format "%s/%s" api-url ts))

            intensity-value
            (and (= status 200)
                 ;; Parse the intensity
                 (-> body json/read-str (get-in ["data" 0 "intensity" "actual"])))]
        (if (some? intensity-value)
          intensity-value
          ;; Else sleep for some time and retry with a new wait-ts
          ;; This branch in take if there was a failure on the server
          ;; side or if we queried the server too early for `ts`.
          (do
            (log/info "Sleeping for" waiting-period "millis.")
            (Thread/sleep waiting-period)
            (recur (if (< max-wait-interval waiting-period)
                     min-wait-interval
                     (* 2 waiting-period)))))))))

(defn start-ts
  "Return the time Instant from which to start querying the carbon
  intensity every half an hour (the maximum granularity supported)."
  [influxdb]
  (or
   ;; We have stored data already.
   (:ts (db/last-data-point influxdb metric-name))
   ;; Or we start querying from some time in the past to have a few
   ;; points from the beginning.
   (.minus (Instant/now)
           (Duration/ofDays 1))))

(defn ts-stream
  "Return a lazy stream/sequence of time instants for which to query the
  carbon intensity API."
  [interval-mins start-ts]
  (iterate (fn [ts]
             (.plus ts (Duration/ofMinutes interval-mins)))
           start-ts))


(defn poll-for-ts-stream
  "Poll the carbon intensity API forever to get values for time stamps
  in the input ts-stream.  If the timestamp is the past, query
  immediately, otherwise wait for a suitable period before
  querying (add a minute to the sleep time to account for clock skew, etc)."
  [influxdb ts-stream]
  (doseq [ts ts-stream]
    (log/info "Querying Carbon Intensity for:" (str ts))
    (if (.isBefore ts (Instant/now))
      (let [carbon-intensity (get-carbon-intensity ts)]
        (db/add-data-point! influxdb ts carbon-intensity)
        (log/info "Added: " (str ts) "|" carbon-intensity))
      (do
        ;; Wait for a suitable time and then query and add the point.
        (log/info "Sleeping until carbon intensity is available.")
        (Thread/sleep (.toMillis (Duration/between ts (Instant/now))))
        (db/add-data-point! influxdb (get-carbon-intensity ts))))))

(defn -main
  [& args]
  (let [influxdb (db/new-influxdb (System/getenv "INFLUXDB_URI")
                                  (System/getenv "INFLUXDB_ORG")
                                  (System/getenv "INFLUXDB_BUCKET")
                                  (System/getenv "INFLUXDB_TOKEN"))]
    (->> (start-ts influxdb)
         (ts-stream polling-interval-mins)
         (poll-for-ts-stream influxdb))))
