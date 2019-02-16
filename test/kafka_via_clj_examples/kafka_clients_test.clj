(ns kafka-via-clj-examples.kafka-clients-test
  (:require [clojure.test :refer [deftest testing is]])
  (:import
    [org.apache.kafka.clients.consumer     KafkaConsumer ConsumerConfig]
    [org.apache.kafka.clients.producer     KafkaProducer ProducerRecord]
    [org.apache.kafka.common.serialization StringSerializer
                                           StringDeserializer]))

(def bootstrap-server "localhost:9092")
(def topic            "test")
(def producer-topic   topic)

(defn- build-consumer
  "Create the consumer instance to consume
from the provided kafka topic name"
  [consumer-topic bootstrap-server group-id]
  (let [consumer-props
        {"bootstrap.servers"  bootstrap-server
         "group.id"           group-id
         "key.deserializer"   StringDeserializer
         "value.deserializer" StringDeserializer
         "auto.offset.reset"  "earliest"
         "enable.auto.commit" "false"}]

    (doto (KafkaConsumer. consumer-props)
      (.subscribe [consumer-topic]))))

(defn- build-producer
  "Create the kafka producer to send on messages received"
  [bootstrap-server]
  (let [producer-props {"value.serializer"  StringSerializer
                        "key.serializer"    StringSerializer
                        "bootstrap.servers" bootstrap-server}]
    (KafkaProducer. producer-props)))

(deftest kafka-clients-test
  (let [group-id (str "kafka-clients-comsumer-" (java.util.UUID/randomUUID))
        msg      (str "kafka-clients-msg-"      (java.util.UUID/randomUUID))

        consumer (build-consumer topic bootstrap-server group-id)
        producer (build-producer       bootstrap-server)

        _        (.send producer (ProducerRecord. producer-topic msg))

        records  (.poll consumer 100)
        record0  (last records)]

    (is (= msg (when record0
                 (.value record0))))

    (.commitAsync consumer)
    (.close producer)
    (.close consumer)))
