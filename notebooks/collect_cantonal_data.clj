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

;; ---
;; what other data does wikidata offer for a given canton?
(comment
  ;; fetch the cantonal data
  (query `{:select [?canton ?cantonLabel ?websiteLabel ?licensePlateCodeLabel]
                  :where  [[~(entity "Switzerland") ~(wdt :contains-the-administrative-territorial-entity) ?canton]
                           [?canton ~(wdt :official-website) ?website]
                           [?canton ~(wdt :licence-plate-code) ?licensePlateCode]]})
;; => [{:canton :wd/Q834, :cantonLabel "Valais", :websiteLabel "https://www.vs.ch", :licensePlateCodeLabel "VS"} {:canton :wd/Q11911, :cantonLabel "Bern", :websiteLabel "https://www.be.ch", :licensePlateCodeLabel "BE"} {:canton :wd/Q11917, :cantonLabel "Canton of Geneva", :websiteLabel "https://www.ge.ch/", :licensePlateCodeLabel "GE"} {:canton :wd/Q11922, :cantonLabel "Glarus", :websiteLabel "https://gl.ch", :licensePlateCodeLabel "GL"} {:canton :wd/Q11925, :cantonLabel "Grisons", :websiteLabel "http://www.gr.ch", :licensePlateCodeLabel "GR"} {:canton :wd/Q11929, :cantonLabel "Solothurn", :websiteLabel "http://www.so.ch/", :licensePlateCodeLabel "SO"} {:canton :wd/Q11933, :cantonLabel "Zug", :websiteLabel "https://www.zg.ch/", :licensePlateCodeLabel "ZG"} {:canton :wd/Q11943, :cantonLabel "Zürich", :websiteLabel "https://www.zh.ch/", :licensePlateCodeLabel "ZH"} {:canton :wd/Q11972, :cantonLabel "Aargau", :websiteLabel "https://www.ag.ch", :licensePlateCodeLabel "AG"} {:canton :wd/Q12079, :cantonLabel "Appenzell Ausserrhoden", :websiteLabel "https://www.ar.ch/", :licensePlateCodeLabel "AR"} {:canton :wd/Q12094, :cantonLabel "Appenzell Innerrhoden", :websiteLabel "https://www.ai.ch/", :licensePlateCodeLabel "AI"} {:canton :wd/Q12121, :cantonLabel "Lucerne", :websiteLabel "http://www.lu.ch", :licensePlateCodeLabel "LU"} {:canton :wd/Q12146, :cantonLabel "Basel-Landschaft", :websiteLabel "https://www.baselland.ch/", :licensePlateCodeLabel "BL"} {:canton :wd/Q12172, :cantonLabel "Basel-Stadt", :websiteLabel "http://www.bs.ch/", :licensePlateCodeLabel "BS"} {:canton :wd/Q12404, :cantonLabel "Uri", :websiteLabel "https://www.ur.ch/", :licensePlateCodeLabel "UR"} {:canton :wd/Q12433, :cantonLabel "Schwyz", :websiteLabel "https://www.sz.ch", :licensePlateCodeLabel "SZ"} {:canton :wd/Q12573, :cantonLabel "Obwalden", :websiteLabel "http://www.ow.ch", :licensePlateCodeLabel "OW"} {:canton :wd/Q12592, :cantonLabel "Nidwalden", :websiteLabel "http://www.nw.ch", :licensePlateCodeLabel "NW"} {:canton :wd/Q12640, :cantonLabel "Fribourg", :websiteLabel "https://www.fr.ch/", :licensePlateCodeLabel "FR"} {:canton :wd/Q12697, :cantonLabel "Schaffhausen", :websiteLabel "https://www.sh.ch/", :licensePlateCodeLabel "SH"} {:canton :wd/Q12713, :cantonLabel "Thurgau", :websiteLabel "https://www.tg.ch/", :licensePlateCodeLabel "TG"} {:canton :wd/Q12724, :cantonLabel "Ticino", :websiteLabel "http://www.ti.ch", :licensePlateCodeLabel "TI"} {:canton :wd/Q12738, :cantonLabel "Neuchâtel", :websiteLabel "http://www.ne.ch/", :licensePlateCodeLabel "NE"} {:canton :wd/Q12746, :cantonLabel "canton St. Gallen", :websiteLabel "https://www.sg.ch/", :licensePlateCodeLabel "SG"} {:canton :wd/Q12755, :cantonLabel "Jura", :websiteLabel "https://www.jura.ch", :licensePlateCodeLabel "JU"} {:canton :wd/Q12771, :cantonLabel "canton Vaud", :websiteLabel "https://www.vd.ch/", :licensePlateCodeLabel "VD"}]

  ;; fetch the district data
  ;; for Canton Schwyz
  (query `{:select [?district ?districtLabel ?websiteLabel]
           :where  [[:wd/Q12433 ~(wdt :contains-the-administrative-territorial-entity) ?district]
                    [?district ~(wdt :official-website) ?website]]})
;; => [{:district :wd/Q74768, :districtLabel "March District", :websiteLabel "http://www.bezirk-march.ch/"} {:district :wd/Q74818, :districtLabel "Schwyz District", :websiteLabel "http://www.bezirk-schwyz.ch/"} {:district :wd/Q74823, :districtLabel "Höfe District", :websiteLabel "http://www.hoefe.ch/"} {:district :wd/Q74831, :districtLabel "Küssnacht District", :websiteLabel "http://www.kuessnacht.ch/"} {:district :wd/Q74874, :districtLabel "Einsiedeln District", :websiteLabel "http://www.einsiedeln.ch/"}]

  ;; for Canton Solothurn
  (query `{:select [?district ?districtLabel]
           :where  [[:wd/Q11929 ~(wdt :contains-the-administrative-territorial-entity) ?district]
                    [?district ~(wdt :official-website) ?website]]})
;; => []

  (query `{:select [?district ?districtLabel]
           :where  [[:wd/Q11929 ~(wdt :contains-the-administrative-territorial-entity) ?district]]})
;; => [{:district :wd/Q998044, :districtLabel "Bucheggberg-Wasseramt"} {:district :wd/Q1245681, :districtLabel "Dorneck-Thierstein"} {:district :wd/Q2021295, :districtLabel "Olten-Gösgen"} {:district :wd/Q2299305, :districtLabel "Solothurn-Lebern"} {:district :wd/Q2408658, :districtLabel "Thal-Gäu"}]

  ;; do those districts even have websites? - not as far as I could see.


  ;; on 1.1.2021 we had 2172 councils, how many does wikidata have now?

  )

