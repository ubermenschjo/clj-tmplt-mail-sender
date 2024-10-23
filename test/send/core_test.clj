(ns send.core-test
  (:require [clojure.test :refer :all]
            [send.core :as core]
            [postal.core :as postal]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(deftest read-email-data-test
  (testing "이메일 데이터 읽기"
    (let [test-file (str (System/getProperty "java.io.tmpdir") 
                         "/test-email-" (System/currentTimeMillis) ".edn")
          test-data {:to ["test@example.com"]
                     :subject "테스트 제목"
                     :body "테스트 본문"
                     :mimetype "text/plain"}]
      (try
        (spit test-file (pr-str test-data))
        (is (= test-data (core/read-email-data test-file)))
        (finally
          (io/delete-file test-file true))))))

(deftest process-email-data-test
  (testing "이메일 데이터 처리"
    (let [test-data {:to ["test1@example.com" "test2@example.com"]
                     :subject "테스트 제목"
                     :body "테스트 본문"
                     :mimetype "text/plain"}
          sent-emails (atom [])]
      (with-redefs [postal/send-message (fn [_ email] 
                                          (swap! sent-emails conj email)
                                          {:error :SUCCESS})]
        (core/process-email-data test-data false {})
        (is (= 2 (count @sent-emails)))
        (is (= "test1@example.com" (:to (first @sent-emails))))
        (is (= "test2@example.com" (:to (second @sent-emails))))))))

(deftest send-email-test
  (testing "이메일 전송"
    (let [test-email {:to "test@example.com"
                      :subject "테스트 제목"
                      :body "테스트 본문"
                      :mimetype "text/plain"}
          sent-email (atom nil)]
      (with-redefs [postal/send-message (fn [_ email] 
                                          (reset! sent-email email)
                                          {:error :SUCCESS})]
        (core/send-email test-email {})
        (is (= "test@example.com" (:to @sent-email)))
        (is (= "테스트 제목" (:subject @sent-email)))
        (is (= [{:type "text/plain; charset=utf-8" :content "테스트 본문"}] (:body @sent-email)))))))

(deftest send-email-with-newlines-test
  (testing "개행 문자가 있는 이메일 전송"
    (let [test-email {:to "test@example.com"
                      :subject "개행 문자 테스트"
                      :body "첫 번째 줄\n두 번째 줄\n세 번째 줄"
                      :mimetype "text/plain"}
          sent-email (atom nil)]
      (with-redefs [postal/send-message (fn [_ email] 
                                          (reset! sent-email email)
                                          {:error :SUCCESS})]
        (core/send-email test-email {})
        (is (= "test@example.com" (:to @sent-email)))
        (is (= "개행 문자 테스트" (:subject @sent-email)))
        (is (= [{:type "text/plain; charset=utf-8"
                 :content "첫 번째 줄\n두 번째 줄\n세 번째 줄"}]
               (:body @sent-email)))))))

(deftest send-html-email-test
  (testing "HTML 타입 이메일 전송"
    (let [test-email {:to "test@example.com"
                      :subject "HTML 이메일 테스트"
                      :body "<html><body><h1>테스트 제목</h1><p>테스트 본문</p></body></html>"
                      :mimetype "text/html"}
          sent-email (atom nil)]
      (with-redefs [postal/send-message (fn [_ email] 
                                          (reset! sent-email email)
                                          {:error :SUCCESS})]
        (core/send-email test-email {})
        (is (= "test@example.com" (:to @sent-email)))
        (is (= "HTML 이메일 테스트" (:subject @sent-email)))
        (is (= [{:type "text/html; charset=utf-8"
                 :content "<html><body><h1>테스트 제목</h1><p>테스트 본문</p></body></html>"}]
               (:body @sent-email)))))))

(deftest prepare-email-content-test
  (testing "이메일 내용 준비"
    (let [email-data {:to "테스트 유저<test@example.com>"
                      :var_serviceName "My Service"
                      :subject "테스트 이메일"
                      :body "안녕하세요! {{ :to }} 님! 우리 {{ :var_serviceName }} 을 사용해주셔서 감사합니다"
                      :mimetype "text/plain"}
          prepared-email (core/prepare-email-content email-data)]
      (is (= "안녕하세요! 테스트 유저 님! 우리 My Service 을 사용해주셔서 감사합니다"
             (:body prepared-email))))))

(deftest extract-name-from-email-test
  (testing "이메일에서 이름 추출"
    (is (= "테스트 유저" (core/extract-name-from-email "테스트 유저<test@example.com>")))
    (is (= "test@example.com" (core/extract-name-from-email "test@example.com")))))

(deftest replace-variables-test
  (testing "변수 치환"
    (let [text "안녕하세요, {{ :name }}님! 당신의 나이는 {{ :age }}세 입니다."
          variables {:name "홍길동" :age "30"}
          result (core/replace-variables text variables)]
      (is (= "안녕하세요, 홍길동님! 당신의 나이는 30세 입니다." result))))

  (testing "존재하지 않는 변수 처리"
    (let [text "안녕하세요, {{ :name }}님! {{ :non_existent }}."
          variables {:name "홍길동"}
          result (core/replace-variables text variables)]
      (is (= "안녕하세요, 홍길동님! {{ :non_existent }}." result))))

  (testing "중첩된 변수 처리"
    (let [text "{{ :greeting }}, {{ :name }}님! {{ :message }}"
          variables {:greeting "안녕하세요" :name "홍길동" :message "오늘도 좋은 하루 되세요!"}
          result (core/replace-variables text variables)]
      (is (= "안녕하세요, 홍길동님! 오늘도 좋은 하루 되세요!" result)))))

(deftest prepare-email-content-with-variables-test
  (testing "변수가 포함된 이메일 내용 준비"
    (let [email-data {:to "홍길동<hong@example.com>"
                      :subject "안녕하세요, {{ :to }}님"
                      :body "{{ :to }}님, 우리 {{ :service_name }} 서비스에 가입해 주셔서 감사합니다.\n현재 귀하의 등급은 {{ :user_level }} 입니다."
                      :mimetype "text/plain"
                      :service_name "Super Service"
                      :user_level "VIP"}
          prepared-email (core/prepare-email-content email-data)]
      (is (= "안녕하세요, 홍길동님" (:subject prepared-email)))
      (is (= "홍길동님, 우리 Super Service 서비스에 가입해 주셔서 감사합니다.\n현재 귀하의 등급은 VIP 입니다." (:body prepared-email))))))

(deftest prepare-email-with-signature-test
  (testing "서명이 포함된 이메일 준비"
    (let [temp-dir (System/getProperty "java.io.tmpdir")
          signature-path (str temp-dir "/test-signature.png")
          _ (spit signature-path "fake image data")
          email-data {:to "test@example.com"
                      :subject "서명 테스트"
                      :body "이것은 서명이 포함된 테스트 이메일입니다."
                      :mimetype "text/plain"
                      :signature signature-path}
          prepared-email (core/prepare-email-content email-data)]
      (try
        (is (= "text/html" (:mimetype prepared-email)))
        (is (.contains (:body prepared-email) "<img src=\"cid:"))
        (is (:signature prepared-email))
        (is (:content-id (:signature prepared-email)))
        (is (:content (:signature prepared-email)))
        (is (= "image/png" (:content-type (:signature prepared-email))))
        (finally
          (io/delete-file signature-path true))))))

(deftest send-email-with-signature-test
  (testing "서명이 포함된 이메일 전송"
    (let [temp-dir (System/getProperty "java.io.tmpdir")
          signature-path (str temp-dir "/test-signature.png")
          _ (spit signature-path "fake image data")
          test-email {:to "test@example.com"
                      :subject "서명 테스트"
                      :body "이것은 서명이 포함된 테스트 이메일입니다."
                      :mimetype "text/plain"
                      :signature signature-path}
          sent-email (atom nil)]
      (try
        (with-redefs [postal/send-message (fn [_ email] 
                                            (reset! sent-email email)
                                            {:error :SUCCESS})]
          (core/send-email test-email {})
          (is (= "test@example.com" (:to @sent-email)))
          (is (= "서명 테스트" (:subject @sent-email)))
          (is (= 2 (count (:body @sent-email))))
          (is (= "text/html; charset=utf-8" (:type (first (:body @sent-email)))))
          (is (.contains (:content (first (:body @sent-email))) "이것은 서명이 포함된 테스트 이메일입니다."))
          (is (.contains (:content (first (:body @sent-email))) "<img src=\"cid:"))
          (is (= :inline (:type (second (:body @sent-email)))))
          (is (:content-id (second (:body @sent-email)))))
        (finally
          (io/delete-file signature-path true))))))

(deftest text-to-html-test
  (testing "텍스트를 HTML로 변환"
    (let [input-text "안녕하세요.\n이것은 테스트입니다.\n<태그> & 특수문자"
          expected-html "안녕하세요.<br>이것은 테스트입니다.<br>&lt;태그&gt; &amp; 특수문자"]
      (is (= expected-html (core/text-to-html input-text))))))

(deftest prepare-email-with-signature-and-newlines-test
  (testing "개행 문자가 있는 서명 포함 이메일 준비"
    (let [temp-dir (System/getProperty "java.io.tmpdir")
          signature-path (str temp-dir "/test-signature.png")
          _ (spit signature-path "fake image data")
          email-data {:to "test@example.com"
                      :subject "개행 문자와 서명 테스트"
                      :body "첫 번째 줄\n두 번째 줄\n세 번째 줄"
                      :mimetype "text/plain"
                      :signature signature-path}
          prepared-email (core/prepare-email-content email-data)]
      (try
        (is (= "text/html" (:mimetype prepared-email)))
        (is (.contains (:body prepared-email) "<html><body>"))
        (is (.contains (:body prepared-email) "첫 번째 줄<br>두 번째 줄<br>세 번째 줄"))
        (is (.contains (:body prepared-email) "<img src=\"cid:"))
        (is (.contains (:body prepared-email) "</body></html>"))
        (is (:signature prepared-email))
        (is (:content-id (:signature prepared-email)))
        (is (:content (:signature prepared-email)))
        (is (= "image/png" (:content-type (:signature prepared-email))))
        (finally
          (io/delete-file signature-path true))))))

(deftest get-smtp-config-test
  (testing "SMTP 설정 우선순위"
    (let [cli-options {:smtp-server "cli-server.com"
                       :smtp-port "1000"
                       :smtp-username "cli-user"
                       :smtp-password "cli-pass"}
          email-data {:smtp_server "email-server.com"
                      :smtp_port "2000"
                      :smtp_username "email-user"
                      :smtp_password "email-pass"}
          env {:SMTP_SERVER "env-server.com"
               :SMTP_PORT "3000"
               :SMTP_USERNAME "env-user"
               :SMTP_PASSWORD "env-pass"}]
      
      (testing "CLI 옵션 우선"
        (let [config (core/get-smtp-config cli-options email-data env)]
          (is (= "cli-server.com" (:smtp-server config)))
          (is (= 1000 (:smtp-port config)))
          (is (= "cli-user" (:smtp-username config)))
          (is (= "cli-pass" (:smtp-password config)))))
      
      (testing "이메일 데이터 다음 우선"
        (let [config (core/get-smtp-config {} email-data env)]
          (is (= "email-server.com" (:smtp-server config)))
          (is (= 2000 (:smtp-port config)))
          (is (= "email-user" (:smtp-username config)))
          (is (= "email-pass" (:smtp-password config)))))
      
      (testing "환경 변수 마지막 우선"
        (let [config (core/get-smtp-config {} {} env)]
          (is (= "env-server.com" (:smtp-server config)))
          (is (= 3000 (:smtp-port config)))
          (is (= "env-user" (:smtp-username config)))
          (is (= "env-pass" (:smtp-password config)))))
      
      (testing "기본값 사용"
        (let [config (core/get-smtp-config {} {} {})]
          (is (= "smtp.gmail.com" (:smtp-server config)))
          (is (= 587 (:smtp-port config)))
          (is (= "your-email@gmail.com" (:smtp-username config)))
          (is (= "your-app-password" (:smtp-password config))))))))

(deftest load-env-test
  (testing "환경 변수 로딩"
    (let [temp-env-file (str (System/getProperty "java.io.tmpdir") "/.env")]
      (spit temp-env-file "SMTP_SERVER=test-server.com\nSMTP_PORT=2525\nSMTP_USERNAME=test-user\nSMTP_PASSWORD=test-pass")
      (with-redefs [core/load-env (fn [] 
                                    (into {} (for [line (str/split-lines (slurp temp-env-file))
                                                   :let [[k v] (str/split line #"=")]]
                                               [(keyword k) v])))]
        (let [env (core/load-env)]
          (is (= "test-server.com" (:SMTP_SERVER env)))
          (is (= "2525" (:SMTP_PORT env)))
          (is (= "test-user" (:SMTP_USERNAME env)))
          (is (= "test-pass" (:SMTP_PASSWORD env)))))
      (io/delete-file temp-env-file))))

(deftest process-email-data-with-failures-test
  (testing "일부 이메일 전송 실패 처리"
    (let [test-data {:to ["success@example.com" "fail1@example.com" "fail2@example.com"]
                     :subject "테스트 제목"
                     :body "테스트 본문"
                     :mimetype "text/plain"}
          smtp-config {:smtp-server "test-server.com"
                       :smtp-port 587
                       :smtp-username "test-user"
                       :smtp-password "test-pass"}
          send-count (atom 0)]
      (with-redefs [postal/send-message (fn [_ email]
                                          (swap! send-count inc)
                                          (if (= (:to email) "success@example.com")
                                            {:error :SUCCESS}
                                            {:error :FAILED}))]
        (let [failed-emails (core/process-email-data test-data true smtp-config)]
          (is (= 3 @send-count) "모든 이메일에 대해 전송 시도가 이루어져야 합니다")
          (is (= 2 (count failed-emails)) "2개의 실패한 이메일이 있어야 합니다")
          (is (= #{"fail1@example.com" "fail2@example.com"}
                 (set (map :to failed-emails)))
              "실패한 이메일 주소가 올바르게 기록되어야 합니다")
          (is (every? #(= :FAILED (:error %)) failed-emails)
              "모든 실패한 이메일의 오류 상태가 :FAILED여야 합니다"))))))

(deftest main-function-with-failures-test
  (testing "-main 함수에서 실패한 이메일 처리"
    (let [test-file (str (System/getProperty "java.io.tmpdir") "/test-email-data.edn")
          test-data {:to ["success@example.com" "fail@example.com"]
                     :subject "테스트 제목"
                     :body "테스트 본문"
                     :mimetype "text/plain"}
          output (new java.io.StringWriter)]
      (spit test-file (pr-str test-data))
      (with-redefs [postal/send-message (fn [_ email]
                                          (if (= (:to email) "success@example.com")
                                            {:error :SUCCESS}
                                            {:error :FAILED}))
                    core/load-env (constantly {})]
        (binding [*out* output]
          (core/-main "-f" test-file "-v"))
        (let [output-str (str output)]
          (is (.contains output-str "전송 중: success@example.com"))
          (is (.contains output-str "전송 중: fail@example.com"))
          (is (.contains output-str "이메일 전송 성공: success@example.com"))
          (is (.contains output-str "이메일 전송 실패: fail@example.com"))
          (is (.contains output-str "전송 실패한 이메일 목록:"))
          (is (.contains output-str "수신자: fail@example.com, 오류: :FAILED"))))
      (io/delete-file test-file))))
