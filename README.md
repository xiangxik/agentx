# AgentX SaaS Chatbot

基于文档驱动的多租户 SaaS Chatbot 系统骨架仓库。

## 技术栈

- 前端：React + TypeScript + Vite + npm workspaces
- 后端：Java 21 + Spring Boot + Maven
- 数据库：PostgreSQL + pgvector
- 部署：Docker Compose + Nginx

## 目录

```text
backend/                  Spring Boot 模块化单体
frontend/
  apps/                   四个前端应用
  packages/               共享包
deploy/                   Docker / Nginx / 容器脚本
doc/                      需求与设计文档
```

## 快速开始

1. 复制环境变量

   ```bash
   cp .env.example .env
   ```

2. 安装前端依赖

   ```bash
   npm install
   ```

3. 启动前端开发

   ```bash
   npm run dev --workspace @agentx/tenant-admin
   npm run dev --workspace @agentx/super-admin
   npm run dev --workspace @agentx/chat-page
   npm run dev --workspace @agentx/chatbot-widget
   ```

4. 启动后端

   ```bash
   export JAVA_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
   export PATH="$JAVA_HOME/bin:/opt/homebrew/bin:$PATH"
   mvn -f backend/pom.xml spring-boot:run
   ```

   本地默认 `dev` 配置使用 H2 文件数据库，Docker 部署仍使用 PostgreSQL/pgvector。

## 统一命令

- `npm run format`：格式化前端 + 后端
- `npm run lint`：前端 lint + 后端 spotless 检查
- `npm run typecheck`：前端类型检查
- `npm run test`：前端测试 + 后端测试
- `npm run build`：前端构建 + 后端打包
- `npm run verify`：全量质量门禁

## 当前阶段

- 已完成工程骨架、共享包、CI、Docker 部署骨架、后端模块化入口与基础数据库迁移。
- 下一阶段优先实现 MVP 闭环：认证/权限 → 租户 → 套餐 → Chatbot 配置 → FAQ → 对话闭环。
