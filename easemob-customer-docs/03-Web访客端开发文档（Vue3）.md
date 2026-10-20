# 03 · Web 访客端开发文档 — easemob-customer-web

> 面向业务站点/落地页嵌入的访客聊天端：Vue 3 + TypeScript + Vite，既可独立部署为 H5 客服页，也可打包为嵌入式聊天 Widget（一行脚本接入）。

## 1. 技术栈与工程结构

| 项 | 选型 |
|----|------|
| 框架 | Vue 3 (`<script setup>`) + TypeScript + Vite 5 |
| 状态 | Pinia（会话 store / 消息 store / 用户 store） |
| IM | 环信 Web SDK（`easemob-websdk`，基于 mini-core） |
| UI | 自研轻量聊天 UI（访客端要求体积小，不引 Element Plus） |
| 网络 | axios（自建 REST：start/end/evaluate） |
| 其他 | dayjs · localStorage 会话缓存 · vite-plugin-css-injected-by-js（Widget 单文件） |

```
easemob-customer-web
├── index.html                     # H5 入口
├── vite.config.ts                 # 单应用构建（端口 5173）
├── src
│   ├── main.ts                    # 应用入口
│   ├── App.vue
│   ├── api/
│   │   ├── request.ts             # axios 实例（baseURL 指向 customer-open-api）
│   │   └── session.ts             # guestToken / startSession / transferSession
│   │                              #   / closeSession / evaluate / recordMessage
│   ├── sdk/
│   │   ├── easemob.ts             # 环信 SDK 初始化、登录、消息监听（含 ext.bizType 系统事件）
│   │   └── message.ts             # sendGroupText（SDK 直发群消息）
│   ├── store/
│   │   ├── session.ts             # 会话状态（sessionInfo、start、applySystemEvent、transferToHuman）
│   │   ├── message.ts             # 消息列表（append / appendSystem / markSending / markSent / markFailed / clear）
│   │   └── user.ts                # 访客身份（userId / token / easemobToken）
│   ├── types/index.ts             # ChatMessage / SessionInfo 等类型
│   └── views/
│       ├── ChatPage.vue           # 聊天主界面（开始面板 / 头部状态 / 关闭态 / 评价联动）
│       └── components/
│           ├── MessageList.vue    # 消息流
│           ├── MessageItem.vue    # 文本/系统提示气泡
│           ├── ChatInput.vue      # 输入区（支持 disabled）
│           ├── TransferBar.vue    # 转人工提示条（AI 服务时展示）
│           ├── EvaluateDialog.vue # 满意度评价弹窗
│           └── WelcomeCard.vue    # 欢迎语 + 常见问题引导（开始面板与会话顶部复用）
└── package.json
```

> 【现状：未实现】Widget 嵌入式构建（widget.ts / widget.html / Shadow DOM 隔离）、composables 目录、主题样式目录暂不存在，当前为单一 H5 应用构建。

---

## 2. 启动流程（对应产品图主链路）

```
访客点击「开始咨询」/ 常见问题
   │
   ├─ 1. sessionStore.start('web')
   │     ├─ 无 guest token → POST /open/auth/guest-token  （换取 userId/token/easemobToken）
   │     └─ POST /open/session/start                    （服务端幂等：进行中会话直接复用）
   │           ← 返回 { sessionId, groupId, status, handlerType, aiAccount, welcome }
   │     （start 会话内追加欢迎语为系统消息；无 localStorage 会话恢复逻辑，
   │       断线重连依赖服务端「同用户进行中会话复用」幂等）
   │
   ├─ 2. sdk/easemob.ts: bindMessageListener()          // 先注册监听
   │     → loginEasemob(userId, easemobToken)           // ② 环信长连接
   │
   └─ 3. 进入聊天态：WelcomeCard 常见问题 + MessageList + ChatInput
```

### 2.1 环信 SDK 封装

```ts
// src/sdk/easemob.ts
import SDK from 'easemob-websdk'

const conn = new SDK.connection({
  appKey: import.meta.env.VITE_EASEMOB_APP_KEY, // org#app
})

export async function loginEasemob(userId: string, token: string) {
  await conn.open({ user: userId, accessToken: token })
}

export function bindMessageListener(onMsg: (m: EasemobMessage) => void) {
  conn.addEventHandler('cs', {
    onTextMessage:  (m) => onMsg(normalize(m)),
    onImageMessage: (m) => onMsg(normalize(m)),
    onGroupEvent:   (e) => handleGroupEvent(e), // 成员变更（坐席入群）等
    onDisconnected: ()  => reconnectWithBackoff(),
  })
}
```

### 2.2 发送消息（双写链路）

访客消息走「环信 SDK 直发群组（实时） + 自建后端落库（驱动 AI）」双通道：

```ts
// ChatPage.vue onSend()
msgStore.append({ ...本地临时消息, status: 'sending' })
await sendGroupText(text)                            // ① SDK 直发群消息（见 sdk/message.ts）
await recordMessage({ sessionId, content: text, type: 'TXT' })  // ② POST /open/message/record
msgStore.markSent(localId)
// 任一失败 → markFailed（UI 重发）

// src/sdk/message.ts
export async function sendGroupText(text: string) {
  const session = sessionStore.sessionInfo
  const msg = SDK.message.create({
    type: 'txt',
    to: session.groupId,
    chatType: 'groupChat',
    msg: text,
    ext: { sessionId: session.sessionId },          // 业务扩展字段
  })
  msgStore.markSending(id, temp)
  try {
    await conn.send(msg)
    msgStore.markSent(id)
  } catch (e) {
    msgStore.markFailed(id)
    throw e
  }
}
```

