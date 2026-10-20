# 02 · 服务端开发文档 — easemob-customer-service

## 1. 工程概览

- **技术栈**：Java 17 · Spring Boot 3.2.x · MyBatis-Plus 3.5 · MySQL 8 · Redis 7（Redisson）· Sa-Token · OkHttp3 · Knife4j · Maven 多模块
- **运行形态**：无状态单体（可水平扩容），所有共享状态放 Redis/MySQL；回调与 AI 调用线程池隔离。
- **包结构**（多模块 Maven）：

```
easemob-customer-service
├── pom.xml                        # 父 POM，依赖管理
├── customer-common                # 通用层
│   └── src/main/java/com/easemob/cs/common
│       ├── constant/              # 常量、Redis Key、环信常量
│       ├── enums/                 # AccountType/SessionStatus/StrategyType/MsgType
│       ├── exception/             # BizException + GlobalExceptionHandler
│       ├── result/                # R<T> 统一响应、PageResult
│       └── util/                  # JsonUtil/IdUtil/AesUtil/TraceUtil
├── customer-easemob               # 环信集成层
│   └── .../easemob
│       ├── config/                # EasemobProperties(AppKey/OrgName/AppName/凭据)
│       ├── token/                 # AppTokenManager（缓存+到期前刷新）
│       ├── client/                # EasemobUserClient / GroupClient / MessageClient
│       └── callback/              # CallbackController（回调入口）、CallbackDispatchService（分发）、MessageCallbackHandler（处理器接口）；CallbackAuthFilter 验签【未实现】
├── customer-account               # 账号服务
│   └── .../account
│       ├── controller/            # AccountAdminController（控制台用）
│       ├── entity/                # CsAccount / CsGroup / CsFamiliarRelation
│       ├── mapper/
│       └── service/               # AccountService / GroupService / PresenceService
├── customer-assignment            # 分配服务
│   └── .../assignment
│       ├── strategy/              # AssignmentStrategy(SPI) + 3 个实现
│       ├── queue/                 # WaitQueueService（Redis ZSet 排队）
│       └── service/               # AssignmentService（门面）
├── customer-ai                    # AI 编排
│   └── .../ai
│       ├── provider/              # AiProviderAdapter(SPI) + OpenAiAdapter/DifyAdapter...
│       ├── entity/                # CsAiProvider 配置
│       └── service/               # AiChatService / AiRouteService / TransferJudge / UserMessageCallbackHandler
├── customer-conversation          # 会话与消息
│   └── .../conversation
│       ├── entity/                # CsSession / CsMessage / CsTransferRecord
│       ├── service/               # SessionService / MessageService / EvaluateService
│       └── job/                   # SessionTimeoutJob / QueueConsumeJob / StatJob
├── customer-open-api              # 访客端开放接口
│   └── .../open/controller        # OpenSessionController / OpenMessageController
├── customer-admin-api             # 控制台接口
│   └── .../admin/controller       # WorkbenchController / AiConfigController / ...
└── customer-server                # 启动模块
    ├── src/main/java/.../CustomerServerApplication.java
    └── src/main/resources/
        ├── application.yml  application-dev.yml  application-prod.yml
        └── mapper/xml（如需）
```

---

## 2. 通用层（customer-common）

### 2.1 统一响应与异常

```java
public record R<T>(int code, String msg, T data) {
    public static <T> R<T> ok(T data)        { return new R<>(0, "ok", data); }
    public static <T> R<T> fail(int c, String m){ return new R<>(c, m, null); }
}

@Getter
public class BizException extends RuntimeException {
    private final int code;
    public BizException(int code, String msg) { super(msg); this.code = code; }
}

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BizException.class)
    public R<Void> biz(BizException e) { return R.fail(e.getCode(), e.getMessage()); }

    @ExceptionHandler(Exception.class)
    public R<Void> unknown(Exception e, HttpServletRequest req) {
        log.error("[{}] unhandled", req.getRequestURI(), e);
        return R.fail(500, "系统繁忙，请稍后再试");
    }
}
```

