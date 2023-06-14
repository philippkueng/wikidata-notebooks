;; this is a hack that we currently need for clerk to play nice with specter
^{:nextjournal.clerk/error-on-missing-vars :off}
{}

(ns exploring-wikidata
  (:require [mundaneum.query :refer [search entity entity-data clojurize-claims describe label query *default-language*]]
            [mundaneum.properties :refer [wdt]]
            [nextjournal.clerk :as clerk]))

;; I'll be starting off with the notebook and the mundaneum library by @jackrusher over here https://github.com/jackrusher/mundaneum/blob/master/notebooks/basics.clj

(comment
  (clerk/serve! {:watch-paths ["notebooks"] :show-filter-fn #(clojure.string/starts-with? % "notebooks")})
  (clerk/show! "notebooks/exploring_wikidata.clj")

  )


;; let me get the node for the TV Series "Death in Paradise"
(comment
  (entity "Death in Paradise")

  ;; and describe it
  (describe (entity "Death in Paradise"))

  ;; get it's data
  (entity-data (entity "Death in Paradise"))

  (println (wdt :title))

  (let [attr (wdt :title)]
    (query `{:select *
             :where [[~(entity "Death in Paradise") ~attr ?o]]}))

  ;; get the title attribute of the entity
  (query `{:select *
           :where [[~(entity "Death in Paradise") ~(wdt :title) ?o]]})

  ;; get the genre attribute of the entity
  (query `{:select *
           :where [[~(entity "Death in Paradise") ~(wdt :genre) ?o]]})

  (def genre
    (->> (query `{:select *
                  :where [[~(entity "Death in Paradise") ~(wdt :genre) ?o]]})
      first
      :o
      entity-data))

  (def genre-claims
    (clojurize-claims genre))

  (->> (entity "Death in Paradise")
    entity-data
    clojurize-claims)

  ;; getting the characters of the series and then getting the data for the first character
  (->> (entity "Death in Paradise")
    entity-data
    clojurize-claims
    :characters
    first
    entity-data
    clojurize-claims))

^{::clerk/viewer clerk/table}
(query `{:select [?awardLabel]
         :where [[:wd/Q103569 ~(wdt :award-received) ?award]]
         :limit 10})

^{::clerk/viewer clerk/table}
(query `{:select [?personLabel]
         :where [[?person ~(wdt :writing-language) ~(entity "German")]
                 [?person ~(wdt :occupation) ~(entity "philosopher")]]
         :limit 20})


;; # Show the characters of "Death in Paradise" in a table
^{::clerk/viewer clerk/table}
(query `{:select [?castLabel]
         :where [[~(entity "Death in Paradise") ~(wdt :cast-member) ?cast]]})


;;how many cast members do we have?
(->> (query `{:select [?castLabel]
              :where [[~(entity "Death in Paradise") ~(wdt :cast-member) ?cast]]})
  count)

;; of the 13 cast members linked in Wikidata, what is their gender?
(->> (query `{:select [?cast ?genderLabel]
              :where [[~(entity "Death in Paradise") ~(wdt :cast-member) ?cast]
                      [?cast ~(wdt :sex-or-gender) ?gender]]})
  (map :genderLabel)
  frequencies)




(comment

  (clojure.pprint/pprint
    (query `{:select [?cast]
             :where [[~(entity "Death in Paradise") ~(wdt :cast-member) ?cast]]}))

  (wdt :sex-or-gender)

  )


(comment

  (wdt :cast-member)
  (wdt :writing-language)

  )

;; future questions
;; - can I get a list of skyscrapers sorted by their size?
;; - can I find the Swiss canton with the most inhabitants?

