;; this is a hack that we currently need for clerk to play nice with specter
^{:nextjournal.clerk/error-on-missing-vars :off}
{}


(ns cantons-of-switzerland
  (:require [mundaneum.query :refer [search entity entity-data clojurize-claims describe label query *default-language*]]
            [mundaneum.properties :refer [wdt]]
            [nextjournal.clerk :as clerk]
            [clojure.java.shell :as shell]
            [com.rpl.specter :as sp]
            [cheshire.core :as json]))

;; I'll be starting off with the notebook and the mundaneum library by @jackrusher over here https://github.com/jackrusher/mundaneum/blob/master/notebooks/basics.clj

(comment
  (clerk/serve! {:watch-paths ["notebooks"] :show-filter-fn #(clojure.string/starts-with? % "notebooks")})
  (clerk/show! "notebooks/cantons_of_switzerland.clj")

  )


;; We're starting the exploration with Canton Schwyz as the starting node https://www.wikidata.org/wiki/Q12433

(wdt :instance-of)
(def canton-of-switzerland (entity "canton of Switzerland"))


;; Get all the cantons
^{::clerk/viewer clerk/table}
(query `{:select [?cantonLabel]
         :where [[?canton ~(wdt :instance-of) ~canton-of-switzerland]]})

;; With how many cantons is a given canton sharing it's border with?
(wdt :shares-border-with)

^{::clerk/viewer clerk/table}
(query `{:select [?cantonLabel [(count ?othercanton) ?count]]
         :where [[?canton ~(wdt :instance-of) ~canton-of-switzerland]
                 [?canton ~(wdt :shares-border-with) ?othercanton]]
         :group-by [?cantonLabel]
         :order-by [(desc ?count)]})

;; the result above most likely contains ?canton's which aren't swiss cantons, this it bordering with 14 in total

;; Get all the councils websites of Canton Schwyz
(def canton-schwyz (entity "Schwyz"))
(wdt :contains-the-administrative-territorial-entity)

^{::clerk/viewer clerk/table}
(query `{:select [?gemeindeLabel ?websiteLabel]
         :where [[~canton-schwyz ~(wdt :contains-the-administrative-territorial-entity) ?bezirk]
                 [?bezirk ~(wdt :contains-the-administrative-territorial-entity) ?gemeinde]
                 [?gemeinde ~(wdt :official-website) ?website]]})

;; Question to self: Does canton schwyz have 30 councils?

;; ---

;; ## Getting the information of a website via `ASN`

;; example call: `asn -Jn https://galgenen.ch`
(-> (shell/sh "asn" "-Jn" "http://galgenen.ch")
  :out
  (json/parse-string true))

;; ## Getting all the official URLs for the cantonal websites of Switzerland
^{::clerk/viewer clerk/table}
(query `{:select [?cantonLabel ?websiteLabel]
         :where [[~(entity "Switzerland") ~(wdt :contains-the-administrative-territorial-entity) ?canton]
                 [?canton ~(wdt :official-website) ?website]]})

(defn asn-lookup [url]
  (-> (shell/sh "asn" "-Jn" url)
    :out
    (json/parse-string true)))

(def cantonal-asn-lookup
  (let [cantons-and-websites (query `{:select [?cantonLabel ?websiteLabel]
                                      :where [[~(entity "Switzerland") ~(wdt :contains-the-administrative-territorial-entity) ?canton]
                                              [?canton ~(wdt :official-website) ?website]]})]
    (->> cantons-and-websites
      (map (fn [entry]
             (assoc entry :asn (asn-lookup (:websiteLabel entry)))))
      doall)))

;; ### Question: Do we have cantons with multiple asn results? (why?)
(->> (group-by #(count (:results (:asn %))) cantonal-asn-lookup)
  keys)
;; we got some websites which have 6 result entries

;; The result count per website
^{::clerk/viewer clerk/table}
(->> cantonal-asn-lookup
  (map (fn [entry]
         {:cantonLabel (:cantonLabel entry)
          :result_count (-> entry :asn :result_count)}))
  (sort-by :result_count)
  (reverse))

;; let's have a look at the Basel-Landschaft entry
(->> cantonal-asn-lookup
  (filter #(= (:cantonLabel %) "Basel-Landschaft"))
  first
  :asn
  :results
  (map #(select-keys % [:net_name :org_name])))

;; so Basel-Landschaft routes everything via Cloudflare

;; Let's have a look at all the `:net_name` of the other cantons
^{::clerk/viewer clerk/table}
(->> cantonal-asn-lookup
  (map (fn [entry]
         {:cantonLabel (:cantonLabel entry)
          :net_names (->> entry :asn :results
                       (map #(:net_name %))
                       (clojure.string/join ", "))}))
  (sort-by :net_names))

;; ## What about the countries the websites are hosted in (ignore the ones hosted in Switzerland)?
^{::clerk/viewer clerk/table}
(->> cantonal-asn-lookup
  (map (fn [entry]
         {:cantonLabel (:cantonLabel entry)
          :hosted-in-countries (->> entry :asn :results
                                 (map #(:country (:geolocation %)))
                                 (clojure.string/join ", "))}))
  (remove #(clojure.string/includes? (:hosted-in-countries %) "Switzerland"))
  (sort-by :hosted-in-countries))

;; Fribourg and Solothurn stands out, why?
(->> cantonal-asn-lookup
  (filter #(= (:cantonLabel %) "Fribourg"))
  first
  :asn
  :results)

;; So they're hosting their Drupal site in AWS in the Frankfurt region

(->> cantonal-asn-lookup
  (filter #(= (:cantonLabel %) "Solothurn"))
  first
  :asn
  :results)

;; they're hosting their websites in the US, but why? their abuse contact is aio.so.ch which leads to https://so.ch/verwaltung/finanzdepartement/amt-fuer-informatik-und-organisation/
;; Maybe their IP address has been incorrectly mapped to a geolocation and it's actually hosted in Switzerland


;; ## What about the regions the websites are apparently hosted in?
^{::clerk/viewer clerk/table}
(->> cantonal-asn-lookup
  (map (fn [entry]
         {:cantonLabel (:cantonLabel entry)
          :hosted-in-regions (->> entry :asn :results
                               (map #(:region (:geolocation %)))
                               (clojure.string/join ", "))}))
  (sort-by :hosted-in-regions))

;; ## Questions
;; - [ ] can I get the website URL of all the councils (Gemeinden) of Canton Schwyz? - YES
;; - [ ] figure out what kind of CMS those websites are running and then plot them on a map - is there a big area using eg. Wordpress or another system?
;; - [ ] figure out where those websites are being hosted? local hosting vs in the cloud outside of switzerland?
;; - [ ] figure out which councils are using what kind of Analytics tools (Google Analytics, Piwik, etc.)
;; - [ ] figure out what kind of SSL provider the councils are using. Eg. Galgenen uses Let's encrypt
;; - [ ] how many websites are still using a `www.` subdomain?
;; - [ ] website load time using a headless browser