### 2.2 核心枚举

```java
public enum AccountType   { AI, USER, AGENT }                    // AI组/用户组/坐席组
public enum SessionStatus { CREATED, AI_SERVING, HUMAN_SERVING, CLOSED, EVALUATED }
public enum StrategyType  { ROUND_ROBIN, FAMILIAR_FIRST, SPECIFY } // 平均/熟客/指定
public enum HandlerType   { AI, HUMAN }
```

### 2.3 Redis Key 规范

```
cs:token:easemob            String  环信 App Token（带过期）
cs:agent:online             Set     在线坐席 ID
cs:agent:load               ZSet    坐席当前接待数（score=负载）
cs:agent:lastAssign         ZSet    坐席最近派单时间（轮转用）
cs:queue:{skillGroup}       ZSet    等待队列（score=入队时间戳）
cs:session:cur:{userId}     String  用户当前进行中会话 ID
cs:msg:idem:{msgId}         String  消息幂等（SETNX + TTL 24h）
cs:lock:assign:{userId}     Lock    同一用户转人工分配锁
cs:msg:ai:dedup:{sessionId}:{md5(question)}  String  10s  AI应答去重（环信回调与 /open/message/record 双通道）
```

> 注：`cs:assignment:strategy:*` 策略缓存【现状：未实现】，策略读库在每次分配时进行。

---

## 3. 环信集成层（customer-easemob）

### 3.1 配置

```yaml
easemob:
  org-name: your-org
  app-name: your-app
  client-id: xxxx
  client-secret: xxxx
  base-url: https://a1.easemob.com
  callback-secret: your-callback-sign-secret   # 回调验签
```

```java
@Data @Component
@ConfigurationProperties(prefix = "easemob")
public class EasemobProperties {
    private String orgName, appName, clientId, clientSecret, baseUrl, callbackSecret;
    public String appKey() { return orgName + "#" + appName; }
    public String apiBase() { return baseUrl + "/" + orgName + "/" + appName; }
}
```

### 3.2 Token 管理（缓存 + 提前刷新）

```java
@Component
@RequiredArgsConstructor
public class AppTokenManager {
    private final EasemobProperties props;
    private final StringRedisTemplate redis;
    private final OkHttpClient http;

    private static final String KEY = "cs:token:easemob";

    public String getToken() {
        String cached = redis.opsForValue().get(KEY);
        if (cached != null) return cached;
        synchronized (this) {
            cached = redis.opsForValue().get(KEY);
            if (cached != null) return cached;
            // POST /{org}/{app}/token  {grant_type, client_id, client_secret}
            TokenResp resp = fetchTokenFromEasemob();
            // 提前 5 分钟过期
            redis.opsForValue().set(KEY, resp.accessToken(),
                    resp.expiresIn() - 300, TimeUnit.SECONDS);
            return resp.accessToken();
        }
    }
}
```

### 3.3 环信 Client 封装（建群 / 发消息 / 加成员）

```java
@Component
@RequiredArgsConstructor
public class EasemobGroupClient {
    private final EasemobProperties props;
    private final AppTokenManager tokenManager;
    private final OkHttpClient http;

    /** 创建群组：群主=用户，成员含 AI 账号（步骤①后调用） */
    public String createGroup(String ownerUserId, List<String> members, String groupName) {
        String url = props.apiBase() + "/chatgroups";
        Map<String, Object> body = Map.of(
                "groupname", groupName,
                "desc", "customer-service-session",
                "public", false, "members_only", true,
                "allowinvites", false,
                "maxusers", 10,
                "owner", ownerUserId,
                "members", members);
        JsonNode resp = post(url, body);
        return resp.path("data").path("groupid").asText();
    }

    /** 真人坐席加入群组（步骤⑨之后） */
    public void addMember(String groupId, String agentId) {
        post(props.apiBase() + "/chatgroups/" + groupId + "/users/" + agentId, Map.of());
    }

    /** 解散群组（会话关闭时调用；DELETE /chatgroups/{groupId}，失败只记日志不阻断关会话） */
    public void dismissGroup(String groupId) {
        delete(props.apiBase() + "/chatgroups/" + groupId);
    }
}

@Component
@RequiredArgsConstructor
public class EasemobMessageClient {
    /** 以指定账号身份向群组发消息（步骤⑥：AI 回复；以及系统提示消息） */
    public void sendGroupText(String from, String groupId, String text) {
        Map<String, Object> body = Map.of(
                "from", from,
                "to", List.of(groupId),
                "type", "txt",
                "target_type", "chatgroups",
                "body", Map.of("msg", text));
        post(props.apiBase() + "/messages/chatgroups", body);
    }
}
```

