(ns dh-async-example.core
  (:require [dh-async-example.util :refer [g p rand-bytes powermod pr-md5]]
            [clojure.core.async :refer [go put! take! chan close! <! >!]]))

;; Basic Diffie-Hellman sequenced with core.async
;; Note: not secure against MITM attack

(let [alice-to-bob-chan (chan)
      bob-to-alice-chan (chan)]

  ;; Alice's side of the handshake
  (let [alice-x (BigInteger. (rand-bytes (/ 2048 8)))
        alice-g-to-the-x (powermod g alice-x p)]
    (go (println "alice sends g^x  to bob")
        (>! alice-to-bob-chan alice-g-to-the-x))
    (go (let [bobgy (<! bob-to-alice-chan)]
          (println "alice recieves g^y from bob")
          (pr-md5 "alice" (powermod bobgy alice-x p)))))

  ;; Bob's side of the handshake
  (let [bob-y (BigInteger. (rand-bytes (/ 2048 8)))
        bob-g-to-the-y (powermod g bob-y p)]
    (go (let [alicegx (<! alice-to-bob-chan)]
          (println "bob recieves g^x from alice")
          (pr-md5 "  bob" (powermod alicegx bob-y p))))
    (go (println "bob sends g^y to alice")
        (>! bob-to-alice-chan bob-g-to-the-y))))


;; Using put! and take! instead of >! <!

;; Set up channels
(def alice-to-bob-chan (chan))
(def bob-to-alice-chan (chan))

;; Alice
(let [alice-x (BigInteger. (rand-bytes (/ 2048 8)))
      alice-g-to-the-x (powermod g alice-x p)]
  (put! alice-to-bob-chan alice-g-to-the-x)
  (take! bob-to-alice-chan
         (fn [bobgy] (pr-md5 "alice" (powermod bobgy alice-x p)))))

;; Bob
(let [bob-y (BigInteger. (rand-bytes (/ 2048 8)))
      bob-g-to-the-y (powermod g bob-y p)]
  (take! alice-to-bob-chan
         (fn [alicegx] (pr-md5 "  bob" (powermod alicegx bob-y p))))
  (put! bob-to-alice-chan bob-g-to-the-y))
