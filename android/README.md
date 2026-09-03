# 📱 학교 생활 위젯 — Android Studio 없이 설치하기

이 폴더(`android/`)는 실제로 빌드 가능한 완전한 Android(Gradle) 프로젝트입니다.
`.github/workflows/android-apk.yml` 워크플로우가 이 폴더에 변경사항이 푸시될 때마다
GitHub Actions 서버에서 자동으로 APK를 빌드하고, 저장소의 **Releases** 탭에
`apk-latest`라는 이름으로 최신 APK를 올려줍니다.

## 태블릿에 설치하는 방법 (Android Studio 불필요)

1. 이 저장소의 GitHub 페이지에서 **Releases** 탭으로 이동합니다.
2. `학교 생활 위젯 APK (자동 빌드 최신본)` 릴리스에서 `app-debug.apk` 파일을 태블릿 브라우저로 직접 다운로드합니다.
3. 다운로드한 apk 파일을 탭하여 설치합니다. "출처를 알 수 없는 앱" 설치를 막는 팝업이 뜨면
   [설정] 허용 후 다시 설치를 진행합니다.
4. 설치가 끝나면 태블릿 홈 화면의 빈 곳을 2초간 길게 눌러 **[위젯]** ➔ **[학교 생활 위젯]**을
   선택해 원하는 위치/크기로 배치합니다.

Android Studio를 열거나, 파일을 복사-붙여넣기 하거나, USB로 PC와 연결할 필요가 전혀 없습니다.

## 다른 사람(다른 학교)도 이 위젯을 쓸 수 있나요?

네. **같은 APK를 모두가 그대로 설치**하고, 자기 학교/시간표/D-Day는 웹 스튜디오에서 설정한 뒤
**[이 기기에 적용하기]** 버튼으로 전달합니다 — 저장소에 커밋/푸시할 필요가 전혀 없습니다.

1. 위젯을 쓸 태블릿(또는 스마트폰)에 위 "Releases에서 APK 받기" 방법으로 앱을 설치합니다 (최초 1회, 모두가 같은 파일 사용).
2. 같은 기기의 브라우저로 웹 스튜디오를 열고, [위젯 설정] 탭에서 학교/시간표/D-Day를 자유롭게 설정합니다.
3. [📱 안드로이드 (태블릿) 위젯] 탭의 **[이 기기에 적용하기]** 버튼을 누르면 이미 설치된 앱이 열리며
   설정이 바로 반영됩니다 (`schoolwidget://import?...` 딥링크, [`MainActivity.kt`](app/src/main/java/com/school/widget/MainActivity.kt)에서 처리).
4. 다른 기기(예: PC에서 설정하고 태블릿에 적용)라면 같은 버튼 옆의 **[다른 기기로 전송 (QR코드)]**로
   QR코드를 띄우고, 위젯 앱이 설치된 기기의 카메라로 스캔하면 됩니다.

이 방식은 각 기기의 앱 내부 저장소에만 설정이 저장되므로, 기기마다 서로 다른 학교/시간표를 쓸 수 있고
클라우드 동기화나 서버 없이도 동작합니다. (반대로, 기기를 초기화하거나 앱을 지우면 다시 적용해야 합니다.)

## 할 일 목록을 폰으로 편하게 수정하고 싶을 때 (실시간 편집)

전자칠판처럼 자체 키보드로 타이핑하기 불편한 기기를 위한 기능입니다. 앱의 설정 화면에서
**[실시간 편집 시작]**을 누르면 QR코드가 뜨고, 학생/교사가 자기 폰 카메라로 스캔하면
폰 브라우저에서 할 일 목록만 간단히 편집하는 페이지([`mobile-edit.html`](../public/mobile-edit.html))가
열립니다. 폰에서 체크/추가/삭제할 때마다 몇 초 안에 자동으로 이 기기의 위젯에 반영됩니다
([`FirestoreSessionClient.kt`](app/src/main/java/com/school/widget/FirestoreSessionClient.kt)가
Google Firestore의 REST API로 두 기기를 중계합니다 - Firebase SDK나 `google-services.json` 없이,
이미 있는 OkHttp로 GET/PATCH 요청만 보내는 방식이라 앱이 무거워지지 않습니다).

이 기능을 쓰려면 Firestore 프로젝트가 하나 필요합니다 (무료):
1. https://console.firebase.google.com 에서 프로젝트 생성 → **빌드 → Firestore Database** →
   **테스트 모드**로 데이터베이스 생성.
2. **규칙(Rules)** 탭에서 아래 내용으로 교체하고 게시 (이 컬렉션만 열어두고 나머지는 기본값 유지):
   ```
   rules_version = '2';
   service cloud.firestore {
     match /databases/{database}/documents {
       match /widgetSessions/{sessionId} {
         allow read, write: if true;
       }
     }
   }
   ```
3. 프로젝트 설정에서 **프로젝트 ID**를 확인해, [`FirestoreSessionClient.kt`](app/src/main/java/com/school/widget/FirestoreSessionClient.kt)의
   `PROJECT_ID`와 [`public/mobile-edit.html`](../public/mobile-edit.html)의 `PROJECT_ID`
   두 곳을 같은 값으로 맞춥니다.

세션 코드는 무작위 6자리라 추측하긴 어렵지만 그 자체가 접근 제어는 아니므로, 규칙은 반드시
`widgetSessions` 컬렉션에만 열어두세요. 편집이 끝나면 [편집 종료]를 눌러 세션 문서를 정리합니다.

