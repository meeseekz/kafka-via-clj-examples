(ns kafka-via-clj-examples.kinsky-test
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.core.async :as a :refer [go <! >!]]
            [taoensso.timbre :as tb]

            [kinsky.client :as client]
            [kinsky.async  :as async]))

(tb/merge-config! {:level :warn})

;; -----------------------------------------------------------------------------
;; kinsky.client

(deftest kinsky-client-test
  (let [topic    "test"
        group-id (str "kinsky-consumer-" (java.util.UUID/randomUUID))
        msg      (str "kinsky-msg-"      (java.util.UUID/randomUUID))

        p (client/producer {:bootstrap.servers "localhost:9092"}
                           :keyword :edn)
        c (client/consumer {:bootstrap.servers "localhost:9092"
                            :group.id          group-id
                            :auto.offset.reset "earliest"}
                           :keyword :edn)]

    (client/subscribe! c topic)
    (client/send!      p topic :kinsky-msg1 msg)

    (let [out (client/poll! c 100)
          v   (-> out
                  (get-in [:by-topic topic])
                  last
                  :value)]
      (when-not v (println :poll-out out))
      (is (= msg v)))

    (client/close! c)
    (client/close! p)))


;; -----------------------------------------------------------------------------
;; kinsky.async

(deftest kinsky-async-test
  (let [topic    "test"
        group-id (str "kinsky-async-consumer-" (java.util.UUID/randomUUID))
        msg      (str "kinsky-async-msg-"      (java.util.UUID/randomUUID))

        ch-p            (async/producer {:bootstrap.servers "localhost:9092"}
                                        :keyword :edn)
        [ch-c-rec ch-c] (async/consumer {:bootstrap.servers "localhost:9092"
                                         :group.id          group-id
                                         :auto.offset.reset "earliest"}
                                        :keyword :edn)]

    (a/<!!
      (a/go
        (a/>! ch-c {:op :subscribe :topic topic})
        (a/>! ch-p {:op :record    :topic topic  :value msg})

        (a/<! (a/go-loop [n 0
                          record-curr {:type :record}]
                (let [[record ch] (a/alts! [ch-c-rec (a/timeout 1000)])]
                  (if (some->> record :type (contains? #{:record :rebalance}))
                    (recur (inc n) record)
                    (is (= msg (:value record-curr)))))))

        (a/>! ch-c {:op :stop})))))
