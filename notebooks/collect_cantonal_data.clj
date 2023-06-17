(ns collect-cantonal-data
  (:require [mundaneum.query :refer [search entity entity-data clojurize-claims describe label query *default-language*]]
            [mundaneum.properties :refer [wdt]]
            [nextjournal.clerk :as clerk]
            [clojure.java.shell :as shell]
            [cheshire.core :as json]
            [clojure.java.io :as io]
            [xtdb.api :as xt]))

;; - Fetch the cantons of Switzerland and their website URL from wikidata
;; - Enrich each canton with their shodan query and the general information queried by the [ASN CLI tool](https://github.com/nitefood/asn)

;; start up an instance of XTDB to cache the collected data
(defn start-xtdb! []
  (letfn [(kv-store [dir]
            {:kv-store {:xtdb/module 'xtdb.rocksdb/->kv-store
                        :db-dir      (io/file dir)
                        :sync?       true}})]
    (xt/start-node
      {:xtdb/tx-log         (kv-store "data/dev/tx-log")
       :xtdb/document-store (kv-store "data/dev/doc-store")
       :xtdb/index-store    (kv-store "data/dev/index-store")})))

(defonce node (start-xtdb!))
(defn db [] (xt/db node))

;; insert the cantons with their websites
(comment
  (->> (query `{:select [?canton ?cantonLabel ?websiteLabel]
                :where  [[~(entity "Switzerland") ~(wdt :contains-the-administrative-territorial-entity) ?canton]
                         [?canton ~(wdt :official-website) ?website]]})
       (map (fn [canton] [::xt/put (assoc canton :xt/id (:canton canton))]))
       (into [])
       (xt/submit-tx node)))

;; verify we got 26 cantons in the database
(->> (xt/q (db) '{:find  [?e]
                  :where [[?e :cantonLabel]]})
     count)
;; => 26

;; have a look at a single entry
(clojure.pprint/pprint
  (xt/q (db) '{:find  [(pull ?e [*])]
                                    :where [[?e :cantonLabel "Schwyz"]]}))


;; upsert the cantons with the ASN data
(defn asn-lookup [url]
  (-> (shell/sh "asn" "-Jn" url)
      :out
      (json/parse-string true)))

(comment
  (let [cantons-and-urls (xt/q (db) '{:find  [?e ?url]
                                      :where [[?e :cantonLabel]
                                              [?e :websiteLabel ?url]]})]
    (doall
      (doseq [canton cantons-and-urls]
        (let [asn-result  (asn-lookup (second canton))
              full-entity (assoc (xt/entity (db) (first canton))
                                 :asn asn-result)]
          (xt/submit-tx node [[::xt/put full-entity]]))))))

;; upsert the cantons with the shodan data
(defn shodan-lookup [url]
  (-> (shell/sh "asn" "-Js" url)
      :out
      (json/parse-string true)))

(comment
  (let [cantons-and-urls (xt/q (db) '{:find  [?e ?url]
                                      :where [[?e :cantonLabel]
                                              [?e :websiteLabel ?url]]})]
    (doall
      (doseq [canton cantons-and-urls]
        (let [asn-result  (shodan-lookup (second canton))
              full-entity (assoc (xt/entity (db) (first canton))
                                 :shodan asn-result)]
          (xt/submit-tx node [[::xt/put full-entity]]))))))
