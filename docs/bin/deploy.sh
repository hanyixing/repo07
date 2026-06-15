#!/bin/bash

#======================================================================
# OneBlog 生产环境部署脚本
# 用法: ./deploy.sh [deploy|rollback|status|backup]
#======================================================================

set -e

# ==================== 配置区域 ====================
DEPLOY_BASE="/home/www"
ADMIN_APP_DIR="${DEPLOY_BASE}/blog-admin"
WEB_APP_DIR="${DEPLOY_BASE}/blog-web"
BACKUP_DIR="${DEPLOY_BASE}/backups"
LOG_DIR="${DEPLOY_BASE}/deploy-logs"
HEALTH_CHECK_TIMEOUT=60
HEALTH_CHECK_INTERVAL=5
ROLLBACK_VERSIONS=3  # 保留的回滚版本数
DEPLOY_TIMESTAMP=$(date +%Y%m%d_%H%M%S)
DEPLOY_LOG="${LOG_DIR}/deploy_${DEPLOY_TIMESTAMP}.log"

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# ==================== 工具函数 ====================

log_info() {
    echo -e "${GREEN}[INFO]${NC} $(date '+%Y-%m-%d %H:%M:%S') - $1" | tee -a ${DEPLOY_LOG}
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $(date '+%Y-%m-%d %H:%M:%S') - $1" | tee -a ${DEPLOY_LOG}
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $(date '+%Y-%m-%d %H:%M:%S') - $1" | tee -a ${DEPLOY_LOG}
}

log_step() {
    echo -e "${BLUE}[STEP]${NC} $(date '+%Y-%m-%d %H:%M:%S') - $1" | tee -a ${DEPLOY_LOG}
}

# 检查命令是否存在
check_command() {
    if ! command -v $1 &> /dev/null; then
        log_error "命令 $1 未安装，请先安装"
        exit 1
    fi
}

# ==================== 预检查 ====================

preflight_check() {
    log_step "执行部署前检查..."

    # 检查必要命令
    check_command java
    check_command curl

    # 检查 Java 版本
    java_version=$(java -version 2>&1 | head -n 1 | awk -F '"' '{print $2}')
    log_info "Java 版本: $java_version"

    if [[ ! "$java_version" =~ ^1\.8 ]]; then
        log_warn "建议使用 Java 8，当前版本: $java_version"
    fi

    # 检查目录权限
    if [[ ! -d "${DEPLOY_BASE}" ]]; then
        log_info "创建部署目录: ${DEPLOY_BASE}"
        mkdir -p ${DEPLOY_BASE}
    fi

    # 检查备份目录
    mkdir -p ${BACKUP_DIR}
    mkdir -p ${LOG_DIR}

    log_info "预检查通过"
}

# ==================== 备份 ====================

backup_current() {
    log_step "备份当前版本..."

    local backup_name="backup_${DEPLOY_TIMESTAMP}.tar.gz"
    local backup_path="${BACKUP_DIR}/${backup_name}"

    # 备份 blog-admin
    if [[ -f "${ADMIN_APP_DIR}/blog-admin.jar" ]]; then
        log_info "备份 blog-admin..."
        tar -czf "${BACKUP_DIR}/admin_${DEPLOY_TIMESTAMP}.tar.gz" \
            -C ${ADMIN_APP_DIR} blog-admin.jar 2>/dev/null || true
    fi

    # 备份 blog-web
    if [[ -f "${WEB_APP_DIR}/blog-web.jar" ]]; then
        log_info "备份 blog-web..."
        tar -czf "${BACKUP_DIR}/web_${DEPLOY_TIMESTAMP}.tar.gz" \
            -C ${WEB_APP_DIR} blog-web.jar 2>/dev/null || true
    fi

    # 清理旧备份（保留最近 N 个版本）
    log_info "清理旧备份（保留最近 ${ROLLBACK_VERSIONS} 个版本）..."
    ls -t ${BACKUP_DIR}/admin_*.tar.gz 2>/dev/null | tail -n +$((ROLLBACK_VERSIONS + 1)) | xargs rm -f 2>/dev/null || true
    ls -t ${BACKUP_DIR}/web_*.tar.gz 2>/dev/null | tail -n +$((ROLLBACK_VERSIONS + 1)) | xargs rm -f 2>/dev/null || true

    log_info "备份完成"
}

