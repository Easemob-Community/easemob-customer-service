# 环信智能客服系统 · 开发文档总览

> 基于环信 IM 的「AI 客服 + 人工客服」一体化客服产品。
> 核心链路：用户点击客服入口 → 创建群组会话 → AI 账号自动回复 → 按需分配真人坐席接管 → 消息收发与回调闭环。

---

## 1. 文档目录

| 序号 | 文档 | 说明 |
|------|------|------|
| 00 | README.md（本文档） | 产品定位、总体架构、技术栈、仓库结构 |
| 01 | 01-产品设计与业务流程.md | 业务角色、九大核心流程、功能清单（PRD 摘要） |
| 02 | 02-服务端开发文档（Spring Boot）.md | `easemob-customer-service` 详细设计与核心代码 |
| 03 | 03-Web访客端开发文档（Vue3）.md | `easemob-customer-web` 访客聊天端设计与实现 |
| 04 | 04-管理控制台开发文档（Vue3）.md | `easemob-customer-console` 坐席工作台/运营后台 |
| 05 | 05-移动端开发文档（Flutter）.md | `easemob-customer-app` 移动端（iOS/Android） |
| 06 | 06-接口与数据库设计.md | REST API 规范、数据库 DDL、缓存设计 |
| 07 | 07-部署运维与测试.md | 环境、CI/CD、监控告警、测试方案 |

---

## 2. 产品定位

面向需要在线客服能力的业务方（电商、SaaS、金融、教育等），提供：

1. **AI 优先接待**：用户发起会话后，由 AI 客服账号在群组内自动应答，覆盖 80%+ 常见问题；
2. **平滑转人工**：AI 答不上来、用户主动要求、或命中规则时，从坐席池中按策略（平均分配 / 熟客优先 / 指定 ID）分配真人账号加入群组，真人取代 AI 回复；
3. **全端接入**：Web 访客端（可嵌入 H5/站点）、Flutter 移动端（iOS/Android）、管理控制台（坐席工作台 + 运营配置）；
4. **多 AI 厂家可插拔**：AI 厂家配置化管理（地址、账号、密钥、超时），发送/接收消息服务统一抽象，支持同时接入多家 AI 并灰度切换。

**IM 底座不自建**，复用环信（Easemob）的群组、消息收发、回调能力；自建后端专注「账号体系、分配策略、AI 编排、数据沉淀」。

---

## 3. 总体架构

```
┌─────────────────────────────────────────────────────────────────────┐
│                            接入层                                    │
│  easemob-customer-web(Vue3 访客端)   easemob-customer-app(Flutter)   │
│  easemob-customer-console(Vue3 控制台/坐席工作台)                    │
└──────────────┬───────────────────────────────┬──────────────────────┘
               │ HTTPS / REST                  │ 环信 SDK (WebSocket)
┌──────────────▼───────────────────────────────▼──────────────────────┐
│              easemob-customer-service（Spring Boot 自建后端）         │
│ ┌────────────┐ ┌────────────┐ ┌────────────┐ ┌────────────────────┐ │
│ │ 账号服务    │ │ 分配服务    │ │ AI编排服务  │ │ 会话/消息服务       │ │
│ │ AI组/用户组 │ │ 平均/熟客/  │ │ 厂家配置    │ │ 群组创建/消息记录   │ │
│ │ 坐席组     │ │ 指定ID     │ │ 发送/接收   │ │ 转人工记录         │ │
│ └────────────┘ └────────────┘ └────────────┘ └────────────────────┘ │
│ ┌────────────────────────────────────────────────────────────────┐ │
│ │ 环信集成层：REST Client（建群/发消息/加人） + 回调接收（Callback） │ │
│ └────────────────────────────────────────────────────────────────┘ │
│        MySQL（业务数据）   Redis（分配锁/在线态/会话缓存）            │
└──────────────┬───────────────────────────────────────────────────────┘
               │ 环信 REST API / 消息回调
┌──────────────▼───────────────────────────────────────────────────────┐
│                     环信后端服务（Easemob Cloud）                      │
│            发送消息服务 ｜ 回调服务 ｜ 群组 ｜ 用户体系                  │
└──────────────────────────────────────────────────────────────────────┘
```

### 3.1 核心流程（对应产品图 ①~⑨）

