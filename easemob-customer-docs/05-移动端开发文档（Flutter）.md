# 05 · 移动端开发文档 — easemob-customer-app

> Flutter 双端（iOS / Android）客服 App。功能与 Web 访客端对齐：发起会话、AI 应答、转人工、评价。

## 1. 技术栈与工程结构

| 项 | 选型 |
|----|------|
| 框架 | Flutter 3.22+ / Dart 3 |
| IM | `im_flutter_sdk` 4.13.0（环信 Flutter SDK） |
| 状态 | Riverpod（`StateNotifier`，独立 provider 文件，未用 riverpod_generator 生成） |
| 网络 | dio（`DioClient`，guest token 手动赋值，无拦截器自动注入） |
| 本地存储 | hive（缓存 userId / sessionId） |
| 图片 | cached_network_image · image_picker（已引入，图片消息未实现） |

```
easemob-customer-app
├── lib
│   ├── main.dart                  # App 入口
│   ├── app.dart                   # MaterialApp
│   ├── config/
│   │   └── env.dart               # apiBase、easemobAppKey（--dart-define 注入）
│   ├── sdk/
│   │   ├── easemob_client.dart    # EasemobClient 单例：init / loginWithToken
│   │   └── message_sender.dart    # MessageSender.sendText（群文本直发，ext.bizType=USER）
│   ├── api/
│   │   ├── dio_client.dart        # dio 单例 + token 静态字段
│   │   └── session_api.dart       # guestToken / start / transfer / record / close / evaluate
│   ├── store/
│   │   ├── session_provider.dart  # SessionNotifier：start / onTransferred / onQueueUpdate / transferToHuman / onClosed
│   │   ├── session_state.dart     # SessionState（sessionId/groupId/status/handlerType/aiAccount/agent/queuePosition）
│   │   └── message_provider.dart  # MessageNotifier：append / appendLocal / markSent / markFailed
│   └── pages/
│       └── chat/
│           ├── chat_page.dart     # 聊天主页（开始咨询 → 会话界面）
│           └── widgets/
│               ├── message_bubble.dart      # 文本气泡
│               ├── chat_input_bar.dart      # 输入栏（文本 + 发送 + 转人工）
│               ├── transfer_banner.dart     # 转人工/排队横幅
│               ├── welcome_card.dart        # 欢迎语
│               └── evaluate_sheet.dart      # 满意度评价底部弹层（已实现，未接入页面）
├── test/
├── android/  ios/
└── pubspec.yaml
```

> 【现状：未实现】`user_provider`、消息监听注册（收消息/系统事件）、失败重试队列、通用 widgets/ 目录。

---

## 2. 初始化与登录

```dart
// lib/sdk/easemob_client.dart（单例，init 幂等）
class EasemobClient {
  Future<void> init() async {
    if (_inited) return;
    final options = EMOptions(appKey: Env.easemobAppKey, autoLogin: false);
    await EMClient.getInstance.init(options);
    _inited = true;
  }

  Future<void> login(String userId, String token) async {
    await init();
    await EMClient.getInstance.loginWithToken(userId, token);   // ② 收发消息通道
  }
}
```

> 【现状：未实现】连接事件监听（addConnectionEventHandler）与消息事件监听（addMessageEvent / addEventHandler）尚未注册，收消息与 `ext.bizType` 系统事件当前不生效（见 §3.2）。

### 2.1 会话启动编排（对应 ① 建群 + AI 接待）

```dart
// lib/store/session_provider.dart
Future<void> start({String channel = 'app'}) async {
  // 1. hive 缓存复用 userId；无 checkActive 本地恢复
  _userId ??= box.get('userId');
  // 2. guest-token → start（后端幂等：进行中会话直接复用）
  final guest = await SessionApi.guestToken({'userId': _userId, 'channel': channel});
  _userId = guest['userId'];
  box.put('userId', _userId!);
  DioClient.token = guest['token'];
  final resp = await SessionApi.startSession({'userId': _userId, 'channel': channel});
  state = state.copyWith(
    sessionId: resp['sessionId'], groupId: resp['groupId'],
    status: resp['status'], handlerType: resp['handlerType'], aiAccount: aiAccount,
  );
  box.put('sessionId', sessionIdStr);
  // 3. 环信登录失败仅打日志，页面照常展示
  try {
    await EasemobClient().login(_userId!, guest['easemobToken']);
  } catch (e) { debugPrint('easemob login failed: $e'); }
}
```

