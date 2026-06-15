#!/bin/bash

ROOT_DIR="/home/www/blog-admin"
APP_NAME=blog-admin.jar
LOG_DIR="${ROOT_DIR}/logs"
HEALTH_CHECK_URL="http://localhost:8085/actuator/health"
HEALTH_CHECK_INTERVAL=30
HEALTH_CHECK_TIMEOUT=10
MAX_LOG_SIZE=100  # MB
LOG_RETENTION_DAYS=30
NOW_DATE=$(date +%c)

# 创建日志目录
mkdir -p ${LOG_DIR}

usage() {
    echo "用法: sh blog-admin.sh [start|stop|restart|status|log|health|clean-log]"
    echo "  start    - 启动应用"
    echo "  stop     - 停止应用"
    echo "  restart  - 重启应用"
    echo "  status   - 查看状态"
    echo "  log      - 实时查看日志"
    echo "  health   - 执行健康检查"
    echo "  clean-log - 清理过期日志"
    exit 1
}

cd ${ROOT_DIR}

echo "当前时间：${NOW_DATE}"

is_exist(){
  pid=`ps -ef|grep ${APP_NAME}|grep -v grep|awk '{print $2}'`
  if [[ -z "${pid}" ]]; then
   return 1
  else
    return 0
  fi
}

# 健康检查函数
health_check(){
  if command -v curl &> /dev/null; then
    response=$(curl -s -o /dev/null -w "%{http_code}" --max-time ${HEALTH_CHECK_TIMEOUT} ${HEALTH_CHECK_URL} 2>/dev/null)
    if [[ "$response" == "200" ]]; then
      echo "[健康检查] ✓ 服务正常运行 (HTTP $response)"
      return 0
    else
      echo "[健康检查] ✗ 服务异常 (HTTP $response)"
      return 1
    fi
  else
    echo "[健康检查] 警告: curl 未安装，跳过 HTTP 健康检查"
    is_exist
    if [[ $? -eq "0" ]]; then
      echo "[健康检查] ✓ 进程存在 (PID: $pid)"
      return 0
    else
      echo "[健康检查] ✗ 进程不存在"
      return 1
    fi
  fi
}

# 日志轮转函数
rotate_logs(){
  local log_file="${LOG_DIR}/blog-admin.log"
  if [[ -f "$log_file" ]]; then
    local size=$(du -m "$log_file" 2>/dev/null | cut -f1)
    if [[ ${size:-0} -gt ${MAX_LOG_SIZE} ]]; then
      echo "[日志轮转] 日志文件大小 ${size}MB，执行轮转"
      mv "$log_file" "${log_file}.$(date +%Y%m%d_%H%M%S)"
      # 通知应用重新打开日志文件（如果支持）
      is_exist
      if [[ $? -eq "0" ]]; then
        kill -USR1 $pid 2>/dev/null || true
      fi
    fi
  fi
}

# 清理过期日志
clean_old_logs(){
  echo "[日志清理] 清理 ${LOG_RETENTION_DAYS} 天前的日志..."
  find ${LOG_DIR} -name "*.log.*" -type f -mtime +${LOG_RETENTION_DAYS} -exec rm -f {} \;
  echo "[日志清理] 完成"
}

# 后台健康检查守护进程
start_health_monitor(){
  (
    while true; do
      sleep ${HEALTH_CHECK_INTERVAL}
      is_exist
      if [[ $? -ne "0" ]]; then
        echo "[$(date '+%Y-%m-%d %H:%M:%S')] [健康监控] ✗ 服务已停止，尝试重启..."
        start
        sleep 10
        health_check
        if [[ $? -ne "0" ]]; then
          echo "[$(date '+%Y-%m-%d %H:%M:%S')] [健康监控] 重启失败，请检查"
        fi
      fi
      rotate_logs
    done
  ) &
  echo $! > ${ROOT_DIR}/health-monitor.pid
}

stop_health_monitor(){
  if [[ -f "${ROOT_DIR}/health-monitor.pid" ]]; then
    monitor_pid=$(cat ${ROOT_DIR}/health-monitor.pid)
    kill $monitor_pid 2>/dev/null || true
    rm -f ${ROOT_DIR}/health-monitor.pid
  fi
}

start(){
  is_exist
  if [[ $? -eq "0" ]]; then
    echo "${APP_NAME} 正在运行。 pid=${pid} ."
  else
    nohup java -server -Xms256m -Xmx512m -XX:+UseG1GC \
      -jar ${ROOT_DIR}/${APP_NAME} \
      --spring.profiles.active=prod \
      > ${LOG_DIR}/nohup.out 2>&1 &
    echo "${APP_NAME} 正在启动，请查看日志确保运行正常。"

    # 等待启动并执行健康检查
    echo "等待应用启动..."
    for i in $(seq 1 30); do
      sleep 2
      is_exist
      if [[ $? -eq "0" ]]; then
        echo "进程已启动 (PID: $pid)"
        health_check
        if [[ $? -eq "0" ]]; then
          echo "✓ 应用启动成功"
          start_health_monitor
          return 0
        fi
      fi
    done
    echo "✗ 应用启动超时，请检查日志"
    return 1
  fi
}

stop(){
  stop_health_monitor
  is_exist
  if [[ $? -eq "0" ]]; then
    echo "正在停止 ${APP_NAME} (PID: $pid)..."
    kill $pid
    # 等待进程优雅退出
    for i in $(seq 1 15); do
      sleep 1
      is_exist
      if [[ $? -ne "0" ]]; then
        echo "✓ ${APP_NAME} 已停止"
        return 0
      fi
    done
    # 强制杀死
    echo "优雅退出超时，强制停止..."
    kill -9 $pid
    echo "${pid} 进程已被杀死，程序停止运行"
  else
    echo "${APP_NAME} 未运行！"
  fi
}

status(){
  is_exist
  if [[ $? -eq "0" ]]; then
    echo "${APP_NAME} 正在运行。Pid is ${pid}"
    health_check
  else
    echo "${APP_NAME} 未运行！"
  fi
}

log(){
  local log_file="${LOG_DIR}/nohup.out"
  if [[ -f "$log_file" ]]; then
    echo "日志文件位置：${log_file}"
    tail -f ${log_file}
  else
    echo "日志文件不存在：${log_file}"
  fi
}

restart(){
  stop
  sleep 2
  start
}

case "$1" in
  "start")
    start
    ;;
  "stop")
    stop
    ;;
  "status")
    status
    ;;
  "restart")
    restart
    ;;
  "log")
    log
    ;;
  "health")
    health_check
    ;;
  "clean-log")
    clean_old_logs
    ;;
  *)
    usage
    ;;
esac