### 3.4 回调接收（步骤③的入口）

```java
@RestController
@RequestMapping("/easemob/callback")
@RequiredArgsConstructor
public class CallbackController {
    private final CallbackDispatchService dispatchService;

    /** 环信发送后回调：msg 已投递，业务侧据此编排 AI/转人工 */
    @PostMapping("/message")
    public Map<String, String> onMessage(@RequestBody CallbackPayload payload,
                                         HttpServletRequest req) {
        // TODO: CallbackAuthFilter 验签/校验来源（尚未实现）
        dispatchService.dispatchAsync(payload);            // 异步处理，快速 ACK
        return Map.of("status", "ok");                     // 必须 200 + ok，否则环信重推
    }
}
```

回调分发与业务编排分离（避免 customer-easemob 反向依赖业务模块）：easemob 模块只负责接收与分发，编排在 customer-ai 实现：

```java
// customer-easemob：处理器接口 + 分发
public interface MessageCallbackHandler {
    void handle(CallbackPayload payload);
}

@Service
@RequiredArgsConstructor
public class CallbackDispatchService {
    private final List<MessageCallbackHandler> handlers;

    @Async("callbackExecutor")   // 独立线程池：core=8, max=32, queue=500
    public void dispatchAsync(CallbackPayload p) {
        for (MessageCallbackHandler h : handlers) {
            try { h.handle(p); } catch (Exception e) { log.error("callback handler failed", e); }
        }
    }
}

// customer-ai：用户消息编排
@Service
@RequiredArgsConstructor
public class UserMessageCallbackHandler implements MessageCallbackHandler {
    private final MessageService messageService;
    private final AiChatService aiChatService;

    public void handle(CallbackPayload p) {
        // 1. 幂等：msgId SETNX，重复回调直接返回
        if (!messageService.tryMarkProcessed(p.getMsgId())) return;
        // 2. 只处理「用户」在客服群里发的消息
        CsSession session = messageService.loadSessionByGroup(p.getGroupId());
        if (session == null || !Objects.equals(p.getFrom(), session.getUserAccountId())) return;
        // 3. 落库用户消息
        messageService.saveUserMessage(session, p);
        // 4. 交给 AI 编排（内部判断会话状态/转人工词表/去重）
        aiChatService.handleUserMessage(session, p.getText());
    }
}
```

> **双通道触发**：本地开发时环信回调到不了 localhost，访客端 SDK 直发后调用的
> `POST /open/message/record` 也会驱动 `aiChatService.handleUserMessage`；
> 生产环境两通道并存时由 `cs:msg:ai:dedup:{sessionId}:{内容MD5}`（10s）去重，不会重复回复。

---

## 4. 账号服务（customer-account）

### 4.1 实体

```java
@Data @TableName("cs_account")
public class CsAccount {
    private Long id;
    private String easemobId;      // 环信用户 ID
    private AccountType type;      // AI / USER / AGENT
    private Long groupId;          // 所属组（AI 组/技能组）
    private String nickname;
    private String avatar;
    private Integer status;        // 1 启用 0 停用
    private Integer maxSessions;   // 坐席接待上限（仅 AGENT）
    private LocalDateTime createTime;
}

@Data @TableName("cs_familiar_relation")
public class CsFamiliarRelation {  // 熟客关系
    private Long id;
    private String userId;
    private String agentId;
    private Integer serveCount;
    private LocalDateTime lastServeTime;
}
```

