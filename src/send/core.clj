(ns send.core
  (:require [clojure.tools.cli :refer [parse-opts]]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [postal.core :as postal]
            [clojure.string :as str])
  (:import [java.util UUID]
           [java.nio.charset StandardCharsets]))

(defn load-env
  "환경 변수를 .env 파일에서 로드합니다."
  []
  (into {} (for [line (str/split-lines (slurp ".env"))
                 :let [[k v] (str/split line #"=")]]
             [(keyword k) v])))

(defn get-smtp-config
  "SMTP 설정을 우선순위에 따라 가져옵니다. 
   CLI 옵션 > 이메일 데이터 > 환경 변수 > 기본값 순으로 적용됩니다."
  [cli-options email-data env]
  {:smtp-server (or (:smtp-server cli-options)
                    (:smtp_server email-data)
                    (:SMTP_SERVER env)
                    "smtp.gmail.com")
   :smtp-port (Integer/parseInt (or (:smtp-port cli-options)
                                    (:smtp_port email-data)
                                    (:SMTP_PORT env)
                                    "587"))
   :smtp-username (or (:smtp-username cli-options)
                      (:smtp_username email-data)
                      (:SMTP_USERNAME env)
                      "your-email@gmail.com")
   :smtp-password (or (:smtp-password cli-options)
                      (:smtp_password email-data)
                      (:SMTP_PASSWORD env)
                      "your-app-password")})

;; CLI 옵션 설정
(def cli-options
  [["-f" "--file FILE" "파일 경로"
    :validate [#(.exists (java.io.File. %)) "존재하지 않는 파일입니다."]]
   ["-v" "--verbose" "상세 모드"
    :default false]
   ["-h" "--help" "도움말 표시"]
   [nil "--smtp-server SERVER" "SMTP 서버"]
   [nil "--smtp-port PORT" "SMTP 포트"
    :parse-fn #(Integer/parseInt %)]
   [nil "--smtp-username USERNAME" "SMTP 사용자 이름"]
   [nil "--smtp-password PASSWORD" "SMTP 비밀번호"]])

(defn usage
  "프로그램 사용법을 반환합니다."
  [options-summary]
  (str "사용법: program [options]\n\n"
       "옵션:\n" options-summary))

(defn read-email-data
  "주어진 파일 경로에서 이메일 데이터를 읽어옵니다."
  [file-path]
  (with-open [reader (io/reader file-path)]
    (edn/read-string (slurp reader))))

(defn extract-name-from-email
  "이메일 주소에서 이름을 추출합니다."
  [email]
  (if-let [name (second (re-find #"(.+)<.+>" email))]
    (str/trim name)
    email))

(defn replace-variables
  "텍스트 내의 변수를 주어진 값으로 치환합니다."
  [text variables]
  (reduce-kv
   (fn [t k v]
     (str/replace t (str "{{ :" (name k) " }}") v))
   text
   variables))

(defn read-image-file
  "이미지 파일을 바이트 배열로 읽어옵니다."
  [file-path]
  (let [file (io/file file-path)]
    (when (.exists file)
      (with-open [in (io/input-stream file)]
        (let [buf (byte-array (.length file))]
          (.read in buf)
          buf)))))

(defn text-to-html
  "일반 텍스트를 HTML로 변환합니다."
  [text]
  (-> text
      (str/replace #"&" "&amp;")
      (str/replace #"<" "&lt;")
      (str/replace #">" "&gt;")
      (str/replace #"\n" "<br>")))

(defn prepare-email-with-signature
  "서명이 포함된 이메일을 준비합니다."
  [email-data]
  (let [signature-path (:signature email-data)
        image-data (read-image-file signature-path)]
    (if image-data
      (let [content-id (str (UUID/randomUUID))
            html-body (str "<html><body>"
                           (text-to-html (:body email-data))
                           "<br><img src=\"cid:" content-id "\" alt=\"signature\">"
                           "</body></html>")]
        (-> email-data
            (assoc :body html-body)
            (assoc :mimetype "text/html")
            (assoc :signature {:content-id content-id
                               :content image-data
                               :content-type "image/png"})))
      email-data)))

(defn prepare-email-content
  "이메일 내용을 준비하고 변수를 치환합니다."
  [email-data]
  (let [to (extract-name-from-email (:to email-data))
        variables (assoc email-data :to to)
        email-with-vars (-> email-data
                            (update :subject #(replace-variables % variables))
                            (update :body #(replace-variables % variables)))]
    (if (:signature email-with-vars)
      (prepare-email-with-signature email-with-vars)
      email-with-vars)))

(defn send-email
  "준비된 이메일을 전송합니다."
  [{:keys [to subject from body mimetype signature] :as email-data} smtp-config]
  (let [prepared-email (prepare-email-content email-data)
        email-body (if (:signature prepared-email)
                     [{:type "text/html; charset=utf-8"
                       :content (:body prepared-email)}
                      (when-let [sig (:signature prepared-email)]
                        {:type :inline
                         :content (:content sig)
                         :content-type (:content-type sig)
                         :content-id (:content-id sig)})]
                     [{:type (str (:mimetype prepared-email) "; charset=utf-8")
                       :content (:body prepared-email)}])
        email {:from (or from (:smtp-username smtp-config))
               :to to
               :subject (:subject prepared-email)
               :body (filter some? email-body)}
        result (postal/send-message {:host (:smtp-server smtp-config)
                                     :port (:smtp-port smtp-config)
                                     :user (:smtp-username smtp-config)
                                     :pass (:smtp-password smtp-config)
                                     :tls true}
                                    email)]
    (if (= :SUCCESS (:error result))
      (do
        (println "이메일 전송 성공:" to)
        nil)
      (do
        (println "이메일 전송 실패:" to "- 오류:" (:error result))
        {:to to :error (:error result)}))))

(defn process-email-data
  "이메일 데이터를 처리하고 전송합니다."
  [email-data verbose smtp-config]
  (let [to-list (if (string? (:to email-data))
                  [(:to email-data)]
                  (:to email-data))
        failed-emails (atom [])]
    (doseq [to to-list]
      (when verbose
        (println "전송 중:" to))
      (when-let [failed-email (send-email (assoc email-data :to to) smtp-config)]
        (swap! failed-emails conj failed-email)))
    @failed-emails))

(defn -main
  "프로그램의 메인 함수입니다."
  [& args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)]
    (cond
      (:help options) (do (println (usage summary))
                          (System/exit 0))
      errors (do (doseq [error errors]
                   (println error))
                 (System/exit 1))
      :else (let [file-path (:file options)
                  verbose (:verbose options)]
              (if file-path
                (let [email-data (read-email-data file-path)
                      env (load-env)
                      smtp-config (get-smtp-config options email-data env)
                      failed-emails (process-email-data email-data verbose smtp-config)]
                  (when (seq failed-emails)
                    (println "\n전송 실패한 이메일 목록:")
                    (doseq [failed-email failed-emails]
                      (println (str "수신자: " (:to failed-email) ", 오류: " (:error failed-email))))))
                (println "파일 경로를 지정해주세요. (-f 옵션 사용)"))))))