> `/open/message/record` 除落库外还驱动 AI 应答（服务端对双通道做了 10s 内容去重，避免回调与 record 重复触发）；会话已关闭时该接口返回 4002 拒写。

---

## 3. 核心状态（Pinia）

```ts
// src/store/session.ts（Pinia setup 写法）
export const useSessionStore = defineStore('session', () => {
  const sessionInfo = ref<SessionInfo | null>(null)   // { sessionId, groupId, status,
                                                      //   handlerType, aiAccount, agent,
                                                      //   welcome, queuePosition }
  async function start(channel = 'web') { /* 见 §2 流程 */ }

  /** 服务端经群消息 ext.bizType 下发的系统事件 */
  function applySystemEvent(e: { type: string; agent?: any; position?: number }) {
    if (e.type === 'TRANSFERRED') {                   // 转人工成功
      sessionInfo.value.handlerType = 'HUMAN'
      sessionInfo.value.agent = e.agent
      sessionInfo.value.queuePosition = 0
    } else if (e.type === 'QUEUE_UPDATE') {
      sessionInfo.value.queuePosition = e.position ?? 0
    } else if (e.type === 'CLOSED') {                 // 会话结束
      sessionInfo.value.status = 'CLOSED'
    }
  }

  /** 转人工：先调 /open/session/transfer（服务端词表/分配），再发「转人工」消息 */
  async function transferToHuman(reason = 'USER_TRANSFER') { ... }

  return { sessionInfo, loading, start, applySystemEvent, setHandlerType, transferToHuman }
})
```

> **关键约定**：服务端下发的「系统提示/转人工/排队」消息，以群文本消息 + `ext.bizType` 区分，前端据此渲染为系统条而非普通气泡，并驱动状态切换。

---

## 4. 关键交互

### 4.1 转人工

- **按钮触发**：AI 服务时展示 `TransferBar`，点击 → `sessionStore.transferToHuman('USER_TRANSFER')`（调 `/open/session/transfer`）并发送关键词消息「转人工」（复用服务端词表路径，双保险）；
- **排队态**：收到 `QUEUE_UPDATE` 显示「当前第 N 位」，用户可取消排队继续 AI；
- **接管态**：收到 `TRANSFERRED` → 顶部状态变为「人工服务中」，TransferBar 隐藏。

### 4.1.1 会话关闭联动（CLOSED）

服务端结束会话时发结束语（`ext.bizType=CLOSED`）并解散环信群组，前端：

1. `sdk/easemob.ts` 收到 `ext.bizType=CLOSED` → `applySystemEvent({type:'CLOSED'})` → `status='CLOSED'`；
2. `ChatPage` 顶部状态显示「已结束」，输入区替换为「会话已结束 + 重新咨询」按钮（`ChatInput` 以 `disabled` 卸载）；
3. `status` 变为 `CLOSED` 的 watcher 自动弹出 `EvaluateDialog`；
4. 点「重新咨询」→ `msgStore.clear()` 清空消息列表 → `start()` 重新发起会话（服务端幂等复用/新建）。

### 4.2 断线恢复

刷新/重连后重新 `start()`：服务端对同用户进行中会话幂等复用（groupId/sessionId 不变），SDK 重连后仅接收新消息，历史消息不回填。

### 4.3 评价

会话结束（服务端 `CLOSED` 事件）→ 弹出 `EvaluateDialog`（1-5 星 + 标签 + 文本）→ `POST /open/session/evaluate`。

### 4.4 未读与提示音

【现状：未实现】Widget 折叠态角标、`document.title` 闪烁、提示音均未开发（当前无 Widget 模式，见 §1 注记）。

---

## 5. Widget 嵌入模式

【现状：未实现】当前为单一 H5 应用（`vite build` 仅产出应用包，无 widget.html / widget.ts / Shadow DOM 隔离）。规划中的接入形态如下，供后续实现参考：

```html
<!-- 业务方页面接入（规划） -->
<script src="https://cdn.example.com/cs-widget.js"
        data-app-key="org#app"
        data-server="https://cs-api.example.com"
        data-user-id="{{业务方用户ID}}"
        data-theme="#3B6CFF"
        data-position="right"></script>
```

- 规划要点：`widget.ts` 读取 `data-*` 完成初始化，悬浮按钮 + 可拖拽聊天面板（iframe-free，Shadow DOM 隔离样式）；
- 构建产物为单 JS（css 内联，vite-plugin-css-injected-by-js），支持 `window.CSWidget.open()/close()/destroy()` 宿主 API；
- 匿名访客：不传 `data-user-id` 时自动生成 `guest_{uuid}` 并持久化 localStorage。

---

## 6. 配置与环境

```env
# .env.production
VITE_API_BASE=https://cs-api.example.com
VITE_EASEMOB_APP_KEY=your-org#your-app
```

| 环境变量 | 说明 |
|----------|------|
| VITE_API_BASE | 自建后端（customer-open-api）地址 |
| VITE_EASEMOB_APP_KEY | 环信 AppKey |

---

## 7. 自测清单

- [ ] 首次发起会话：建群成功、欢迎语到达、AI 自动回复可达
- [ ] 消息收发：文本/图片、失败重发、已读回执
- [ ] 转人工全链路：按钮 → 排队提示 → 坐席接管 → 顶栏切换
- [ ] 断网重连后重新 start 复用原会话，新消息正常接收
- [ ] 结束会话 → 顶栏「已结束」、输入区替换为重新咨询按钮 → 评价弹窗 → 提交成功 → 重新咨询开新会话
- [ ] AI 回复经群消息到达并渲染为 AI 气泡；转人工后顶栏切「人工服务中」
- [ ] Widget 嵌入第三方页面：样式隔离、角标、宿主 API 可用