### 4.2 关键能力

```java
@Service
@RequiredArgsConstructor
public class AccountService {
    /** 步骤①：取一个可用 AI 账号（组内轮询 + Redis 指针） */
    public CsAccount pickAiAccount(Long aiGroupId) {
        List<CsAccount> ais = aiMapper.selectEnabledByGroup(aiGroupId);
        if (ais.isEmpty()) throw new BizException(4001, "AI 账号池为空");
        String key = "cs:ai:rr:" + aiGroupId;
        long idx = redis.opsForValue().increment(key, 1) - 1;
        return ais.get((int) (idx % ais.size()));
    }

    /** 熟客查询（步骤⑧输入之一） */
    public Optional<CsAccount> findFamiliarAgent(String userId, int validDays) { ... }

    /** 坐席在线态：坐席端 SDK 登录/登出后上报，写 cs:agent:online */
    public void agentOnline(String agentId)  { redis.opsForSet().add(ONLINE, agentId); }
    public void agentOffline(String agentId) { redis.opsForSet().remove(ONLINE, agentId); }
}
```

---

## 5. 分配服务（customer-assignment）

### 5.1 策略 SPI

```java
public interface AssignmentStrategy {
    StrategyType type();
    /** 返回命中的坐席；空 = 无可分配，进入排队 */
    Optional<CsAccount> assign(AssignContext ctx);
}

@Data @Builder
public class AssignContext {
    private String userId;
    private Long skillGroupId;     // 目标技能组（A组/B组…）
    private String specifyAgentId; // 指定 ID（可空）
    private int familiarValidDays;
}
```

### 5.2 三个策略实现

```java
@Component
@RequiredArgsConstructor
public class RoundRobinStrategy implements AssignmentStrategy {   // 平均分配
    public StrategyType type() { return StrategyType.ROUND_ROBIN; }

    public Optional<CsAccount> assign(AssignContext ctx) {
        // 候选 = 在线 ∩ 技能组匹配 ∩ 负载<上限
        Set<String> online = redis.opsForSet().members("cs:agent:online");
        List<CsAccount> candidates = accountMapper
                .selectAgents(ctx.getSkillGroupId()).stream()
                .filter(a -> online.contains(a.getEasemobId()))
                .filter(a -> currentLoad(a.getEasemobId()) < a.getMaxSessions())
                .toList();
        if (candidates.isEmpty()) return Optional.empty();
        // 负载最低优先；负载相同取最久未派单者（cs:agent:load + cs:agent:lastAssign）
        return candidates.stream().min(Comparator
                .comparingInt((CsAccount a) -> currentLoad(a.getEasemobId()))
                .thenComparing(a -> lastAssignTime(a.getEasemobId())));
    }
}

@Component
@RequiredArgsConstructor
public class FamiliarFirstStrategy implements AssignmentStrategy {  // 熟客优先
    public StrategyType type() { return StrategyType.FAMILIAR_FIRST; }
    public Optional<CsAccount> assign(AssignContext ctx) {
        Optional<CsAccount> familiar =
                accountService.findFamiliarAgent(ctx.getUserId(), ctx.getFamiliarValidDays());
        if (familiar.isPresent() && isAssignable(familiar.get())) return familiar;
        return roundRobin.assign(ctx);        // 降级平均
    }
}

@Component
public class SpecifyStrategy implements AssignmentStrategy {        // 指定 ID
    public StrategyType type() { return StrategyType.SPECIFY; }
    public Optional<CsAccount> assign(AssignContext ctx) {
        if (!StringUtils.hasText(ctx.getSpecifyAgentId())) return Optional.empty();
        return accountService.findAgent(ctx.getSpecifyAgentId())
                .filter(this::isAssignable);
    }
}
```

### 5.3 分配门面 + 转人工主流程（步骤⑦⑧⑨）