(defn fetch-districts
  "Some districts have websites and some don't, this wrapper will ensure we get them all"
  [canton]
  (let [districts-with-url (query `{:select [?district ?districtLabel ?websiteLabel]
                                    :where  [[~canton ~(wdt :contains-the-administrative-territorial-entity) ?district]
                                             [?district ~(wdt :official-website) ?website]]})
        districts (query `{:select [?district ?districtLabel]
                           :where  [[~canton ~(wdt :contains-the-administrative-territorial-entity) ?district]]})]
    (->> (concat districts-with-url districts)
         (group-by :district)
         (map #(->> % val (apply merge)))
         (map #(assoc % :canton canton)))))

(comment
  (let [cantons (->> (query `{:select [?canton ?cantonLabel ?websiteLabel]
                              :where  [[~(entity "Switzerland") ~(wdt :contains-the-administrative-territorial-entity) ?canton]
                                       [?canton ~(wdt :official-website) ?website]]})
                     #_(filter #(contains? #{"Schwyz" "Solothurn"} (:cantonLabel %))))
        districts (->> cantons
                       (map #(fetch-districts (:canton %)))
                       (apply concat)
                       (map (fn [district] (assoc district :xt/id (:district district)))))]
    (doall
     (doseq [district districts]
       (xt/submit-tx node [[::xt/put district]]))))


  )

;; verify the districts got inserted
(count
  (xt/q (db) '{:find  [(pull ?e [*])]
               :where [[?e :districtLabel]]}))
;; => 279

(xt/q (db) '{:find  [?canton-label (count ?district)]
             :where [[?district :canton ?canton]
                     [?canton :cantonLabel ?canton-label]]
             :ordeal-by [[?canton-label :asc]]})
;; => #{["Basel-Stadt" 4] ["Uri" 21] ["Canton of Geneva" 46] ["Aargau" 12] ["Thurgau" 6] ["Appenzell Innerrhoden" 7] ["Jura" 4] ["Appenzell Ausserrhoden" 21] ["Grisons" 12] ["Schwyz" 7] ["Zürich" 13] ["canton St. Gallen" 9] ["Nidwalden" 12] ["Lucerne" 7] ["canton Vaud" 11] ["Ticino" 9] ["Zug" 12] ["Obwalden" 8] ["Solothurn" 6] ["Basel-Landschaft" 6] ["Schaffhausen" 27] ["Bern" 11] ["Neuchâtel" 7] ["Valais" 15] ["Glarus" 4] ["Fribourg" 8]}

;; some cantons like eg. Geneva probably included their councils into this result

;; ----

;; I've been looking at the wikidata entry of 2 municipalities and it might work this way to fetch them all
;; https://www.wikidata.org/wiki/Q67929
(comment
  (entity "municipality of Switzerland")
  (def municipalities
    (query `{:select [?municipality ?municipalityLabel]
             :where  [[?municipality ~(wdt :instance-of) ~(entity "municipality of Switzerland")]]}))

  (count municipalities)
;; => 3359

  ; huh, those are way too many entries

  ;; how many of those municipalities have a website?
  (count (query `{:select [?municipality ?municipalityLabel ?websiteLabel]
                  :where  [[?municipality ~(wdt :instance-of) ~(entity "municipality of Switzerland")]
                           [?municipality ~(wdt :official-website) ?website]]}))
;; => 2652

  ;; maybe we can get the BFS-NR for each municipality and then check it against a list of the current ones.
  ;; or the postal code as the BFS-NR isn't available for eg. Kammersrohr
  ;; what do we get if we just look up the municipality entity of Kammersrohr?
  (clojurize-claims (entity-data :wd/Q67929))
;; => {:SAPA-ID ["p/c28d1c08-b8c8-4355-a0a8-be32118f7fd2"], :OpenStreetMap-relation-ID ["1683531"], :locator-map-image ["Karte Gemeinde Kammersrohr 2011.png"], :topics-main-category [:wd/Q14553065], :local-dialing-code ["032"], :instance-of [:wd/Q70208], :flag-image ["CHE Kammersrohr Flag.svg"], :elevation-above-sea-level [{:amount "+600", :units "http://www.wikidata.org/entity/Q11573"}], :official-language [:wd/Q188], :GADM-ID ["CHE.19.5.9_1"], :postal-code ["4535"], :Whos-on-First-ID ["1193167691"], :coat-of-arms-image ["Kammersrohr-blason.svg"], :Commons-category ["Kammersrohr"], :official-name [{:de-ch "Kammersrohr"}], :HDS-ID ["001151"], :GeoNames-ID ["8533599"], :population [{:amount "+29", :units "1"} {:amount "+28", :units "1"}], :list-of-monuments [:wd/Q14913429], :external-data-available-at-URL ["https://ld.geo.admin.ch/boundaries/municipality/2549"], :image ["Ortsschild Kammersrohr Mai 2011.jpg"], :area [{:amount "+0.94", :units "http://www.wikidata.org/entity/Q712226"} {:amount "+0.95", :units "http://www.wikidata.org/entity/Q712226"}], :Swiss-municipality-code ["2549"], :licence-plate-code ["SO"], :country [:wd/Q39], :coordinate-location [["globe-coordinate" {:value {:latitude 47.253333333333, :longitude 7.5933333333333, :altitude nil, :precision 2.7777777777778E-4, :globe "http://www.wikidata.org/entity/Q2"}, :type "globecoordinate"}]], :Freebase-ID ["/m/0g4jzp"], :located-in-the-administrative-territorial-entity [:wd/Q661119]}
  )
