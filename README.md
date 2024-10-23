# Email Sender

이 프로그램은 Clojure로 작성된 이메일 전송 도구입니다. EDN 파일에서 이메일 데이터를 읽어 여러 수신자에게 이메일을 보낼 수 있습니다.

## 환경 설정

1. Java 8 이상이 설치되어 있어야 합니다.
2. Clojure CLI 도구가 설치되어 있어야 합니다.
3. 프로젝트 루트 디렉토리에 `.env` 파일을 생성하고 SMTP 설정을 추가합니다:

   ```
   SMTP_SERVER=smtp.example.com
   SMTP_PORT=587
   SMTP_USERNAME=your-username
   SMTP_PASSWORD=your-password
   ```

4. Gmail을 사용하는 경우, 다음 설정이 필요합니다:
   - Gmail 계정에서 "덜 안전한 앱의 액세스"를 허용하거나
   - 2단계 인증을 설정하고 앱 비밀번호를 생성하여 사용

## 프로그램 실행 방법

1. 프로젝트 루트 디렉토리에서 다음 명령어를 실행합니다:

   ```
   ./send-email.sh -f <이메일_데이터_파일_경로> [옵션]
   ```

   또는

   ```
   clj -M -m send.core -f <이메일_데이터_파일_경로> [옵션]
   ```

2. 사용 가능한 옵션:
   - `-f, --file FILE`: 이메일 데이터 파일 경로 (필수)
   - `-v, --verbose`: 상세 모드 활성화
   - `-h, --help`: 도움말 표시
   - `--smtp-server SERVER`: SMTP 서버 지정
   - `--smtp-port PORT`: SMTP 포트 지정
   - `--smtp-username USERNAME`: SMTP 사용자 이름 지정
   - `--smtp-password PASSWORD`: SMTP 비밀번호 지정

## 이메일 데이터 파일 구조

이메일 데이터 파일은 EDN 형식으로 작성됩니다. 파일 구조는 다음과 같습니다:

```clojure
clojure
{:to ["recipient1@example.com" "recipient2@example.com"]
:subject "이메일 제목"
:body "이메일 본문 내용"
:mimetype "text/plain"
:signature "signature.png" ; 선택적
:smtp_server "smtp.example.com" ; 선택적
:smtp_port "587" ; 선택적
:smtp_username "your-username" ; 선택적
:smtp_password "your-password" ; 선택적
:var_name1 "변수1 값" ; 선택적
:var_name2 "변수2 값"} ; 선택적
```
    
### 주요 필드 설명

- `:to`: 수신자 이메일 주소 (문자열 또는 문자열 배열)
- `:subject`: 이메일 제목
- `:body`: 이메일 본문
- `:mimetype`: 이메일 본문의 MIME 타입 ("text/plain" 또는 "text/html")
- `:signature`: 서명 이미지 파일 경로 (선택적)
- `:smtp_*`: SMTP 설정 (선택적, 환경 변수나 CLI 옵션보다 우선순위 낮음)
- `:var_*`: 본문에서 사용할 변수들 (선택적)

### 변수 사용

본문에서 `{{ :var_name }}` 형식으로 변수를 사용할 수 있습니다. 예:

```
안녕하세요, {{ :to }} 님!

{{ :var_serviceName }} 서비스에 가입해 주셔서 감사합니다.
```

### 서명 이미지

`:signature` 필드가 있는 경우, 이메일은 자동으로 HTML 형식으로 변환되며 서명 이미지가 본문 끝에 추가됩니다.

## 실행 예시

1. 기본 실행:
   ```
   ./send-email.sh -f emails/welcome.edn
   ```

2. 상세 모드로 실행:
   ```
   ./send-email.sh -f emails/newsletter.edn -v
   ```

3. SMTP 설정을 직접 지정하여 실행:
   ```
   ./send-email.sh -f emails/alert.edn --smtp-server smtp.gmail.com --smtp-port 587 --smtp-username your-email@gmail.com --smtp-password your-app-password
   ```

## 주의사항

- 민감한 정보(비밀번호 등)는 가능한 환경 변수나 `.env` 파일을 통해 제공하세요.
- 대량의 이메일을 보낼 때는 SMTP 서버의 제한 사항을 확인하세요.
- 테스트 실행 시 `clj -X:test` 명령어를 사용하세요.