| 步骤 | 动作 | 参与方 |
|------|------|--------|
| ① | 获取 AI ID（从账号服务 AI 组取一个在线 AI 账号） | 自建后端 · 账号服务 |
| ② | 接收和发送消息（环信 SDK 通道） | 客户端 ↔ 环信 |
| ③ | 触发消息回调（环信回调服务 → 自建后端，识别转人工意图） | 环信 → 自建后端 |
| ④ | 发给 AI 服务（消息接收服务 → AI 厂家） | 自建后端 → AI 厂家 |
| ⑤ | 返回答案（AI 厂家 → 自建后端） | AI 厂家 → 自建后端 |
| ⑥ | 发送消息到群组，用户收到消息（AI 账号经环信发群消息） | 自建后端 → 环信 → 用户 |
| ⑦ | 获取用户 ID（按分配策略选坐席） | 分配服务 |
| ⑧ | 获取 ID（分配服务读取用户组/熟客关系） | 分配服务 ↔ 账号服务 |
| ⑨ | 返回用户 ID（真人坐席账号返回，加入群组取代 AI） | 账号服务 → 分配服务 |

详见 `01-产品设计与业务流程.md`。

---

## 4. 技术栈总览

| 模块 | 技术选型 |
|------|----------|
| 服务端 | Java 17 · Spring Boot 3.2 · Spring Web / Validation / AOP · MyBatis-Plus · MySQL 8 · Redis 7 · Redisson · OkHttp/RestTemplate（环信 & AI 调用）· Sa-Token（认证，未启用）· Knife4j（接口文档）· Maven |
| Web 访客端 | Vue 3 + Vite + TypeScript · Pinia · 环信 Web SDK（easemob-websdk / mini-core）· （规划中）嵌入式聊天 Widget |
| 管理控制台 | Vue 3 + Vite + TypeScript · Pinia · Vue Router · Element Plus · 环信 Web SDK（坐席实时收发）·（规划中）ECharts 数据看板 |
| Flutter 移动端 | Flutter 3.x · Dart · im_flutter_sdk（环信）· Riverpod · dio · hive |
| 基础设施 | Nginx · Docker / Docker Compose · MySQL 8 · Redis 7 ·（可选）RocketMQ/Kafka 削峰 · Prometheus + Grafana |

---

## 5. Monorepo 仓库结构

```
easemob-customer/
├── easemob-customer-service       # Spring Boot 服务端
│   ├── customer-common/           # 通用：常量/枚举/异常/结果封装/工具
│   ├── customer-account/          # 账号服务：AI组、用户组、坐席组、熟客关系
│   ├── customer-assignment/       # 分配服务：平均分配/熟客优先/指定ID
│   ├── customer-ai/               # AI编排：厂家配置、发送消息服务、消息接收服务
│   ├── customer-conversation/     # 会话服务：群组会话、消息记录、转人工
│   ├── customer-easemob/          # 环信集成：REST Client、Token、回调接收
│   ├── customer-admin-api/        # 控制台后端接口（坐席/运营）
│   ├── customer-open-api/         # 访客端开放接口（发起会话等）
│   └── customer-server/           # 启动模块（聚合、配置、启动类）
│
├── easemob-customer-web           # Vue 3 访客端（H5）
│   ├── src/api  src/sdk  src/store  src/views  src/types
│   └── ...
│
├── easemob-customer-console       # Vue 3 管理控制台
│   ├── src/modules/login/         # 登录
│   ├── src/modules/workbench/     # 坐席工作台（会话列表/收发/结束）
│   ├── src/modules/account/       # 账号管理（坐席/AI 账号 CRUD）
│   ├── src/modules/ai/            # AI 厂家配置与转人工词表
│   ├── src/modules/assignment/    # 分配策略配置
│   ├── src/modules/dashboard/     # 数据看板
│   └── ...
│
├── easemob-customer-app           # Flutter 移动端
│   ├── lib/pages/  lib/sdk/  lib/store/  lib/widgets/
│   └── ...
│
└── easemob-customer-docs/         # 本文档集（sql/init-schema.sql 建库脚本）
```

---

## 6. 关键设计决策

1. **群组即会话**：每一次客服会话 = 一个环信群组，成员 = 用户 + AI 账号（+ 接管后的真人坐席）。天然支持多方、历史消息、状态回执；会话关闭时发送结束语（`ext.bizType=CLOSED`）并解散群组。
2. **AI 也是账号**：AI 客服在环信注册为普通用户（归入「AI 组」），发消息走环信标准能力，业务层只关心「当前由谁回复」。
3. **双通道驱动 AI 应答**：环信消息回调（③）与访客端 `POST /open/message/record`（落库）都会触发 AI 应答编排，服务端以 10s 内容去重保证只应答一次；转人工判定同样由该入口完成。
4. **策略可插拔**：分配策略、AI 厂家均为配置化 + SPI 风格接口，新增策略/厂家不改主流程。
5. **前后端双通道**：控制指令（发起会话、转人工、结束）走自建 REST；聊天消息走环信 SDK 长连接，保证实时性与回执。

---

## 7. 阅读建议

- 产品/项目经理：README → 01；
- 后端工程师：01 → 02 → 06 → 07；
- Web/控制台前端：01 → 03 / 04 → 06；
- 移动端：01 → 05 → 06；
- 测试/运维：06 → 07。