```java
@Service
@RequiredArgsConstructor
public class AssignmentService {
    private final Map<StrategyType, AssignmentStrategy> strategies; // Spring 注入聚合
    private final SessionService sessionService;
    private final EasemobGroupClient groupClient;
    private final EasemobMessageClient messageClient;
    private final RedissonClient redisson;

    @Transactional
    public void transferToHuman(CsSession session, String reason) {
        RLock lock = redisson.getLock("cs:lock:assign:" + session.getUserAccountId());
        if (!lock.tryLock()) return;                    // 防并发重复转人工
        try {
            if (!SessionStatus.AI_SERVING.name().equals(session.getStatus())) return;
            // ⑦⑧：构建上下文，按配置选择策略链（先指定→熟客→平均）
            AssignContext ctx = buildContext(session);
            Optional<CsAccount> agent = strategyChain().stream()
                    .map(s -> s.assign(ctx))
                    .filter(Optional::isPresent).map(Optional::get)
                    .findFirst();
            if (agent.isEmpty()) {                      // 无可用坐席 → 排队
                waitQueueService.enqueue(session);
                messageClient.sendGroupText(session.getAiAccountId(),
                        session.getGroupId(), "正在为您排队，当前第 "
                        + waitQueueService.position(session) + " 位，请稍候～");
                return;
            }
            // ⑨：坐席入群，切换服务方
            groupClient.addMember(session.getGroupId(), agent.get().getEasemobId());
            sessionService.switchToHuman(session, agent.get(), reason);
            accountService.incrLoad(agent.get().getEasemobId());   // 负载 +1
            accountService.upsertFamiliar(session.getUserAccountId(),
                    agent.get().getEasemobId());                    // 写熟客关系
            messageClient.sendGroupText(agent.get().getEasemobId(),
                    session.getGroupId(),
                    "客服 " + agent.get().getNickname() + " 为您服务，请问有什么可以帮您？");
        } finally { lock.unlock(); }
    }
}
```

---

## 6. AI 编排（customer-ai）

### 6.1 厂家适配 SPI

```java
public interface AiProviderAdapter {
    String providerCode();                       // OPENAI / DIFY / SELF_NLP ...
    AiAnswer chat(AiChatRequest req, CsAiProvider cfg);
}

public record AiChatRequest(String sessionId, String userId,
                            String question, List<AiMessage> history) {}
public record AiAnswer(String answer, double confidence,
                       boolean transfer, List<String> suggests) {}

@Component
public class DifyAdapter implements AiProviderAdapter {
    public String providerCode() { return "DIFY"; }
    public AiAnswer chat(AiChatRequest req, CsAiProvider cfg) {
        // OkHttp 调用 cfg.getBaseUrl()，带 appKey 鉴权、超时、重试
        // 解析厂家响应 → 统一 AiAnswer
    }
}

@Component
public class OpenAiAdapter implements AiProviderAdapter {
    public String providerCode() { return "OPENAI"; }
    // OpenAI 兼容协议（POST {baseUrl}/chat/completions），适用于 OpenAI 官方及
    // DeepSeek / Moonshot / 阿里云百炼兼容模式 等；模型与系统提示词配置在
    // cs_ai_provider.extra_json：{"model":"qwen-plus","systemPrompt":"..."}
    // 历史消息角色映射：user→user，ai/agent→assistant，system→system
}
```

### 6.2 AI 应答主流程（步骤④⑤⑥）