## 위젯을 수정하고 싶을 때 (공통 흐름)

무엇을 바꾸든 흐름은 항상 동일합니다.

1. `android/app/src/main/java/com/school/widget/` 안의 관련 파일을 수정합니다.
2. `main` 브랜치에 커밋 & 푸시합니다.
3. GitHub Actions가 자동으로 새 APK를 빌드해 `apk-latest` Release의 `app-debug.apk`를 덮어씁니다 (보통 2~4분).
4. 태블릿에서 Releases 페이지를 새로고침하고 `app-debug.apk`를 다시 다운로드해 설치합니다.
   서명 키(`app/debug.keystore`)가 고정돼 있어 **기존 앱을 지우지 않고 그대로 덮어 설치**되며,
   위젯을 다시 추가할 필요 없이 홈 화면에 이미 놓인 위젯도 자동으로 새 버전을 반영합니다.

무엇을 어디서 고치는지는 아래를 참고하세요:

| 바꾸고 싶은 것 | 수정할 파일 |
| --- | --- |
| **학교 / 시간표 / D-Day** (내 기기, 코딩·git 불필요) | 웹 스튜디오의 [📱 안드로이드 (태블릿) 위젯] 탭 ➔ **[이 기기에 적용하기]** (다른 기기는 QR코드) |
| **시간표 / D-Day의 기본값**(새로 설치하는 모든 사람에게 적용될 초기값) | 웹 스튜디오에서 `widget_config.json`을 다운로드해 [`app/src/main/assets/widget_config.json`](app/src/main/assets/widget_config.json)에 덮어쓰기 후 푸시 |
| 위젯 디자인/레이아웃/색상/문구 | [`SchoolWidget.kt`](app/src/main/java/com/school/widget/SchoolWidget.kt) |
| 급식 자동 전환 시각(13:30), 알레르기 표기 등 급식 로직 | [`NeisMealService.kt`](app/src/main/java/com/school/widget/NeisMealService.kt) |
| 자동 갱신 주기(현재 15분) | [`MainActivity.kt`](app/src/main/java/com/school/widget/MainActivity.kt), [`BootReceiver.kt`](app/src/main/java/com/school/widget/BootReceiver.kt) (두 곳의 `PeriodicWorkRequestBuilder` 시간을 동일하게 맞춰야 합니다) |
| 설정 화면(앱을 열었을 때 나오는 화면) | [`MainActivity.kt`](app/src/main/java/com/school/widget/MainActivity.kt) |
| 앱 아이콘 | `app/src/main/res/drawable/ic_launcher_*.xml`, `app/src/main/res/mipmap-anydpi-v26/` |
| 위젯 최소/최대 크기, 리사이즈 방식 | `app/src/main/res/xml/school_widget_info.xml` |

수정한 뒤 CI가 실패하면 저장소의 **Actions** 탭에서 실패한 실행을 열어 어떤 줄에서
오류가 났는지 확인하면 됩니다 (Kotlin 컴파일 오류는 파일명:줄번호 형태로 표시됩니다).

## 학교 정보(급식 조회용 코드) 바꾸는 방법

기본값은 동덕여자고등학교(`B10` / `7010152`)로 설정되어 있습니다. 다른 학교로 바꾸려면:

1. [`app/src/main/java/com/school/widget/NeisMealService.kt`](app/src/main/java/com/school/widget/NeisMealService.kt)
   상단의 `DEFAULT_ATPT_OFCDC_SC_CODE`(교육청 코드), `DEFAULT_SD_SCHUL_CODE`(학교 코드),
   `DEFAULT_SCHOOL_NAME`(학교 이름) 값을 원하는 학교 정보로 수정합니다.
   (스튜디오의 [위젯 설정] 탭에서 학교를 검색하면 정확한 코드를 확인할 수 있습니다.)
2. 이 저장소의 `main` 브랜치에 커밋을 푸시합니다.
3. GitHub Actions가 자동으로 새 APK를 빌드해 Releases에 올려줍니다. 태블릿에서 새 apk를
   다시 다운로드해 설치(덮어쓰기)하면 됩니다.

## 수동으로 빌드를 다시 실행하고 싶다면

저장소의 **Actions** 탭 ➔ `Android Widget APK` 워크플로우 ➔ **Run workflow** 버튼을 누르면
코드를 바꾸지 않아도 즉시 다시 빌드할 수 있습니다.

## 로컬에서 Android Studio로 열어보고 싶다면

이 폴더는 표준 Gradle 프로젝트라 Android Studio에서 `android/` 폴더를 **Open**으로 열면
그대로 인식되고, Gradle Wrapper(`gradlew`)가 포함되어 있어 별도 설치 없이 바로 빌드/실행할 수
있습니다. (선택 사항일 뿐, 필수는 아닙니다.)

## `app/debug.keystore`는 왜 저장소에 커밋되어 있나요?

일반적으로 서명 키는 저장소에 커밋하지 않지만, 이 키는 Android 표준 **디버그 전용** 키로
비밀번호(`android`)와 별칭(`androiddebugkey`)이 모두 공개된 값입니다. Play 스토어 배포용이
아니라 오직 "같은 위젯 앱을 업데이트 설치할 때 서명이 계속 일치하게" 만드는 용도이므로
저장소에 포함해도 안전합니다.
