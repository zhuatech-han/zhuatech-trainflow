# TrainFlow 部署与升级

知华科技（上海如静知华信息科技有限公司） · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2。公开源码学习版；商业部署须书面授权。

## 空库容器部署

Docker27+、Compose2+，4GB以上可用内存。执行 `python3 scripts/init-env.py` 创建0600私有 `.env`，从文件读取管理员密码。运行 `docker compose -p trainflow up --build -d`。前端默认127.0.0.1:8106，健康URL同端口 `/actuator/health`。MySQL与后端不向主机开放端口，只有Nginx前端代理。保留项目名以维持同一数据卷。

生成器拒绝覆盖已有 `.env`；手工配置时以 `.env.example` 的名称为准，填写独立强密码。`ADMIN_PASSWORD` 只供空库初始化，已有库不会重置密码。不要发布私有环境文件、测试状态、备份及日志。

## 分离开发

Java21 / Maven3.9，Node24.19.0+ / npm11，Python3.11+。MySQL8创建空库 `zhuatech_trainflow`，受限数据库账号 `trainflow`。后端支持 `DATABASE_CATALOG`（默认zhuatech_trainflow，自定义库时必须与连接一致）、`DATABASE_URL`（默认 `jdbc:mariadb://mysql:3306/zhuatech_trainflow`）、`DATABASE_USER`（默认trainflow）、`DATABASE_PASSWORD`、`ADMIN_PASSWORD`、`COOKIE_SECURE`。开发在私有shell环境注入本机数据库地址，运行 `mvn -f backend/pom.xml spring-boot:run`，监听8080。前端 `cd frontend && npm ci && npm run dev` 使用本机Vite代理至8080，端口在 `vite.config.js` 定义。源代码修改后按README执行格式、测试及构建。

## 验证与清理

创建单独 `trainflow-check` 项目，以全新卷启动，验证健康、登录、业务、权限、Flyway和重启持久化。`scripts/smoke.py` 仅允许本机URL，需全新测试库和私有 `.env`，创建TEST记录，状态保存在被忽略的 `.smoke-state.json`（0600），再次运行会拒绝覆盖。重启后执行 `--verify`。仅清理自己的测试项目，`docker compose -p trainflow-check down -v` 会删除该项目测试数据，不能用于正式数据。手工删除本次 `.smoke-state.json` 后才能重新执行全新验收。

## 持久数据与升级

MySQL数据卷为持久存储。上线前备份库并验证恢复，监控存储与健康。升级必须保留V1、V2、V3原校验和，新增V4等迁移；先在备份副本运行构建和完整验收，再升级实例。JPA只校验结构。回退涉及迁移时需恢复经过验证的备份，不依赖应用版本倒退自动撤销SQL。正式运维备份和灾备需由部署方实施。

正式访问使用HTTPS反向代理，把COOKIE_SECURE设为true；受控配置BIND_ADDRESS，避免直接向公网暴露数据库。容器使用非root应用账号，前端有基础安全响应头。这里没有生产服务器、域名证书或外部账号配置，不提供公网托管服务。
