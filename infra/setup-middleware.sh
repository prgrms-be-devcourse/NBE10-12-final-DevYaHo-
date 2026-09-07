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
# 접근 제한은 EC2 보안그룹이 아니라 Tailscale(사설망)로 처리 — 위 3개 포트 + 8080(백엔드)은
# 보안그룹에 공개 인바운드를 열지 않고, OCI가 같은 tailnet에 조인해 Tailscale IP로만 접근.
# (Tailscale 설치/조인은 이 스크립트 범위 밖 — 별도 진행)
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

# ── redis_exporter (Prometheus용) ────────────────────────
# redis(6379)는 raw 프로토콜 포트라 Prometheus가 직접 못 읽어서 별도 익스포터 필요
# 9121 포트는 OCI Prometheus가 Tailscale로 스크레이핑 (§ 11 참고, 인터넷에는 미공개)
docker stop redis-exporter || true
docker rm redis-exporter || true
docker pull oliver006/redis_exporter:latest
docker run -d \
  --name redis-exporter \
  --network wellbuying-net \
  --restart unless-stopped \
  --memory 64m \
  -e TZ=Asia/Seoul \
  -p 9121:9121 \
  --log-opt max-size=10m \
  --log-opt max-file=3 \
  oliver006/redis_exporter:latest \
  --redis.addr=redis:6379

# ── kafka-exporter (Prometheus용) ────────────────────────
# kafka(9092)는 raw 프로토콜 포트라 Prometheus가 직접 못 읽어서 별도 익스포터 필요
# 9308 포트는 OCI Prometheus가 Tailscale로 스크레이핑 (§ 11 참고, 인터넷에는 미공개)
docker stop kafka-exporter || true
docker rm kafka-exporter || true
docker pull danielqsj/kafka-exporter:latest
docker run -d \
  --name kafka-exporter \
  --network wellbuying-net \
  --restart unless-stopped \
  --memory 64m \
  -e TZ=Asia/Seoul \
  -p 9308:9308 \
  --log-opt max-size=10m \
  --log-opt max-file=3 \
  danielqsj/kafka-exporter:latest \
  --kafka.server=kafka:9092

# ── node_exporter (Prometheus용, EC2 호스트 자체 지표) ────
# CPU/메모리/디스크 등 EC2 호스트 레벨 지표 — 호스트의 /proc, /sys를 읽어야 해서 host 네트워크로 실행
# 9100 포트는 OCI Prometheus가 Tailscale로 스크레이핑 (§ 11 참고, 인터넷에는 미공개)
docker stop node-exporter || true
docker rm node-exporter || true
docker pull prom/node-exporter:latest
docker run -d \
  --name node-exporter \
  --restart unless-stopped \
  --memory 64m \
  --pid host \
  --net host \
  -v /proc:/host/proc:ro \
  -v /sys:/host/sys:ro \
  -v /:/rootfs:ro \
  --log-opt max-size=10m \
  --log-opt max-file=3 \
  prom/node-exporter:latest \
  --path.procfs=/host/proc \
  --path.sysfs=/host/sys \
  --path.rootfs=/rootfs \
  --web.listen-address=:9100