```java
@Service
@RequiredArgsConstructor
public class AiChatService {
    private final AiRouteService routeService;            // 按权重选厂家 + failover
    private final EasemobMessageClient messageClient;
    private final MessageService messageService;
    private final AssignmentService assignmentService;
    private final TransferJudge transferJudge;            // 词表读 cs_transfer_word 表
    private final StringRedisTemplate redis;

    /** 用户消息编排入口（环信回调与 /open/message/record 共用） */
    @Async("aiExecutor")
    public void handleUserMessage(CsSession session, String question) {
        if (!SessionStatus.AI_SERVING.name().equals(session.getStatus())) return;
        // 去重：双通道 10s 内同会话同内容只处理一次
        String key = "cs:msg:ai:dedup:" + session.getId() + ":" + md5(question);
        if (!setIfAbsent(key, 10, SECONDS)) return;
        if (transferJudge.hitTransferWords(question)) {    // 转人工词表
            assignmentService.transferToHuman(session, "KEYWORD");
            return;
        }
        reply(session, question);
    }

    @Async("aiExecutor")                                  // 与回调线程池隔离
    public void reply(CsSession session, String question) {
        List<AiMessage> history = messageService.recentHistory(session.getId(), 10);
        AiAnswer answer = routeService.chatWithFailover(
                new AiChatRequest(session.getId().toString(),
                        session.getUserAccountId(), question, history));
        if (answer.transfer()) {                          // AI 主动转人工
            assignmentService.transferToHuman(session, "AI_TRANSFER");
            return;
        }
        if (StringUtils.hasText(answer.answer())) {
            messageClient.sendGroupText(session.getAiAccountId(),   // ⑥
                    session.getGroupId(), answer.answer());
            messageService.saveAiMessage(session, answer);
            session.resetFailCount();
        } else {
            handleAiFailure(session);                     // 连续失败 N 次 → 转人工
        }
    }
}
```

### 6.3 厂家路由与故障转移

```java
@Service
public class AiRouteService {
    private final List<AiProviderAdapter> adapterList;   // 启动时 @PostConstruct 按 providerCode() 建索引
    private Map<String, AiProviderAdapter> adapters;

    /** 连通性测试：按厂商直连，不走权重路由（AI 管理-连通性测试用） */
    public String chatWith(CsAiProvider cfg, AiChatRequest req) {
        return adapters.get(cfg.getProviderCode()).chat(req, cfg);
    }

    public AiAnswer chatWithFailover(AiChatRequest req) {
        List<CsAiProvider> chain = loadEnabledByWeight();   // 主→备
        for (CsAiProvider cfg : chain) {
            try {
                return adapters.get(cfg.getProviderCode()).chat(req, cfg);
            } catch (Exception e) {
                log.warn("AI provider {} failed, failover", cfg.getProviderCode(), e);
                metrics.incr("ai.failover", cfg.getProviderCode());
            }
        }
        return AiAnswer.empty();   // 全部失败 → 上层兜底
    }
}
```

---

## 7. 会话服务（customer-conversation）

### 7.1 发起会话（开放接口，对应「用户点击客服入口 → 创建群组会话」）

```java
@RestController
@RequestMapping("/open/session")
@RequiredArgsConstructor
public class OpenSessionController {
    private final SessionService sessionService;

    @PostMapping("/start")
    public R<StartSessionResp> start(@RequestBody @Valid StartSessionReq req) {
        return R.ok(sessionService.start(req));
    }
}
```

```java
@Transactional
public StartSessionResp start(StartSessionReq req) {
    // 1. 幂等：同一用户已有进行中会话则直接复用（断线重连）
    CsSession existing = sessionMapper.selectActiveByUser(req.getUserId());
    if (existing != null) return StartSessionResp.of(existing);

    // 2. ① 取 AI 账号
    CsAccount ai = accountService.pickAiAccount(req.getAiGroupIdOrDefault());

    // 3. 创建环信群组（用户 + AI）
    String groupId = groupClient.createGroup(req.getUserId(),
            List.of(ai.getEasemobId()), "cs-" + IdUtil.fastSimpleUUID());

    // 4. 落库会话，状态 AI_SERVING
    CsSession session = saveSession(req, ai, groupId);
    redis.opsForValue().set("cs:session:cur:" + req.getUserId(),
            String.valueOf(session.getId()));

    // 5. 欢迎语（AI 身份发送）
    messageClient.sendGroupText(ai.getEasemobId(), groupId, welcomeText());
    return StartSessionResp.of(session);   // 返回 groupId / 环信登录凭证等
}
```

### 7.2 结束会话 / 满意度评价

