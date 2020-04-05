(ns carbon-intensity.db
  "Interface to InfluxDB HTTP API for storing carbon intensity values."
  (:require [clj-http.client :as http]
            [clojure.data.csv :as csv])
  (:import java.time.Instant))

(defn new-influxdb
  "A representation of the DB client for talking to InfluxBD."
  [endpoint org bucket token]
  {:endpoint endpoint
   :org      org
   :bucket   bucket
   :token    token})

(defn ^:private write!
  "Write a metric with (ts, value) to InfluxDB."
  [{:keys [endpoint org bucket token] :as _influxdb} metric-name ^Instant ts value]
  ;; https://v2.docs.influxdata.com/v2.0/reference/syntax/line-protocol/#elements-of-line-protocol
  (let [body (format "%s value=%s %s" metric-name value (.getEpochSecond ts))]
    (let [response (http/post (str endpoint "/api/v2/write")
                              {:query-params {:org       org
                                              :bucket    bucket
                                              :precision "s"}
                               :headers      {:authorization (format "Token %s" token)}
                               :content-type "text/plain"
                               :body         body})]
      (if (not= 204 (:status response))
        (throw (Exception. "Failed to write DB record.") )))))

(defn ^:private query
  "Query InfluxDB with the input flux query."
  [{:keys [endpoint org bucket token] :as _influxdb} flux-query]
  (let [{:keys [status body]}
        (http/post (str endpoint "/api/v2/query")
                   {:query-params {:org       org
                                   :bucket    bucket
                                   :precision "s"}
                    :headers      {:authorization (format "Token %s" token)
                                   :accept        "application/csv"
                                   :content-type  "application/vnd.flux"}
                    :body         flux-query})]
    (if (not= 200 status)
      (throw (Exception. "Failed to query DB."))
      (csv/read-csv body))))

(defn add-data-point!
  "Add a new data point with ts timestamp and carbon-intensity value."
  [influxdb ^Instant ts carbon-intensity]
  (write! influxdb "carbon-intensity" ts carbon-intensity))

(defn last-data-point
  "Get the most recent data point added to the database."
  [{:keys [bucket] :as influxdb} metric-name]
  ;; An arbitrary value for the period during which the last point
  ;; must have been added.
  (let [possible-downtime-hours (* 7 24)]
    (let [result
          (query influxdb
                 (format "from(bucket:\"%s\")
                          |> range(start:-%sh)
                          |> filter(fn : (r) => r._measurement == \"%s\")
                          |> last()"
                         bucket
                         possible-downtime-hours
                         metric-name))]
      (when-let [last-point (and (< 1 (count result))
                                 (apply zipmap (take 2 result)))]
        {:ts    (Instant/parse (get last-point "_time"))
         :value (get  last-point "_value")}))))