# ==================== 健康检查 ====================

health_check() {
    local app_name=$1
    local port=$2
    local url="http://localhost:${port}/actuator/health"

    log_info "等待 ${app_name} 启动 (端口: ${port})..."

    for i in $(seq 1 $((HEALTH_CHECK_TIMEOUT / HEALTH_CHECK_INTERVAL))); do
        sleep ${HEALTH_CHECK_INTERVAL}

        if curl -s -o /dev/null -w "%{http_code}" --max-time 5 ${url} 2>/dev/null | grep -q "200"; then
            log_info "✓ ${app_name} 健康检查通过"
            return 0
        fi

        log_info "等待中... (${i}/${HEALTH_CHECK_TIMEOUT}/${HEALTH_CHECK_INTERVAL})"
    done

    log_error "✗ ${app_name} 健康检查超时 (${HEALTH_CHECK_TIMEOUT}s)"
    return 1
}

# ==================== 部署 ====================

deploy_app() {
    local app_name=$1
    local app_dir=$2
    local jar_file=$3
    local port=$4
    local script_file=$5

    log_step "部署 ${app_name}..."

    # 检查 JAR 文件是否存在
    if [[ ! -f "${jar_file}" ]]; then
        log_error "JAR 文件不存在: ${jar_file}"
        return 1
    fi

    # 创建应用目录
    mkdir -p ${app_dir}
    mkdir -p ${app_dir}/logs

    # 停止现有服务
    if [[ -f "${script_file}" ]]; then
        log_info "停止现有 ${app_name} 服务..."
        bash ${script_file} stop 2>&1 | tee -a ${DEPLOY_LOG} || true
        sleep 3
    fi

    # 复制新 JAR
    log_info "复制新 JAR 文件..."
    cp ${jar_file} ${app_dir}/

    # 启动服务
    log_info "启动 ${app_name}..."
    if [[ -f "${script_file}" ]]; then
        bash ${script_file} start 2>&1 | tee -a ${DEPLOY_LOG}
    else
        # 如果没有脚本，直接启动
        cd ${app_dir}
        nohup java -server -Xms256m -Xmx512m -XX:+UseG1GC \
            -jar ${jar_file} \
            --spring.profiles.active=prod \
            > ${app_dir}/logs/nohup.out 2>&1 &
    fi

    # 健康检查
    health_check ${app_name} ${port}
    return $?
}

# ==================== 主部署流程 ====================

deploy() {
    log_step "开始部署 OneBlog..."

    # 预检查
    preflight_check

    # 备份
    backup_current

    # 检查是否有新的 JAR 文件
    ADMIN_JAR=$(ls -t blog-admin/target/blog-admin.jar 2>/dev/null | head -1)
    WEB_JAR=$(ls -t blog-web/target/blog-web.jar 2>/dev/null | head -1)

    if [[ -z "${ADMIN_JAR}" && -z "${WEB_JAR}" ]]; then
        log_error "未找到 JAR 文件，请先执行构建"
        exit 1
    fi

    # 部署 blog-admin
    if [[ -n "${ADMIN_JAR}" ]]; then
        deploy_app "blog-admin" "${ADMIN_APP_DIR}" "${ADMIN_JAR}" "8085" "${ADMIN_APP_DIR}/blog-admin.sh"
        if [[ $? -ne 0 ]]; then
            log_error "blog-admin 部署失败，执行回滚"
            rollback_app "blog-admin" "${ADMIN_APP_DIR}" "8085" "${ADMIN_APP_DIR}/blog-admin.sh"
        fi
    fi

    # 部署 blog-web
    if [[ -n "${WEB_JAR}" ]]; then
        deploy_app "blog-web" "${WEB_APP_DIR}" "${WEB_JAR}" "8443" "${WEB_APP_DIR}/blog-web.sh"
        if [[ $? -ne 0 ]]; then
            log_error "blog-web 部署失败，执行回滚"
            rollback_app "blog-web" "${WEB_APP_DIR}" "8443" "${WEB_APP_DIR}/blog-web.sh"
        fi
    fi

    log_info "✓ 部署完成！"
    status
}

