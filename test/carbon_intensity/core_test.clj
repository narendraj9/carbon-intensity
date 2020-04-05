(ns carbon-intensity.core-test
  (:require [carbon-intensity.core :as core]
            [carbon-intensity.db :as db]
            [clojure.test :refer :all])
  (:import [java.time Duration Instant]))

(deftest testing-looping-logic-assuming-db-api-calls-work
  (testing "Poller adds data returned for each timestamp in time stream"
    (let [saved-data   (atom [])
          ;; Making the stream finite for testing.
          input-stream (take 10 (core/ts-stream 1000 (Instant/now)))]
      ;; Mock calls to the API endpoint and calls to DB.
      (with-redefs [db/add-data-point!
                    (fn [_ ts value]
                      (swap! saved-data conj value))
                    core/get-carbon-intensity
                    (let [call-count (atom 0)]
                      (fn [ts]
                        (swap! call-count inc)
                        @call-count))]
        (core/poll-for-ts-stream {} input-stream)
        (is (= (range 1 (inc (count input-stream))) @saved-data)
            "Polling function saves all returned values for input time stream.")))))

(deftest checking-construction-of-ts-streams
  (testing "Test if the timestamps for querying the API are generated correctly"
    (let [constant-now (Instant/now)
          ts-interval  (* 1000 (rand-int 1000))
          ts-stream    (core/ts-stream ts-interval constant-now)]
      (is (every?
           #(= % (Duration/ofMillis ts-interval))
           (take 1000
                 (map (fn [a b] (Duration/between a b))
                      ts-stream
                      (rest ts-stream))))
          "First 1000 consecutive time stamps in the time stamp stream are correclty spaced."))))
