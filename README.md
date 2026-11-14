# 살살 드라이브 (SalsalDrive)

뒷자리에 탄 아이·임산부·어르신·반려동물이 느끼는 흔들림을 차량 속도 데이터로 측정해, 지붕 위 물컵의 "수위"로 보여주는 동승자 배려 운전 코치 앱입니다.

## 실행 방법

1. Android Studio(최신 버전)에서 이 폴더를 엽니다. 처음 열 때 필요한 도구를 자동으로 내려받습니다.
2. Pleos Connect 에뮬레이터를 실행합니다. (설치: Pleos Playground 개발 문서 → 개발 환경 구성)
3. 상단의 실행(▶) 버튼으로 앱을 설치하고 엽니다.
4. 출발 화면에서 동승자를 고른 뒤 기어를 D로 바꾸면 측정이 시작되고, P로 바꾸면 도착 리포트가 나옵니다.
   - 에뮬레이터 Virtual Console에서 속도를 바꿀 수 없을 때는 **"시연 모드로 시작"** 을 누르세요. 가상 주행과 함께 "급제동 재현", "급가속 재현" 버튼이 나옵니다.

## 테스트 환경 및 필요한 설정

| 항목 | 값 |
|---|---|
| 에뮬레이터 | Pleos Connect Emulator v2.1.0 (Android 14, API 34 system image) |
| AVD 설정 | Automotive / Pleos Connect v2.1.0, Cold boot, RAM 4GB |
| 화면 규격 | 에뮬레이터 기본 가로 화면 (해상도에 맞춰 자동 확대·축소) |
| minSdk / targetSdk | 28 / 34 |
| 언어·빌드 | Kotlin, Gradle 8.7, Android Gradle Plugin 8.5.2 |
| 퍼미션 | `android.car.permission.CAR_SPEED`, `android.car.permission.CAR_POWERTRAIN` |

별도 서버, 로그인, 외부 API 키가 필요 없습니다. 주행 기록은 기기 안에만 저장합니다.

## 사용하는 차량 데이터

| 데이터 | 경로 | 용도 |
|---|---|---|
| 차량 속도 `PERF_VEHICLE_SPEED` | AAOS 차량 속성 (Pleos 개발 문서에 사용 가능으로 안내) | 가감속을 계산해 흔들림 판정 |
| 기어 `GEAR_SELECTION` | AAOS 차량 속성 (사용 가능으로 안내) | D에서 측정 시작, P에서 리포트 |
| 음성 안내 | Android TextToSpeech (Pleos TextToSpeech SDK로 교체 예정) | 한마디 코칭 |

Pleos Vehicle SDK의 Brake 도메인(브레이크 페달 상태)은 다음 단계에서 붙입니다. 아래 "Pleos SDK 연결" 참고.

## APK 만들기

1. Android Studio 메뉴 **Build → Generate App Bundles or APKs → Generate APKs**
2. `app/build/outputs/apk/release/app-release.apk` 가 생깁니다. (release 빌드도 디버그 키로 서명하도록 설정해 두었습니다)
3. 파일을 `apk/` 폴더로 옮기고 이름을 `팀명_SalsalDrive_release.apk` 로 바꿉니다.
4. 용량이 GitHub 업로드 한도를 넘으면 GitHub Releases에 올리고 그 주소를 아래에 적습니다.

APK 위치: `apk/팀명_SalsalDrive_release.apk` (또는 Release 링크: ________)

## 코드 구조 (PT 기술 질의응답용)

```
app/src/main/java/com/salsaldrive/app/
├─ DriveEngine.kt    측정 엔진. 속도 → 가감속 → 흔들림 판정 → 수위 계산 (안드로이드에 의존하지 않는 순수 코드)
├─ ReportText.kt     도착 리포트 문장(수위 비교, 한 줄 후기, 운전 팁, 코칭 문구)
├─ VehicleSource.kt  차량 데이터 입구. 실제 차량(CarPropertySource)과 시연 모드(DemoSource)
├─ SceneView.kt      주행 화면 그리기. 도로·차·물컵·물방울 애니메이션과 상단 표시
└─ MainActivity.kt   화면 3개(출발 → 주행 → 도착)와 음성 코칭, 가족별 보정 저장
```

데이터 흐름: 차량 속도 값 → `VehicleSource` → `MainActivity.onSpeed` → `DriveEngine.onSpeed` → 결과(수위, 물방울, 흔들림)를 `SceneView`에 전달 → 화면 갱신.

### 판정 기준 (DriveEngine.kt)

- 가감속은 연속한 두 속도 값의 차이를 시간으로 나눠 구하고, 잡음을 줄이려고 지수 평활(계수 0.4)을 씁니다.
- 공통 기준: 주의 1.5 m/s², 흔들림 2.0 m/s², 강한 흔들림 3.0 m/s². 2.0과 3.0은 선행 연구에 소개된 한국교통안전공단 운행기록 기준(급감속 초당 7.5km/h, 급가속 초당 11km/h)을 m/s²로 바꾼 값과 비슷합니다. 1.5는 승차감 연구의 불편 기준을 참고했습니다.
- 수위 = 100 − 흔들림 감점 − 멀미 누적 지수 × 0.05 × 동승자 가중치
  - 흔들림 감점: 흔들림 4, 강한 흔들림 8, 여기에 동승자 가중치를 곱함
  - 멀미 누적 지수: 가속도 제곱을 시간에 따라 더한 값 (ISO 2631-1의 멀미 누적량 개념을 참고한 단순화)
- 동승자 가중치: 아이는 멀미 누적 2.5배, 임산부·어르신은 급제동·급출발 1.5배, 반려동물은 모든 흔들림 1.25배. **설계 초기값이며 실제 사용 데이터로 조정할 값입니다.**
- 가족별 보정: 리포트에서 "힘들어했어요"를 누르면 다음 주행의 기준이 8% 엄격해지고, "편안했어요"는 3% 느슨해집니다(0.85~1.3배 범위).

### 테스트

`app/src/test/.../DriveEngineTest.kt` 에 측정 엔진 단위 테스트가 있습니다. Android Studio에서 파일을 열고 실행하거나, 터미널에서 `gradlew test` 로 실행합니다.

## Pleos SDK 연결 (다음 단계)

1. Pleos Playground 개발 문서 Getting Started에 안내된 Maven 저장소 주소를 `settings.gradle.kts` 에 넣습니다.
2. `app/build.gradle.kts` 의 `implementation("ai.pleos.playground:Vehicle:2.3.2")` 주석을 풉니다.
3. Brake 도메인의 지원 여부 확인(`checkXXXCapability`) → 현재 값 조회 → 콜백 등록 순서로 브레이크 페달 상태를 받아, `DriveEngine` 에서 감속이 제동에 의한 것인지 구분하는 데 씁니다. 정확한 함수 이름과 퍼미션은 Brake API Reference를 따릅니다.
4. 음성 안내를 Pleos TextToSpeech SDK(On-device 모드)로 바꿉니다.

## 알려진 한계

- 앞뒤 방향 흔들림만 측정합니다. 좌우 쏠림(커브)은 아직 반영하지 않습니다.
- 속도 값의 갱신 주기는 에뮬레이터에서 확인이 필요합니다.
- 이 저장소의 첫 버전은 개발 환경에서 직접 빌드해 보기 전에 작성되었습니다. 빌드 오류가 나면 오류 메시지를 기준으로 수정합니다.
