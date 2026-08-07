# WinForms ↔ Java TCP/HTTP 통신 예제

`.NET Framework 4.8` WinForms 클라이언트에서 Spring 없이 작성한 Java 21 서버와 TCP 또는 HTTP로 통신하는 학습용 Echo 예제입니다.

## 구성

```text
WindowsFormsJavaCommunication.sln
winforms-client/                 WinForms 클라이언트(TCP/HTTP 탭)
java-servers/
  tcp-server/                   ServerSocket 기반 TCP 서버
  http-server/                  JDK HttpServer 기반 HTTP 서버
```

기본 주소는 다음과 같습니다.

| 방식 | 서버 주소 | 규약 |
|---|---|---|
| TCP | `127.0.0.1:5000` | UTF-8 한 줄 요청 → 한 줄 응답 |
| HTTP | `http://127.0.0.1:8080/echo` | `POST`, `text/plain; charset=utf-8` |

## 준비 사항

- Windows 10/11
- Visual Studio 2022와 **.NET Framework 4.8 Developer Pack**
- JDK 21
- Maven 3.9 이상

Java 서버는 운영체제와 관계없이 실행할 수 있지만 WinForms 클라이언트의 빌드와 실행은 Windows에서 진행해야 합니다.

## Java 서버 빌드 및 실행

먼저 두 서버를 빌드하고 테스트합니다.

```bash
cd java-servers
mvn clean test package
```

TCP 서버와 HTTP 서버는 각각 별도 터미널에서 실행합니다.

```bash
java -cp tcp-server/target/tcp-server-1.0.0.jar example.communication.tcp.TcpEchoServer
```

```bash
java -cp http-server/target/http-server-1.0.0.jar example.communication.http.HttpEchoServer
```

기본값 대신 외부 접속 허용 주소와 포트를 지정하려면 `바인딩주소 포트` 순서로 인자를 전달합니다.

```bash
java -cp tcp-server/target/tcp-server-1.0.0.jar example.communication.tcp.TcpEchoServer 0.0.0.0 5001
java -cp http-server/target/http-server-1.0.0.jar example.communication.http.HttpEchoServer 0.0.0.0 8081
```

`0.0.0.0`으로 실행하면 같은 네트워크의 다른 장치에서도 접근할 수 있으므로 OS 방화벽에서 해당 포트를 허용해야 합니다. 신뢰할 수 없는 네트워크에서는 기본 루프백 주소를 유지하세요.

## WinForms 클라이언트 실행

1. Windows의 Visual Studio에서 `WindowsFormsJavaCommunication.sln`을 엽니다.
2. `CommunicationClient`를 시작 프로젝트로 선택하고 실행합니다.
3. **TCP 통신** 탭에서 `연결`을 누른 뒤 메시지를 전송합니다. 한 연결에서 여러 메시지를 계속 보낼 수 있습니다.
4. **HTTP 통신** 탭에서는 URL과 메시지를 입력하고 `POST 전송`을 누릅니다.

두 탭 모두 전송한 문자열과 Java 서버가 돌려준 문자열을 시간과 함께 표시합니다. Enter 키로도 전송할 수 있으며 네트워크 연결 및 응답 제한 시간은 5초입니다.

## HTTP만 빠르게 확인하기

HTTP 서버 실행 후 `curl`로 확인할 수 있습니다.

```bash
curl -i -X POST http://127.0.0.1:8080/echo \
  -H 'Content-Type: text/plain; charset=utf-8' \
  --data '안녕하세요 Java'
```

존재하지 않는 경로는 `404`, `/echo`에 POST 이외의 메서드를 사용하면 `405 Allow: POST`를 반환합니다.

## 예제 범위

통신의 기본 구조를 보여주기 위해 인증, TLS, JSON, 파일 전송, 데이터베이스는 포함하지 않았습니다. 실제 서비스에서는 TLS, 메시지 크기 제한, 인증, 재시도 정책과 입력 검증을 추가해야 합니다.
