(ns dh-async-example.etc
  (:require [clojure.core.async
             :as a
             :refer [>! <! >!! <!! put! take! go chan buffer close! thread
                     alts! alts!! timeout sliding-buffer go-loop]]))

;; a bunch of random core.async examples - not related to the dh-async-example
;; just dropping them here for convenience during the meetup

;; via http://www.braveclojure.com/core-async/

;; unbuffered
(def echo-chan (chan))
(go (println (<! echo-chan)))
(>!! echo-chan "ketchup")

(def echo-buffer (chan 2))
(>!! echo-buffer "ketchup")
(go (println (<! echo-buffer)))

(def slide-buff (chan (sliding-buffer 2)))

(>!! slide-buff "one")
(>!! slide-buff "two")
(>!! slide-buff "three") ; one falls off (buffer size is 2)

(go (println (<! slide-buff)))
;;playsync.core> two

(go (println (<! slide-buff)))  ; chan empty
;;playsync.core> three

;; put lots of messages on a chan
(def hi-chan (chan))
(doseq [n (range 1000)]
  (go (>! hi-chan (str "hi " n))))

(<!! hi-chan)
(<!! hi-chan)
(<!! hi-chan)

;; in channel for recieving $$. out channel to dispense hot dogs.
;; stock with hotdog-count hotdogs. Will dispense 1 hd for $3 put on in channel.
(defn hotdog-machine
  [hotdog-count]
  (let [in (chan)
        out (chan)]
    (go (loop [hc hotdog-count]
          (if (> hc 0)
            (let [input (<! in)]
              (if (= 3 input)
                (do (>! out "hotdog")
                    (recur (dec hc)))
                (do (>! out "wilted lettuce")
                    (recur hc))))
            (do (close! in)
                (close! out)))))
    [in out]))

(def myhdm (hotdog-machine 2))
(def hdin (first myhdm))
(def hdout (second myhdm))

(>!! hdin "lint")
(<!! hdout)
;;"wilted lettuce"
(>!! hdin 3)
(<!! hdout)
"hotdog"
(>!! hdin 3)
(<!! hdout)
"hotdog"
(>!! hdin 3)
(<!! hdout)
;;nil         ; all out of hotdogs

;; pipeline processing
(let [c1 (chan)
      c2 (chan)
      c3 (chan)]
  (go (>! c2 (clojure.string/upper-case (<! c1))))
  (go (>! c3 (clojure.string/reverse (<! c2))))
  (go (println (<! c3)))
  (>!! c1 "redrum"))
;; => MURDER

(defn upload
  [headshot c]
  (go (Thread/sleep (rand 100))
      (>! c headshot)))

(let [c1 (chan)
      c2 (chan)
      c3 (chan)]
  (upload "serious.jpg" c1)
  (upload "fun.jpg" c2)
  (upload "sassy.jpg" c3)
  (let [[headshot channel] (alts!! [c1 c2 c3])]
    (println "Sending headshot notification for" headshot)))

;; => Sending headshot notification for <random one of the three>.jpg

;; with timeout
;; timeout of 40 with random upload 0-100, should timeout 60% of the time.
(let [c1 (chan)]
  (upload "serious.jpg" c1)
  (let [[headshot channel] (alts!! [c1 (timeout 40)])]
    (if headshot
      (println "Sending headshot notification for" headshot)
      (println "Timed out!"))))

;; test it (eval the above multiple times)
;; Timed out!
;; Timed out!
;; Sending headshot notification for serious.jpg
;; Timed out!
;; Sending headshot notification for serious.jpg
;; etc. Got 12/20 timeouts = 60% ! Amazeballs!


(defn upper-caser
  [in]
  (let [out (chan)]
    (go (while true (>! out (clojure.string/upper-case (<! in)))))
    out))

(defn reverser
  [in]
  (let [out (chan)]
    (go (while true (>! out (clojure.string/reverse (<! in)))))
    out))

(defn printer
  [in]
  (go (while true (println (<! in)))))

(def in-chan (chan))
(def upper-caser-out (upper-caser in-chan))
(def reverser-out (reverser upper-caser-out))
(printer reverser-out)

(>!! in-chan "redrum")
; => MURDER

(>!! in-chan "repaid")
; ==> DIAPER


;;;; Seen on clojurians slack channel

(defn play []
  (let [ch (chan 1)]
    (go-loop [cnt 0]
      (if (= cnt 0)
        (do
          (>! ch :ping)
          (println "Ping")
          (<! (timeout 500)))
        (let [ball (<! ch)]
          (if (= ball :ping)
            (do
              (>! ch :pong)
              (println "Pong")
              (<! (timeout 500)))
            (do
              (>! ch :ping)
              (println "Ping")
              (<! (timeout 500))))))
      (if (< cnt 10)
        (recur (inc cnt))
        (println "Game over!")))))

(play)

;; Nolen http://cognitect.com/events/webinars#designing-front-end-applications-with-core-async
;; Asynch tasks plus streams and queues (best rep for mouse clicks / events
;; illusion of blocking even in single-threaded envs. like JS
;; channels: conduit between different processes
;; channels: fundamental abstraction.
;;    - Use buffers - deal with uneven processing rates / backpressure
;; transducers - how values enter map/filter and exit
;; go blocks - units of async execution
;; put on, take off, select from multiple.
;;
;; CSP abstraction naturally captures both one-off async tasks as well as async streams/queues
;; core.async go blocks are a source transform to give the illusion of blocking even in single threaded contexts (i.e. JavaScript hosts)
;;Callback hell begone!
;;

;; Baldridge conj 2013 core.async talk on youtube.
;; https://www.youtube.com/watch?v=enwIIGzhahw

;; Vanderhart http://cognitect.com/events/training-sessions/core-async-clojurewest-2015
;; blocking
(def mychan (chan))
(future (loop [] (when-let [v (<!! mychan)] (println v) (recur))))
(>!! mychan "foo1")
(>!! mychan "foo1")
(close! mychan)

;; mytake recurses from its callback when it gets a value
(def mychan (chan))
(defn mytake [] (take! mychan (fn [v] (println v) (mytake))))
(put! mychan "baz" (fn [s] (println "worked")))
(close! mychan)

;; parking - only in go blocks
(def mychan (chan))
(go (println (<! mychan)))
(go (>! mychan "5"))
(close! mychan)

; print anything put on channel
(go (loop [] (when-let [v (<! mychan)] (println v) (recur))))
(>!! mychan "5")
(close! mychan)
(>!! mychan "5") ; false

;; w/ transducer
(def mychan (chan 1 (map inc)))  ; must use a buffer
(go (loop [] (when-let [v (<! mychan)] (println v) (recur))))
(>!! mychan 5)  ; 6

