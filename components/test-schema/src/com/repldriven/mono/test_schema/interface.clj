(ns com.repldriven.mono.test-schema.interface
  (:require
    com.repldriven.mono.test-schema.system

    [com.repldriven.mono.test_schemas.pets :as pets]

    [protojure.protobuf :as proto])
  (:import
    (com.repldriven.mono.test_schemas.pets PetProto$Pet)))

(def pb->Pet pets/pb->Pet)
(defn Pet->pb [m] (proto/->pb (pets/new-Pet m)))
(defn Pet->java [m] (PetProto$Pet/parseFrom (Pet->pb m)))
