#!/bin/bash
#
# OneBlog 生产环境部署脚本（基于 docker-compose 编排 docs/docker 服务栈）
#
# 用法: ./deploy.sh [--build] <命令>
#   --build  使用 docker-compose.yml 从本地源码构建镜像（默认使用预构建镜像）
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DOCKER_DIR="${SCRIPT_DIR}/docs/docker"
ENV_FILE="${DOCKER_DIR}/.env"
ENV_EXAMPLE="${DOCKER_DIR}/.env.example"
# 默认使用预构建镜像的 compose 文件；--build 时切换为本地构建
COMPOSE_FILE="docker-compose.yml.template"

# 健康检查参数（端口可由 .env 覆盖）
ADMIN_PORT="8085"
WEB_PORT="8443"
HEALTH_RETRIES="${HEALTH_RETRIES:-40}"
HEALTH_INTERVAL="${HEALTH_INTERVAL:-3}"
APP_DIR="/var/.oneblog"

log() { echo "[deploy] $*"; }
err() { echo "[deploy][ERROR] $*" >&2; }

usage() {
  cat <<EOF
用法: ./deploy.sh [--build] <命令>

选项:
  --build          使用 docs/docker/docker-compose.yml 从本地源码构建镜像
                   （默认使用预构建镜像 docker-compose.yml.template）

命令:
  up               拉起完整服务栈（redis/mysql/blog-admin/blog-web）并做健康门禁
  down             停止并移除服务栈
  restart          重启服务栈（down 后重新 up）
  logs [服务名]    跟踪查看日志（可指定服务名）
  ps               查看服务运行状态
  health           对 blog-admin / blog-web 执行一次 HTTP 健康检查
  backup           备份 MySQL 数据目录到 ${DOCKER_DIR}/backup

可用环境变量: HEALTH_RETRIES, HEALTH_INTERVAL
EOF
  exit 1
}

# ---------- 解析参数 ----------
if [ "${1:-}" = "--build" ]; then
  COMPOSE_FILE="docker-compose.yml"
  shift
fi
CMD="${1:-}"
[ "$#" -gt 0 ] && shift || true

# ---------- compose 命令探测（兼容 v1/v2）----------
detect_compose() {
  if docker compose version >/dev/null 2>&1; then
    COMPOSE="docker compose"
  elif command -v docker-compose >/dev/null 2>&1; then
    COMPOSE="docker-compose"
  else
    err "未找到 docker compose / docker-compose，请先安装 Docker。"
    exit 1
  fi
}

# ---------- 前置检查 ----------
preflight() {
  command -v docker >/dev/null 2>&1 || { err "未找到 docker 命令，请先安装 Docker。"; exit 1; }
  detect_compose
  if [ ! -f "${ENV_FILE}" ]; then
    err "缺少环境文件 ${ENV_FILE}"
    err "请基于模板创建并填写真实配置: cp ${ENV_EXAMPLE} ${ENV_FILE}"
    exit 1
  fi
  # 读取 .env 获取数据目录与对外端口
  set -a; . "${ENV_FILE}"; set +a
  APP_DIR="${ONEBLOG_APP_DIR:-/var/.oneblog}"
  ADMIN_PORT="${ONEBLOG_EXPORT_PORT_ADMIN:-8085}"
  WEB_PORT="${ONEBLOG_EXPORT_PORT_WEB:-8443}"
}

# ---------- 准备数据/日志目录 ----------
prepare_dirs() {
  log "准备数据/日志目录: ${APP_DIR}"
  mkdir -p "${APP_DIR}/logs" "${APP_DIR}/redis" "${APP_DIR}/mysql" \
           "${APP_DIR}/blog-admin" "${APP_DIR}/blog-web"
  # 应用容器以非 root (uid 1000) 运行；仅 chown 应用自身写入的目录。
  # mysql/redis 数据目录交由各自官方镜像初始化，不在此 chown，避免权限冲突。
  chown -R 1000:1000 "${APP_DIR}/logs" "${APP_DIR}/blog-admin" "${APP_DIR}/blog-web" 2>/dev/null \
    || log "（提示）chown 失败（可能非 root 执行）。若应用日志写入报权限错误，请手动: chown -R 1000:1000 ${APP_DIR}/{logs,blog-admin,blog-web}"
}