```java
@Transactional
public void close(Long sessionId, String by, String reason) {
    CsSession s = sessionMapper.selectById(sessionId);
    if (s == null || SessionStatus.CLOSED.name().equals(s.getStatus())) return;
    sessionMapper.updateStatus(sessionId, SessionStatus.CLOSED.name());
    if (HandlerType.HUMAN.name().equals(s.getCurrentHandlerType())) {
        accountService.decrLoad(s.getAgentAccountId());   // 释放坐席负载
    }
    redis.delete("cs:session:cur:" + s.getUserAccountId());
    // 结束语带 ext.bizType=CLOSED，访客端收到后切「会话已结束」态并禁言
    messageClient.sendGroupText(s.getAiAccountId(), s.getGroupId(),
            "本次会话已结束，感谢您的咨询。欢迎为本次服务评分～",
            Map.of("bizType", "CLOSED"));
    // 解散环信群组（关闭会话即群生命周期结束）；环信调用失败仅记日志，不影响关单
    try {
        groupClient.dismissGroup(s.getGroupId());
    } catch (Exception e) {
        log.warn("dismiss group {} failed", s.getGroupId(), e);
    }
}
```

### 7.3 定时任务

```java
@Component
@RequiredArgsConstructor
public class SessionTimeoutJob {
    /** 每分钟扫描：AI/人工会话 30 分钟无消息自动关闭 */
    @Scheduled(fixedDelay = 60_000)
    public void closeTimeoutSessions() { ... }
}

@Component
public class QueueConsumeJob {
    /** 坐席上线/释放容量事件 + 定时兜底：FIFO 补位排队会话 */
    @Scheduled(fixedDelay = 5_000)
    public void consumeWaitQueue() { ... }
}
```

---

## 8. 线程池与并发设计

```java
@Configuration
@EnableAsync
public class ExecutorConfig {
    @Bean("callbackExecutor")   // 环信回调：IO 密集，快速 ACK
    public ThreadPoolTaskExecutor callbackExecutor() {
        return build(8, 32, 500, "callback-");
    }
    @Bean("aiExecutor")         // AI 调用：长超时，防拖垮回调
    public ThreadPoolTaskExecutor aiExecutor() {
        return build(4, 16, 200, "ai-");
    }
}
```

| 并发点 | 方案 |
|--------|------|
| 消息回调重复推送 | `cs:msg:idem:{msgId}` SETNX 幂等 |
| 同一用户并发转人工 | Redisson 锁 `cs:lock:assign:{userId}` |
| 坐席负载增减 | Redis `ZINCRBY` 原子操作 |
| 会话重复创建 | `cs:session:cur:{userId}` + DB 唯一索引兜底 |

---

## 9. 配置与安全

- **application-prod.yml**：环信凭据、AI 厂家密钥一律走环境变量/Nacos，禁止入库明文（DB 内 AES 加密）；
- **回调验签**：校验环信回调签名/时间戳/IP 白名单（`CallbackAuthFilter`）【现状：未实现，回调仅按 msgId 幂等】；
- **开放接口**：访客端先调 `/open/auth/guest-token` 换取带 userId 的短期 JWT（Sa-Token），后续接口鉴权【现状：未实现，开放接口当前无鉴权】；
- **管理接口**：RBAC（管理员/组长/坐席），Sa-Token 拦截器 + 注解鉴权【现状：未实现，管理接口当前无鉴权】；
- **审计**：转接、改策略、改 AI 配置写操作日志表【现状：`cs_op_log` 未启用】。

## 10. 服务端自测清单

- [ ] 建群 → AI 首响 ≤ 3s；② 环信 SDK 消息互通
- [ ] 回调触发 AI 应答（④⑤⑥）闭环；AI 超时 failover 到备厂家
- [ ] 关键词「转人工」→ 分配成功入群（③⑦⑧⑨）；无坐席 → 排队话术正确
- [ ] 熟客优先命中 / 指定 ID 命中 / 平均分配负载均衡正确
- [ ] 会话结束释放坐席负载；超时自动关闭
- [ ] 重复回调、重复 start、并发转人工的幂等验证
