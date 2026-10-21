# easemob-customer-service · 智能客服服务端

基于环信 IM 的「AI 客服 + 人工客服」一体化客服系统的自建后端。负责账号体系、会话管理、坐席分配策略、AI 应答编排与数据沉淀;IM 能力(群组/消息/回调)复用环信云。

## 技术栈

Java 17+ · Spring Boot 3.2 · MyBatis-Plus · MySQL 8 · Redis 7 + Redisson · OkHttp(环信/AI 调用) · Sa-Token · Knife4j 接口文档 · Maven 多模块

## 模块结构

| 模块 | 职责 |
|------|------|
| customer-common | 常量、枚举、异常、统一返回、请求日志过滤器 |
| customer-account | 账号服务:AI 组、技能组、坐席、熟客关系 |
| customer-assignment | 分配服务:指定坐席 / 熟客优先 / 智能轮询策略链 |
| customer-ai | AI 编排:厂家配置(可插拔)、发送/接收消息、转人工词表 |
| customer-conversation | 会话服务:群组会话、消息记录、转人工、评分、定时任务 |
| customer-easemob | 环信集成:REST Client(建群/发消息/加人/解散)、用户令牌、回调接收 |
| customer-open-api | 访客端开放接口(发起会话、消息、转人工、评分) |
| customer-admin-api | 控制台接口(坐席工作台、账号管理、AI 配置、统计看板) |
| customer-server | 启动模块(聚合配置与依赖) |
| easemob-customer-docs | 项目文档集(产品设计、接口与数据库设计、部署运维、建库脚本) |

## 本地运行

前置依赖:JDK 17+、Maven 3.8+、MySQL 8(建库 `easemob_customer` 并导入 `easemob-customer-docs/sql/init-schema.sql`)、Redis 7。

1. 复制私有配置:`cp customer-server/src/main/resources/application-local.yml.example customer-server/src/main/resources/application-local.yml`,填入本地数据库密码与环信凭证(该文件已被 gitignore)
2. 启动:IDEA 直接运行 `CustomerServerApplication`,或 `mvn spring-boot:run -pl customer-server`(默认激活 `local` profile,自动打印 SQL)
3. 接口文档:`http://localhost:8080/doc.html`(Knife4j)

## 生产构建与部署

```bash
mvn clean package -DskipTests
# 产物:customer-server/target/customer-server-0.0.1-SNAPSHOT.jar
# 生产配置 application-prod.yml(含数据库密码与环信凭证)随 jar 打包,已被 gitignore,请勿提交
```

服务器启动:

```bash
java -jar customer-server-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod --server.port=8082
```

> 注意:请使用 `mvn clean package` 全量打包,避免 IDE 增量编译产物混入 jar。

## 配置体系

| 文件 | 说明 | 是否提交 |
|------|------|----------|
| application.yml | 公共配置,默认激活 `local`,敏感项无默认值 | ✅ |
| application-local.yml | 本地环境私有配置 | ❌ gitignore |
| application-prod.yml | 生产环境私有配置(随 jar 打包) | ❌ gitignore |
| application-local.yml.example | 私有配置模板 | ✅ |

## 日志

- 控制台 + 文件双输出,文件位于工作目录 `logs/easemob-customer.log`
- 按天滚动、单文件 50MB、保留 30 天
- 所有 HTTP 请求由 `RequestLoggingFilter` 统一记录(方法/路径/状态/耗时/IP/请求体)
- 环信 API 调用失败会记录状态码与响应体原文,便于排查

## 核心链路

访客发起会话 → 创建环信群组(AI 入群自动接待)→ 消息回调触发 AI 应答 → 命中转人工(用户点击/关键词/AI 判断/连续失败)→ 策略链分配坐席入群接管 → 会话结束(关闭/评分)→ 发送结束语并解散群组。

更多详见 `easemob-customer-docs/` 文档集。
