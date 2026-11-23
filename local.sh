#!/usr/bin/env bash

# ==============================================================================
# Black-Coat Local Development Orchestrator
# Runs PostgreSQL (pgvector), Spring Boot 4 Backend, and Angular Frontend
# ==============================================================================

set -e

# Terminal Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
GOLD='\033[0;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Working Directories & Log Files
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_DIR="$ROOT_DIR/logs"
mkdir -p "$LOG_DIR"

API_LOG="$LOG_DIR/api.log"
WEB_LOG="$LOG_DIR/web.log"

API_PID=""
WEB_PID=""

# Clean shutdown trap
cleanup() {
  echo -e "\n${GOLD}🛑 Shutting down Black-Coat local services...${NC}"

  if [ -n "$API_PID" ] && kill -0 "$API_PID" 2>/dev/null; then
    echo -e "  Shutting down Backend API (PID: $API_PID)..."
    kill "$API_PID" 2>/dev/null || true
  fi

  if [ -n "$WEB_PID" ] && kill -0 "$WEB_PID" 2>/dev/null; then
    echo -e "  Shutting down Frontend Web (PID: $WEB_PID)..."
    kill "$WEB_PID" 2>/dev/null || true
  fi

  # Release ports if still bound
  fuser -k 8080/tcp 2>/dev/null || true
  fuser -k 4200/tcp 2>/dev/null || true

  echo -e "${GREEN}✓ All local processes stopped cleanly.${NC}"
  echo -e "${CYAN}Note: PostgreSQL docker containers are still running. Run 'docker-compose down' to stop them.${NC}"
  exit 0
}

trap cleanup INT TERM EXIT

# ASCII Banner
clear
echo -e "${GOLD}"
cat << "EOF"
  ____  _            _        ____            _   
 | __ )| | __ _  ___| | __   / ___|___   __ _| |_ 
 |  _ \| |/ _` |/ __| |/ /  | |   / _ \ / _` | __|
 | |_) | | (_| | (__|   <   | |__| (_) | (_| | |_ 
 |____/|_|\__,_|\___|_|\_\   \____\___/ \__,_|\__|
   Indian Legal Intelligence • Local Dev Environment
EOF
echo -e "${NC}"

# 1. Dependency Checks
echo -e "${CYAN}🔍 Checking prerequisites...${NC}"

command -v docker >/dev/null 2>&1 || {
  echo -e "${RED}❌ Docker is required but not installed.${NC}"; exit 1;
}
command -v java >/dev/null 2>&1 || {
  echo -e "${RED}❌ Java (21+) is required but not installed.${NC}"; exit 1;
}
command -v mvn >/dev/null 2>&1 || {
  echo -e "${RED}❌ Maven is required but not installed.${NC}"; exit 1;
}
command -v node >/dev/null 2>&1 || {
  echo -e "${RED}❌ Node.js is required but not installed.${NC}"; exit 1;
}

echo -e "${GREEN}✓ Prerequisites verified (Docker, Java, Maven, Node.js).${NC}\n"

# 2. Start PostgreSQL with pgvector & Ollama via Docker Compose
echo -e "${CYAN}🐘 1/3 Starting Database (PostgreSQL 16 + pgvector) & Ollama...${NC}"
docker compose up -d postgres ollama 2>/dev/null || docker-compose up -d postgres ollama

echo -n "   Waiting for PostgreSQL to be healthy"
for i in {1..30}; do
  if docker compose exec -T postgres pg_isready -U blackcoat -d blackcoat >/dev/null 2>&1 || \
     docker-compose exec -T postgres pg_isready -U blackcoat -d blackcoat >/dev/null 2>&1; then
    echo -e " ${GREEN}✓ Healthy!${NC}"
    break
  fi
  echo -n "."
  sleep 1
  if [ $i -eq 30 ]; then
    echo -e "\n${RED}❌ Timed out waiting for PostgreSQL to start.${NC}"
    exit 1
  fi
done

# 3. Start Spring Boot API
echo -e "\n${CYAN}☕ 2/3 Starting Spring Boot API (Port 8080)...${NC}"
echo -e "   Logging output to: ${PURPLE}$API_LOG${NC}"

# Kill any stale process on port 8080
fuser -k 8080/tcp 2>/dev/null || true

cd "$ROOT_DIR/black-coat-api"
mvn spring-boot:run > "$API_LOG" 2>&1 &
API_PID=$!
cd "$ROOT_DIR"

echo -n "   Waiting for API to initialize (/actuator/health)"
for i in {1..45}; do
  if curl -s http://localhost:8080/actuator/health | grep -q "UP"; then
    echo -e " ${GREEN}✓ Online!${NC}"
    break
  fi
  echo -n "."
  sleep 2
  if [ $i -eq 45 ]; then
    echo -e "\n${RED}❌ Backend API failed to start within 90 seconds. Check logs:${NC}"
    tail -n 25 "$API_LOG"
    exit 1
  fi
done

# 4. Start Frontend
echo -e "\n${CYAN}🅰️ 3/3 Starting Angular Web Application (Port 4200)...${NC}"
echo -e "   Logging output to: ${PURPLE}$WEB_LOG${NC}"

# Kill any stale process on port 4200
fuser -k 4200/tcp 2>/dev/null || true

cd "$ROOT_DIR/black-coat-web"

if [ ! -d "node_modules" ]; then
  echo -e "   ${GOLD}Installing npm dependencies for Angular frontend (this may take a minute)...${NC}"
  npm install > "$LOG_DIR/npm-install.log" 2>&1 || true
fi

# Launch Angular dev server if dependencies exist, otherwise fallback to high-speed static server
if [ -d "node_modules/@angular" ]; then
  npm start > "$WEB_LOG" 2>&1 &
  WEB_PID=$!
else
  echo -e "   ${GOLD}Starting static development server on port 4200...${NC}"
  python3 -m http.server 4200 > "$WEB_LOG" 2>&1 &
  WEB_PID=$!
fi

cd "$ROOT_DIR"

echo -n "   Waiting for frontend to become responsive"
for i in {1..30}; do
  if curl -s http://localhost:4200 >/dev/null 2>&1; then
    echo -e " ${GREEN}✓ Online!${NC}"
    break
  fi
  echo -n "."
  sleep 1
done

# 5. Developer Summary Dashboard
echo -e "\n${GREEN}================================================================${NC}"
echo -e "${GOLD}           ⚖️  BLACK-COAT IS LIVE LOCALLY!                      ${NC}"
echo -e "${GREEN}================================================================${NC}"
echo -e "  ${CYAN}Frontend UI:${NC}         ${GREEN}http://localhost:4200${NC}"
echo -e "  ${CYAN}Backend API:${NC}         ${GREEN}http://localhost:8080${NC}"
echo -e "  ${CYAN}Actuator Health:${NC}     ${GREEN}http://localhost:8080/actuator/health${NC}"
echo -e "  ${CYAN}SSE Stream Endpoint:${NC} ${GREEN}http://localhost:8080/api/v1/chat/stream${NC}"
echo -e "  ${CYAN}Postgres (pgvector):${NC} ${GREEN}localhost:5432 (db: blackcoat, user: blackcoat)${NC}"
echo -e "  ${CYAN}Backend Logs:${NC}        ${PURPLE}tail -f logs/api.log${NC}"
echo -e "  ${CYAN}Frontend Logs:${NC}       ${PURPLE}tail -f logs/web.log${NC}"
echo -e "${GREEN}================================================================${NC}"
echo -e "${GOLD}Press Ctrl+C anytime to stop all services.${NC}\n"

# Keep the script running to hold the trap
wait
