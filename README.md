# 가전 구매 타이밍 알리미

가전 모델을 검색하면 **지금 사도 되는 가격인지** 알려주고, 원하는 가격이 되면 **알림**을 주는 안드로이드 앱입니다.
매장에서 가격표 앞에 섰을 때 휴대폰으로 바로 확인하는 상황을 기준으로 만들었습니다.

- 플랫폼: Android 8.0 (API 26) 이상
- 언어: Java · Android Studio
- 서버: 직접 개발한 가전 데이터 API 서버 (FastAPI · Render)와 연동

## 주요 기능

| 기능 | 설명 |
| --- | --- |
| 트렌드 TOP10 | 지금 관심이 높은 가전 순위와 AI가 쓴 트렌드 이유 |
| 제품 검색 | 모델명 · 품목 검색, 카테고리 칩, 정렬, 무한 스크롤 |
| 제품 리포트 | AI 구매 분석(점수 · 장단점 · 주의점 · 추천 대상), 실사용 후기, 관련 뉴스 |
| 구매 타이밍 | 최근 30일 최저가 그래프, 구매 판단(최고 · 좋음 · 보통 · 대기), 품목 평균 비교, 검색 관심도 |
| 제품 비교 | 두 제품의 최저가 · 판매처 · 리뷰 비교표, AI 장단점 비교 (로그인) |
| AI 추천 | 품목 · 예산 · 사용 환경을 고르면 AI가 제품 3개를 이유와 함께 추천 |
| 가격 알림 | 목표 가격 등록 · 목록 · 삭제, 하루 1회 자동 확인 후 휴대폰 알림 (로그인) |
| 최근 본 제품 | 본 제품을 기기에 저장해 홈과 마이페이지에 표시 |
| 회원 | 이메일 인증 회원가입, 로그인 유지(7일), 마이페이지 |

## 화면 구성

```
시작 → 홈 ─┬─ 제품 목록 → 제품 리포트 ─┬─ 구매 타이밍 → (가격 알림 등록)
           │                              └─ 제품 비교 → 제품 목록(고르기 모드)
           ├─ AI 추천 → 제품 리포트
           ├─ 가격 알림
           └─ 마이페이지 ─┬─ 로그인 → 회원가입
                          └─ 가격 알림
```

| 화면 | 액티비티 |
| --- | --- |
| 시작 | `SplashActivity` |
| 홈 | `MainActivity` |
| 제품 목록 | `ProductListActivity` |
| 제품 리포트 | `ProductDetailActivity` |
| 구매 타이밍 | `TimingActivity` |
| 제품 비교 | `CompareActivity` |
| AI 추천 | `RecommendActivity` |
| 가격 알림 | `AlertListActivity` |
| 마이페이지 | `MyPageActivity` |
| 로그인 | `LoginActivity` |
| 회원가입 | `SignUpActivity` |

## 프로젝트 구조

```
app/src/main/java/com/example/gajeontiming/
├── *Activity.java        화면
├── GajeonApp.java        앱 시작 시 초기화 (로그인 저장소, 알림 채널, 하루 1회 확인 예약)
├── adapter/              RecyclerView 어댑터 (트렌드, 제품, 최근 본 제품, 추천, 알림)
├── alert/                가격 알림 확인 작업(WorkManager)과 알림 표시
├── auth/                 로그인 토큰 저장 (SharedPreferences)
├── data/                 최근 본 제품 저장 (SQLite)
├── model/                서버 응답 · 요청 데이터
└── network/              Retrofit 서버 통신, 토큰 자동 첨부, 오류 문구 처리
```

## 기술 스택

| 구분 | 사용 |
| --- | --- |
| 화면 | XML 레이아웃, RecyclerView, Material Components (카드 · 칩 · 툴바) |
| 서버 통신 | Retrofit2 + Gson, OkHttp (대기 90초, 토큰 자동 첨부) |
| 이미지 | Glide |
| 그래프 | MPAndroidChart |
| 기기 저장 | SharedPreferences (로그인), SQLite (최근 본 제품) |
| 백그라운드 · 알림 | WorkManager, NotificationCompat |

## 실행 방법

1. 이 저장소를 Android Studio에서 엽니다. (File > New > Project from Version Control)
2. Gradle Sync가 끝날 때까지 기다립니다.
3. 에뮬레이터 또는 실제 기기(Android 8.0 이상)에서 실행합니다.

- 서버 주소는 `network/ApiClient.java`의 `BASE_URL`에 있습니다.
- 서버가 무료 플랜이라 한동안 요청이 없으면 잠듭니다. 첫 실행 때 시작 화면에서 최대 1분 정도 기다릴 수 있습니다.
- 안드로이드 13 이상에서는 가격 알림을 받으려면 알림 권한을 허용해야 합니다.

## 사용하는 서버 API

| API | 용도 | 로그인 |
| --- | --- | --- |
| `GET /` | 서버 연결 확인 | |
| `GET /api/trend` | 트렌드 TOP10 | |
| `GET /api/naver/products` | 제품 검색 | |
| `GET /api/ai-analysis` | AI 구매 분석 + 실사용 후기 | |
| `GET /api/report` | 관련 뉴스 | |
| `GET /api/timing` | 30일 가격 이력 + 구매 판단 | |
| `GET /api/price-position` | 품목 평균 대비 위치 | |
| `GET /api/best-price` | 지금 최저가 | |
| `GET /api/recommend` | AI 추천 | |
| `GET /api/ai-compare` | AI 장단점 비교 | 필요 |
| `POST /api/auth/*` | 인증번호 · 회원가입 · 로그인 | |
| `GET /api/user/me` | 내 정보 | 필요 |
| `GET · POST · DELETE /api/user/alerts` | 가격 알림 관리 | 필요 |

## 만든 사람

| 이름 | 학번 |
| --- | --- |
| 박민규 | 202208107 |
