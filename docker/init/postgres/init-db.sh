#!/bin/bash
set -e

# PostgreSQL 초기화 스크립트
# 세 개의 독립된 데이터베이스와 사용자를 생성합니다

echo "🚀 weAlist 데이터베이스 초기화 시작..."

# User Service Database
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" <<-EOSQL
    CREATE DATABASE ${USER_DB_NAME};
    CREATE USER ${USER_DB_USER} WITH PASSWORD '${USER_DB_PASSWORD}';
    GRANT ALL PRIVILEGES ON DATABASE ${USER_DB_NAME} TO ${USER_DB_USER};
    \c ${USER_DB_NAME}
    GRANT ALL ON SCHEMA public TO ${USER_DB_USER};
EOSQL

echo "✅ User 서비스 데이터베이스 생성 완료: ${USER_DB_NAME}"

# Board Service Database
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" <<-EOSQL
    CREATE DATABASE ${BOARD_DB_NAME};
    CREATE USER ${BOARD_DB_USER} WITH PASSWORD '${BOARD_DB_PASSWORD}';
    GRANT ALL PRIVILEGES ON DATABASE ${BOARD_DB_NAME} TO ${BOARD_DB_USER};
    \c ${BOARD_DB_NAME}
    GRANT ALL ON SCHEMA public TO ${BOARD_DB_USER};
EOSQL

echo "✅ Board 서비스 데이터베이스 생성 완료: ${BOARD_DB_NAME}"

# Chat Service Database (🔥 추가)
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" <<-EOSQL
    CREATE DATABASE ${CHAT_DB_NAME};
    CREATE USER ${CHAT_DB_USER} WITH PASSWORD '${CHAT_DB_PASSWORD}';
    GRANT ALL PRIVILEGES ON DATABASE ${CHAT_DB_NAME} TO ${CHAT_DB_USER};
    \c ${CHAT_DB_NAME}
    GRANT ALL ON SCHEMA public TO ${CHAT_DB_USER};
EOSQL

echo "✅ Chat 서비스 데이터베이스 생성 완료: ${CHAT_DB_NAME}"

echo "🎉 모든 데이터베이스 초기화 완료!"
echo "📋 생성된 데이터베이스:"
echo "   - ${USER_DB_NAME} (User Service)"
echo "   - ${BOARD_DB_NAME} (Board Service)"
echo "   - ${CHAT_DB_NAME} (Chat Service)"