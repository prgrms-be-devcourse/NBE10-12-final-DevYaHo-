#!/bin/bash
# EC2에서 redis/kafka/opensearch를 최초 1회 띄우는 스크립트.
#
# 실행 방법 (EC2에서 이 repo를 clone/pull 받은 디렉토리 루트에서):
#   bash infra/setup-middleware.sh
#
# 최초 1회만 실행하면 됨 — 전부 --restart unless-stopped로 띄우므로
# EC2가 스케줄로 stop/start 되어도 컨테이너가 알아서 다시 뜸.
# 설정값(mem_limit, 옵션 등)을 바꿨을 때만 다시 실행.
#
# redis(6379)/kafka(9092)/opensearch(9200) 포트를 호스트에 노출하는 이유:
# 별도 OCI 인스턴스의 Prometheus(monitor.wellbuying.xyz)가 모니터링 대상으로 접근하기 위함.
# 실제 접근 제한은 이 스크립트가 아니라 EC2 보안그룹에서 처리 —
# 위 3개 포트 + 8080(백엔드)은 반드시 OCI 인스턴스 IP만 인바운드 허용하도록 설정할 것.
set -euo pipefail

docker network create wellbuying-net || true

# ── Redis ──────────────────────────────────────────────
# 컨테이너 이름 "redis" — 백엔드 .env의 REDIS_HOST=redis가 이 이름으로 접속
docker stop redis || true
docker rm redis || true
docker pull redis:8-alpine
docker run -d \
  --name redis \
  --network wellbuying-net \
  --restart unless-stopped \
  --memory 256m \
  -e TZ=Asia/Seoul \
  -p 6379:6379 \
  -v wellbuying-redis-data:/data \
  --log-opt max-size=10m \
  --log-opt max-file=3 \
  redis:8-alpine \
  redis-server --maxmemory 200mb --maxmemory-policy allkeys-lru

# ── Kafka (KRaft 단일 노드) ──────────────────────────────
# 컨테이너 이름 "kafka" — 백엔드 .env의 KAFKA_BOOTSTRAP_SERVERS=kafka:9092가 이 이름으로 접속
docker stop kafka || true
docker rm kafka || true
docker pull apache/kafka:3.7.0
docker run -d \
  --name kafka \
  --network wellbuying-net \
  --restart unless-stopped \
  --memory 1300m \
  -e TZ=Asia/Seoul \
  -e KAFKA_HEAP_OPTS="-Xms768m -Xmx768m" \
  -e KAFKA_NODE_ID=1 \
  -e KAFKA_PROCESS_ROLES=broker,controller \
  -e KAFKA_LISTENERS=PLAINTEXT://0.0.0.0:9092,CONTROLLER://0.0.0.0:9093 \
  -e KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://kafka:9092 \
  -e KAFKA_CONTROLLER_LISTENER_NAMES=CONTROLLER \
  -e KAFKA_LISTENER_SECURITY_PROTOCOL_MAP=CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT \
  -e KAFKA_CONTROLLER_QUORUM_VOTERS=1@kafka:9093 \
  -e KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1 \
  -e CLUSTER_ID=MkU3OEVBNTcwNTJENDM2Qk \
  -p 9092:9092 \
  -v wellbuying-kafka-data:/var/lib/kafka/data \
  -v wellbuying-kafka-logs:/opt/kafka/logs \
  --log-opt max-size=10m \
  --log-opt max-file=3 \
  apache/kafka:3.7.0

# ── OpenSearch (nori 플러그인 커스텀 이미지) ─────────────
# 컨테이너 이름 "opensearch" — 백엔드 .env의 OPENSEARCH_HOST=opensearch가 이 이름으로 접속
# wellbuying/docker/opensearch/Dockerfile을 그대로 로컬 빌드 (레지스트리 안 씀)
docker build -t wellbuying-opensearch:local ./wellbuying/docker/opensearch
docker stop opensearch || true
docker rm opensearch || true
docker run -d \
  --name opensearch \
  --network wellbuying-net \
  --restart unless-stopped \
  --memory 2g \
  -e TZ=Asia/Seoul \
  -e discovery.type=single-node \
  -e DISABLE_SECURITY_PLUGIN=true \
  -e OPENSEARCH_JAVA_OPTS="-Xms1g -Xmx1g" \
  -p 9200:9200 \
  -v wellbuying-opensearch-data:/usr/share/opensearch/data \
  -v wellbuying-opensearch-logs:/usr/share/opensearch/logs \
  --log-opt max-size=10m \
  --log-opt max-file=3 \
  wellbuying-opensearch:local
