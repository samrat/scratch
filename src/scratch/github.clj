(ns scratch.github
  (:require [clj-http.client :as client]
            [clojure.data.json :as json])
  (:gen-class))

(defn github-query-url
  [query-map]
  (str "https://api.github.com/search/users?"
       (client/generate-query-string query-map)))

(defn poll-rate-limit
  "Polls /rate_limit endpoint in 5s intervals to find out if the rate-limit has been reset."
  [gh-user gh-token]
  (loop []
    (let [resp (client/get "https://api.github.com/rate_limit"
                           {:basic-auth [gh-user gh-token]})
          remaining (get-in (json/read-str (:body resp)
                                           :key-fn keyword)
                     [:resources :search :remaining])]
      (if (> remaining 0)
        remaining
        (do (Thread/sleep 5000)
            (recur))))))

(defn get-all-sg-users
  "Gets all Github users who've specified 'Singapore' as their location.

  The Github API paginates results and returns a link to the URL for
  the next 'page'. This function traverses this 'linked list'.

  Github rate-limits its API, even for authenticated requests, so this
  function also handles waiting for the rate-limit to subside by
  polling the /rate_limit endpoint."
  [gh-user gh-token]
  (loop [next-link (github-query-url {:q "location:singapore"})
         sg-usernames []
         remaining-queries 29           ; first query is "page 1"
         ]
    (if next-link
      (let [resp (client/get next-link
                             {:basic-auth [gh-user gh-token]})
            parsed (json/read-str (:body resp) :key-fn keyword)
            sg-usernames (concat sg-usernames
                                 (map :login (:items parsed)))
            next (get-in resp [:links :next :href])
            remaining-queries (if (= remaining-queries 0)
                                ;; rate-limited. Poll /rate_limit to
                                ;; find if the rate_limit has been
                                ;; reset.
                                (poll-rate-limit gh-user gh-token)
                                (dec remaining-queries))]
        (println "Processed " next-link)
        (recur next
               sg-usernames
               remaining-queries))
      sg-usernames)))

(defn -main
  [& args]
  (let [gh-user (System/getenv "GITHUB_USERNAME")
        gh-password (System/getenv "GITHUB_TOKEN")]
    (println (get-all-sg-users gh-user gh-password))))
