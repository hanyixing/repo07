#!/bin/bash

# ===== 可配置项（均可用环境变量覆盖）=====
ROOT_DIR="/home/www/blog-admin"
APP_NAME=blog-admin.jar
# Spring profile，默认 prod
PROFILE=${SPRING_PROFILES_ACTIVE:-prod}
# JVM 参数
JAVA_OPTS=${JAVA_OPTS:-"-server -Xms256m -Xmx512m"}
# 健康检查：应用端口 + 一个稳定返回 2xx/3xx 的路径
HEALTH_PORT=8085
HEALTH_PATH="/passport/login/"
HEALTH_URL="http://127.0.0.1:${HEALTH_PORT}${HEALTH_PATH}"
HEALTH_RETRIES=${HEALTH_RETRIES:-30}
HEALTH_INTERVAL=${HEALTH_INTERVAL:-3}
# 日志（按天切分）
LOG_DIR="${ROOT_DIR}/logs"
LOG_FILE="${LOG_DIR}/app-$(date +%F).out"
# 日志保留天数
LOG_KEEP_DAYS=${LOG_KEEP_DAYS:-14}

NOW_DATE=$(date +%c)

usage() {
    echo "用法: sh blog-admin.sh [start|stop|restart|status|log|health|clean]"
    echo "  start   启动并等待健康检查通过"
    echo "  stop    优雅停止（先 TERM，超时再 KILL）"
    echo "  restart 重启"
    echo "  status  查看进程与 HTTP 健康状态"
    echo "  log     实时跟踪当天日志"
    echo "  health  执行一次 HTTP 健康检查（健康返回 0）"
    echo "  clean   清理 ${LOG_KEEP_DAYS} 天前的日志"
    echo "环境变量: SPRING_PROFILES_ACTIVE, JAVA_OPTS, LOG_KEEP_DAYS, HEALTH_RETRIES, HEALTH_INTERVAL"
    exit 1
}

mkdir -p "${LOG_DIR}"
cd "${ROOT_DIR}" || exit 1

echo "当前时间：${NOW_DATE}"

is_exist(){
  pid=$(ps -ef | grep "${APP_NAME}" | grep -v grep | awk '{print $2}')
  [ -n "${pid}" ]
}

# 单次 HTTP 健康探测，2xx/3xx 视为健康（返回 0）
http_ok(){
  if command -v curl >/dev/null 2>&1; then
    local code
    code=$(curl -s -o /dev/null -m 4 -w '%{http_code}' "${HEALTH_URL}" 2>/dev/null)
    [ -n "${code}" ] && [ "${code}" -ge 200 ] && [ "${code}" -lt 400 ]
  elif command -v wget >/dev/null 2>&1; then
    wget -q -T 4 -O /dev/null "${HEALTH_URL}"
  else
    echo "未找到 curl/wget，跳过 HTTP 健康检查。"
    return 0
  fi
}

# 轮询直至健康或超时
wait_health(){
  echo "等待 ${APP_NAME} 健康检查（${HEALTH_URL}）..."
  local i=0
  while [ ${i} -lt ${HEALTH_RETRIES} ]; do
    if http_ok; then
      echo "✓ ${APP_NAME} 健康检查通过。"
      return 0
    fi
    i=$((i + 1))
    sleep ${HEALTH_INTERVAL}
  done
  echo "✗ ${APP_NAME} 健康检查未通过（已重试 ${HEALTH_RETRIES} 次）。请查看日志：${LOG_FILE}"
  return 1
}

start(){
  if is_exist; then
    echo "${APP_NAME} 正在运行。 pid=${pid} 。"
    return 0
  fi
  echo "使用 profile=${PROFILE} 启动 ${APP_NAME}，日志写入：${LOG_FILE}"
  nohup java ${JAVA_OPTS} -jar "${ROOT_DIR}/${APP_NAME}" --spring.profiles.active=${PROFILE} >> "${LOG_FILE}" 2>&1 &
  wait_health
}

stop(){
  if ! is_exist; then
    echo "${APP_NAME} 未运行！"
    return 0
  fi
  echo "正在优雅停止 ${APP_NAME} (pid=${pid}) ..."
  kill "${pid}" 2>/dev/null
  local i=0
  while [ ${i} -lt 15 ]; do
    is_exist || { echo "${APP_NAME} 已停止。"; return 0; }
    i=$((i + 1))
    sleep 1
  done
  echo "优雅停止超时，强制 kill -9 ${pid}"
  kill -9 "${pid}" 2>/dev/null
  echo "${APP_NAME} 已强制停止。"
}

status(){
  if is_exist; then
    echo "${APP_NAME} 正在运行。Pid is ${pid}"
    if http_ok; then
      echo "HTTP 健康状态：健康 (${HEALTH_URL})"
    else
      echo "HTTP 健康状态：异常 (${HEALTH_URL})"
    fi
  else
    echo "${APP_NAME} 未运行！"
  fi
}

health(){
  if http_ok; then
    echo "✓ 健康 (${HEALTH_URL})"
    exit 0
  else
    echo "✗ 异常 (${HEALTH_URL})"
    exit 1
  fi
}

log(){
  echo "日志文件位置：${LOG_FILE}"
  tail -f "${LOG_FILE}"
}

clean(){
  echo "清理 ${LOG_DIR} 下修改时间超过 ${LOG_KEEP_DAYS} 天的日志..."
  find "${LOG_DIR}" -name 'app-*.out' -type f -mtime +${LOG_KEEP_DAYS} -print -delete
  echo "清理完成。"
}

restart(){
  stop
  start
}

case "$1" in
  "start") start ;;
  "stop") stop ;;
  "status") status ;;
  "restart") restart ;;
  "log") log ;;
  "health") health ;;
  "clean") clean ;;
  *) usage ;;
esac
