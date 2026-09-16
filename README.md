# Wellbuying

공동구매 커머스 플랫폼. 프로그래머스 백엔드 데브코스 NBE10-12기 최종 프로젝트(6팀: 개발야호)로 시작한 레포지토리다.

- 백엔드: [`wellbuying/`](wellbuying)
- 프론트엔드: [`wellbuying-front/`](wellbuying-front)

## 기술 스택

### Backend (`wellbuying/`)

- Java 21, Spring Boot 4.0.7
- Spring Data JPA + QueryDSL, PostgreSQL, Flyway
- Spring Security + OAuth2 Client(카카오/구글 소셜 로그인), JWT(jjwt)
- Redis (Refresh Token 저장/로테이션), Resilience4j (Redis 장애 시 서킷 브레이커 폴백)
- Kafka, OpenSearch(상품 검색), AWS S3(이미지 업로드)
- Toss Payments 연동(빌링키 결제)
- Spring REST Docs + Asciidoctor, JUnit5 + Testcontainers
- Actuator + Micrometer(Prometheus)

### Frontend (`wellbuying-front/`)

- Next.js 16.3.5 (App Router), React 19.2.8, TypeScript
- Tailwind CSS 4, Zustand
- pnpm

### Infra / CI-CD

- Backend: AWS EC2(t3.large, x86_64) + RDS(db.t4g.small), GitHub Actions로 배포(`.github/workflows/deploy-back.yml`)
- Frontend: Vercel (GitHub 연동 자동 배포)
- 로컬 개발 인프라(Postgres/Redis/Kafka/OpenSearch/MinIO)는 `wellbuying/docker-compose.yml`로 구성

## 주요 기능

**인증/인가**
- 이메일 인증 기반 회원가입, JWT 로그인 / 토큰 재발급(RTR) / 로그아웃(전체 기기 포함)
- 카카오·구글 소셜 로그인 및 계정 연동/해제
- 로그인 기기 목록 조회, 휴면 계정 재활성화, 비밀번호 재설정
- Role 기반 인가(`@PreAuthorize`)

**회원**
- 내 정보 조회/수정, 회원 탈퇴, 프로필 이미지 업로드(S3 presigned URL)

**판매자(셀러)**
- 셀러 신청/재신청, 다이렉트 셀러 가입, 신청 상태 조회

**상품**
- 상품 등록/수정/삭제, 썸네일·갤러리·상세설명 이미지 업로드, 카테고리 관리
- 상품 검색(OpenSearch, 자동완성 포함), 인기 상품 조회

**공동구매(GroupBuy)**
- 공동구매 등록/수정/삭제, 참여 신청/취소, 실시간 가격·진행 상태 조회
- 판매 중지 요청

**주문 / 결제**
- 주문 내역 조회, Toss Payments 빌링키 등록/결제/결제 재시도

**정산**
- 판매자 정산 내역, 월별 요약, 추이, 참여자 조회

**알림**
- SSE 기반 실시간 알림, 읽음 처리

**배송지**
- 배송지 등록/조회/삭제, 기본 배송지 설정

**관리자**
- 셀러/상품/공동구매 승인·거절, 판매 정지 요청 처리
- 회원/정산 조회, 카테고리 관리, 각종 액션 로그 조회, 상품 검색 인덱스 재구축(reconcile)

## API 명세

- 로컬 서버 기동 후 Swagger UI: `http://localhost:8080/swagger-ui/index.html` (`springdoc-openapi`)
- Spring REST Docs 기반 정적 API 문서: `./gradlew asciidoctor` 실행 후 `wellbuying/build/docs/asciidoc`에 생성

## 시작하기

### Backend

```bash
cd wellbuying
cp .env.example .env.local   # 값 채우기
docker compose up -d         # Postgres/Redis/Kafka/OpenSearch/MinIO
./gradlew bootRun
```

- Flyway 마이그레이션은 `src/main/resources/db/migration`에 위치하며, 이미 커밋된 `V*.sql`은 수정하지 않고 새 버전 파일을 추가하는 방식을 따른다.

### Frontend

```bash
cd wellbuying-front
pnpm install
pnpm dev
```

`http://localhost:3000`에서 확인할 수 있다.
