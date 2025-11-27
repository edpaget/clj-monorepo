(ns bashketball-editor-api.config-test
  (:require
   [bashketball-editor-api.config :as config]
   [clojure.test :refer [deftest is testing]]
   [malli.core :as m]))

(deftest config-schema-test
  (testing "Config schema validates correct configuration"
    (let [valid-config {:server {:port 3000 :host "localhost"}
                        :database {:database-url "jdbc:postgresql://localhost/test"}
                        :github {:oauth {:client-id "id"
                                         :client-secret "secret"
                                         :redirect-uri "http://localhost/callback"
                                         :success-redirect-uri "http://localhost/"}
                                 :repo {:owner "owner" :name "repo" :branch "main"}}
                        :session {:ttl-ms 86400000
                                  :cookie-name "session"
                                  :cookie-secret "16-byte-secret!!"
                                  :cookie-secure? false
                                  :cookie-http-only? true
                                  :cookie-same-site :lax}
                        :auth {:required-org nil
                               :validate-org? false
                               :cache-ttl-ms 300000}
                        :git {:repo-path "/tmp/repo"
                              :remote-url "https://github.com/test/repo"
                              :branch "main"
                              :writer? true}}]
      (is (m/validate config/Config valid-config))))

  (testing "Config schema rejects invalid port"
    (let [invalid-config {:server {:port -1 :host "localhost"}}]
      (is (not (m/validate config/Config invalid-config)))))

  (testing "Config schema rejects missing required fields"
    (let [incomplete-config {:server {:port 3000}}]
      (is (not (m/validate config/Config incomplete-config))))))

(deftest load-config-test
  (testing "load-config loads test profile"
    (let [config (config/load-config :test)]
      (is (map? config))
      (is (pos-int? (get-in config [:server :port])))
      (is (string? (get-in config [:database :database-url])))
      (is (string? (get-in config [:github :oauth :client-id]))))))
