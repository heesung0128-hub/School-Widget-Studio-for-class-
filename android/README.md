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