---

## 3. 消息收发

### 3.1 发送（群聊双写，同 Web 端）

```dart
// chat_page.dart _send()
final localId = messageNotifier.appendLocal(text, senderId: session.sessionId);
await MessageSender.sendText(session.groupId!, text);   // ① SDK 直发群消息
await SessionApi.recordMessage({                        // ② 落库并驱动 AI 应答
  'sessionId': session.sessionId, 'content': text, 'type': 'TXT',
});
messageNotifier.markSent(localId);

// lib/sdk/message_sender.dart
class MessageSender {
  static Future<void> sendText(String groupId, String text, {Map<String, dynamic>? ext}) async {
    final msg = EMMessage.createTxtSendMessage(
      targetId: groupId, content: text, chatType: ChatType.GroupChat,
    );
    msg.attributes = {'bizType': 'USER', ...?ext};
    await EMClient.getInstance.chatManager.sendMessage(msg);
  }
}
```

> 【现状：未实现】失败重试队列（发送失败仅 markFailed，无重发按钮逻辑）、图片/表情消息、已读回执；发送失败时 `/open/message/record` 不会执行。

### 3.2 接收与系统事件

【现状：未实现】消息监听未注册，以下流程为规划实现：`onMessagesReceived` 中按 `attributes['bizType']` 分发——`TRANSFERRED`（`SessionNotifier.onTransferred` 已就绪）、`QUEUE_UPDATE`（`onQueueUpdate` 已就绪）、`CLOSED`（`onClosed` 已就绪：状态置 CLOSED 并清除 hive sessionId，但页面未监听、评价弹层 EvaluateSheet 未接入）。

> 历史消息不回填：重启/重进 App 后重新 `start()`，服务端对同用户进行中会话幂等复用，旧消息不恢复。

---

## 4. 关键 UI 规格

| 组件 | 规格（现状） |
|------|------|
| 聊天气泡 | 用户右侧（主题色）、AI/坐席左侧（MessageBubble） |
| 顶栏 | AI 服务中显示「智能客服」；人工显示「人工服务中 · 坐席昵称」 |
| 输入栏 | 文本 + 发送 + 转人工（ChatInputBar）；无图片/表情 |
| 排队横幅 | TransferBanner：转人工按钮 / 排队位置展示（依赖 IM 事件接入） |
| 欢迎卡片 | WelcomeCard：AI 昵称 + 问候语 |
| 评价弹层 | EvaluateSheet 已实现，页面未接入（无 CLOSED 事件监听） |

> 【现状：未实现】系统消息居中灰条、图片/表情输入、发送中防连点、未读红点/本地通知、主题 ThemeExtension 品牌色覆盖。

---

## 5. 构建与环境

```bash
# 开发
flutter run --dart-define=API_BASE=https://dev-cs.example.com \
            --dart-define=EASEMOB_APP_KEY=org#app
# 生产
flutter build apk --release --dart-define=API_BASE=... --dart-define=EASEMOB_APP_KEY=...
flutter build ipa --release ...
```

| dart-define | 说明 |
|-------------|------|
| API_BASE | 自建后端 open-api 地址 |
| EASEMOB_APP_KEY | 环信 AppKey |

---

## 6. 自测清单

- [ ] 点击「开始咨询」：guest-token → start 成功，顶栏/欢迎卡片展示
- [ ] 发送消息：SDK 直发成功 + record 落库触发 AI 回复（当前 AI 回复依赖环信回调侧，App 内收消息待监听接入后可见）
- [ ] 环信登录失败时页面不崩溃、提示可理解
- [ ] 转人工按钮：transfer 接口 + 「转人工」消息发送
- [ ] 结束 → 评价 → 本地 sessionId 清理，再次进入为新会话【依赖 §3.2 事件接入】
- [ ] iOS/Android 双端基础聊天界面正常

> 【现状】收消息、系统事件（TRANSFERRED/QUEUE_UPDATE/CLOSED）、评价弹层联动均未接入，对应用例暂无法执行。
