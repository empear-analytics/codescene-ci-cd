(ns codescene-ci-cd.github-validation
  (:require
    [taoensso.timbre :as log])
  (:import org.apache.commons.codec.binary.Hex
           javax.crypto.Mac;
           javax.crypto.spec.SecretKeySpec
           java.nio.charset.StandardCharsets))

(def ^:const ^:private signing-algorithm "HmacSHA1")

;; Using memoize to optimize object creation
(defn- get-signing-key* [secret]
  (SecretKeySpec. (.getBytes secret (StandardCharsets/UTF_8))
                  signing-algorithm))
(def ^:private get-signing-key (memoize get-signing-key*))

(defn- get-mac* [signing-key]
  (doto (Mac/getInstance signing-algorithm)
    (.init signing-key)))
(def ^:private get-mac (memoize get-mac*))

(defn- hmac [^String s signature secret]
  (let [mac (get-mac (get-signing-key secret))]
    ;; MUST use .doFinal which resets mac so that it can be reused!
    (str "sha1="
         (Hex/encodeHexString
           (.doFinal mac (.getBytes s (StandardCharsets/UTF_8)))))))

(defn- validate-string [^String s signature secret]
  (let [calculated (hmac s signature secret)]
    (log/debug "Comparing received" signature "with calculated" calculated)
    (= signature calculated)))

(defn is-valid? [secret body request]
  (let [signature (get-in request [:headers "x-hub-signature"])]
    (validate-string body signature secret)))
