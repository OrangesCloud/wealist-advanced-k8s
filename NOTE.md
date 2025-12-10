# 빠른시작

# docker-compose 환경

./docker/scripts/dev.sh up => .env.dev 파일 주의하세요!

# local-kind 환경(ns: wealist-dev)

make kind-setup (cluster 생성 + nginx controller 셋팅)

make infra-setup (인프라 이미지 로드 + 배포 + 대기)
-> kubectl get pods -n wealist-dev 로 확인하세요

make k8s-deploy-services (빌드 + ns 생성 + 배포)
-> kubectl get pods -n wealist-dev 로 확인하세요
make status

## 그 외

kind get clusters (클러스터 확인)
kubectl get namespaces (ns 확인)