# 在 docs/docker 目录下执行 compose
compose() { ( cd "${DOCKER_DIR}" && ${COMPOSE} -f "${COMPOSE_FILE}" "$@" ); }

# ---------- 单次 HTTP 探测，2xx/3xx 视为健康 ----------
http_ok() {
  local url="$1" code
  if command -v curl >/dev/null 2>&1; then
    code=$(curl -s -o /dev/null -m 4 -w '%{http_code}' "${url}" 2>/dev/null || true)
    [ -n "${code}" ] && [ "${code}" -ge 200 ] && [ "${code}" -lt 400 ]
  else
    wget -q -T 4 -O /dev/null "${url}"
  fi
}

# ---------- 健康门禁：轮询直至健康或超时 ----------
wait_health() {
  local name="$1" url="$2" i=0
  log "等待 ${name} 健康 (${url}) ..."
  while [ ${i} -lt ${HEALTH_RETRIES} ]; do
    if http_ok "${url}"; then log "✓ ${name} 健康。"; return 0; fi
    i=$((i + 1)); sleep ${HEALTH_INTERVAL}
  done
  err "✗ ${name} 健康检查超时，打印最近日志："
  compose logs --tail=50 "${name}" || true
  return 1
}

do_health() {
  local rc=0
  if http_ok "http://127.0.0.1:${ADMIN_PORT}/passport/login/"; then
    log "✓ blog-admin 健康 (:${ADMIN_PORT})"
  else
    err "✗ blog-admin 异常 (:${ADMIN_PORT})"; rc=1
  fi
  if http_ok "http://127.0.0.1:${WEB_PORT}/"; then
    log "✓ blog-web 健康 (:${WEB_PORT})"
  else
    err "✗ blog-web 异常 (:${WEB_PORT})"; rc=1
  fi
  return ${rc}
}

do_up() {
  prepare_dirs
  if [ "${COMPOSE_FILE}" = "docker-compose.yml" ]; then
    log "使用本地源码构建并启动（${COMPOSE_FILE}）..."
    compose up -d --build
  else
    log "拉取预构建镜像并启动（${COMPOSE_FILE}）..."
    compose pull || log "（提示）镜像拉取失败或本地已存在，继续启动。"
    compose up -d
  fi
  log "服务已启动，开始健康门禁检查..."
  wait_health blog-admin "http://127.0.0.1:${ADMIN_PORT}/passport/login/"
  wait_health blog-web   "http://127.0.0.1:${WEB_PORT}/"
  log "✓ 部署完成，服务栈健康。"
  compose ps
}

do_backup() {
  local ts backup_dir
  ts="$(date +%Y%m%d-%H%M%S)"
  backup_dir="${DOCKER_DIR}/backup"
  mkdir -p "${backup_dir}"
  if [ ! -d "${APP_DIR}/mysql" ]; then
    err "MySQL 数据目录不存在: ${APP_DIR}/mysql"; exit 1
  fi
  log "备份 MySQL 数据目录 -> ${backup_dir}/mysql-${ts}.tar.gz"
  tar -czf "${backup_dir}/mysql-${ts}.tar.gz" -C "${APP_DIR}" mysql
  log "✓ 备份完成: ${backup_dir}/mysql-${ts}.tar.gz"
}

# ---------- 主流程 ----------
[ -n "${CMD}" ] || usage
preflight

case "${CMD}" in
  up)      do_up ;;
  down)    compose down ;;
  restart) compose down; do_up ;;
  logs)    compose logs -f "$@" ;;
  ps)      compose ps ;;
  health)  do_health ;;
  backup)  do_backup ;;
  *)       usage ;;
esac