# ==================== 回滚 ====================

rollback_app() {
    local app_name=$1
    local app_dir=$2
    local port=$3
    local script_file=$4

    log_warn "回滚 ${app_name}..."

    # 查找最新的备份
    local latest_backup=$(ls -t ${BACKUP_DIR}/${app_name#blog-}_*.tar.gz 2>/dev/null | head -1)

    if [[ -z "${latest_backup}" ]]; then
        log_error "未找到 ${app_name} 的备份文件，无法回滚"
        return 1
    fi

    log_info "使用备份: ${latest_backup}"

    # 停止服务
    if [[ -f "${script_file}" ]]; then
        bash ${script_file} stop 2>&1 | tee -a ${DEPLOY_LOG} || true
        sleep 3
    fi

    # 解压备份
    tar -xzf ${latest_backup} -C ${app_dir}/

    # 启动服务
    if [[ -f "${script_file}" ]]; then
        bash ${script_file} start 2>&1 | tee -a ${DEPLOY_LOG}
    fi

    # 健康检查
    health_check ${app_name} ${port}
    if [[ $? -eq 0 ]]; then
        log_info "✓ ${app_name} 回滚成功"
    else
        log_error "✗ ${app_name} 回滚后健康检查失败"
    fi
}

rollback() {
    log_step "执行回滚操作..."

    rollback_app "blog-admin" "${ADMIN_APP_DIR}" "8085" "${ADMIN_APP_DIR}/blog-admin.sh"
    rollback_app "blog-web" "${WEB_APP_DIR}" "8443" "${WEB_APP_DIR}/blog-web.sh"

    status
}

# ==================== 状态检查 ====================

status() {
    log_step "检查服务状态..."

    echo ""
    echo "=========================================="
    echo "OneBlog 服务状态"
    echo "=========================================="

    # 检查 blog-admin
    echo ""
    echo "blog-admin (端口 8085):"
    if [[ -f "${ADMIN_APP_DIR}/blog-admin.sh" ]]; then
        bash ${ADMIN_APP_DIR}/blog-admin.sh status
    else
        pid=$(ps -ef | grep blog-admin.jar | grep -v grep | awk '{print $2}')
        if [[ -n "${pid}" ]]; then
            echo "  状态: 运行中 (PID: ${pid})"
        else
            echo "  状态: 未运行"
        fi
    fi

    # 检查 blog-web
    echo ""
    echo "blog-web (端口 8443):"
    if [[ -f "${WEB_APP_DIR}/blog-web.sh" ]]; then
        bash ${WEB_APP_DIR}/blog-web.sh status
    else
        pid=$(ps -ef | grep blog-web.jar | grep -v grep | awk '{print $2}')
        if [[ -n "${pid}" ]]; then
            echo "  状态: 运行中 (PID: ${pid})"
        else
            echo "  状态: 未运行"
        fi
    fi

    echo ""
    echo "=========================================="
    echo "备份列表:"
    ls -lh ${BACKUP_DIR}/*.tar.gz 2>/dev/null || echo "  无备份"
    echo "=========================================="
    echo ""
}

# ==================== 帮助信息 ====================

usage() {
    echo "用法: $0 [deploy|rollback|status|backup]"
    echo ""
    echo "命令说明:"
    echo "  deploy   - 部署新版本（自动备份、部署、健康检查）"
    echo "  rollback - 回滚到上一个版本"
    echo "  status   - 查看服务状态"
    echo "  backup   - 仅备份当前版本"
    echo ""
    echo "示例:"
    echo "  $0 deploy    # 部署新版本"
    echo "  $0 rollback  # 回滚"
    echo "  $0 status    # 查看状态"
    echo ""
    exit 1
}

# ==================== 主入口 ====================

case "$1" in
    "deploy")
        deploy
        ;;
    "rollback")
        rollback
        ;;
    "status")
        status
        ;;
    "backup")
        preflight_check
        backup_current
        ;;
    *)
        usage
        ;;
esac
