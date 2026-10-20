package com.easemob.customer.admin.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.easemob.customer.account.entity.CsAccount;
import com.easemob.customer.account.service.AccountService;
import com.easemob.customer.assignment.queue.WaitQueueService;
import com.easemob.customer.common.enums.MsgSenderType;
import com.easemob.customer.common.enums.MsgType;
import com.easemob.customer.common.exception.BizException;
import com.easemob.customer.common.result.PageResult;
import com.easemob.customer.common.result.R;
import com.easemob.customer.conversation.entity.CsMessage;
import com.easemob.customer.conversation.entity.CsSession;
import com.easemob.customer.conversation.mapper.MessageMapper;
import com.easemob.customer.conversation.mapper.SessionMapper;
import com.easemob.customer.conversation.service.SessionService;
import com.easemob.customer.easemob.client.EasemobMessageClient;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/admin/workbench")
@RequiredArgsConstructor
public class WorkbenchController {

    private final AccountService accountService;
    private final SessionService sessionService;
    private final SessionMapper sessionMapper;
    private final MessageMapper messageMapper;
    private final EasemobMessageClient messageClient;
    private final WaitQueueService waitQueueService;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    @PostMapping("/online")
    public R<Void> online() {
        String agentId = currentUserId();
        accountService.agentOnline(agentId);
        return R.ok();
    }

    @PostMapping("/offline")
    public R<Void> offline() {
        String agentId = currentUserId();
        accountService.agentOffline(agentId);
        return R.ok();
    }

    @GetMapping("/sessions")
    public R<PageResult<Map<String, Object>>> sessions(@RequestParam(required = false) String status,
                                                        @RequestParam(defaultValue = "1") int pageNum,
                                                        @RequestParam(defaultValue = "20") int pageSize) {
        String agentId = currentUserId();
        List<CsSession> list = sessionMapper.selectAgentSessions(agentId);
        List<Map<String, Object>> data = list.stream().map(this::sessionMap).toList();
        return R.ok(new PageResult<>(data.size(), pageNum, pageSize, data));
    }

    @GetMapping("/queue")
    public R<List<Map<String, Object>>> queue() {
        List<CsSession> list = sessionMapper.selectWaitingSessions();
        List<Map<String, Object>> data = list.stream().map(this::sessionMap).toList();
        return R.ok(data);
    }

    @PostMapping("/claim")
    public R<Void> claim(@RequestBody Map<String, Object> body) {
        String agentId = currentUserId();
        Long sessionId = Long.valueOf(body.getOrDefault("sessionId", "0").toString());
        CsSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BizException(4003, "会话不存在");
        }
        Optional<CsAccount> agentOpt = accountService.findAgent(agentId);
        if (agentOpt.isEmpty()) {
            throw new BizException(4004, "客服账号不存在");
        }
        sessionService.switchToHuman(session, agentOpt.get(), "AGENT_CLAIM");
        return R.ok();
    }

    @PostMapping("/send")
    public R<Void> send(@RequestBody Map<String, Object> body) {
        String agentId = currentUserId();
        Long sessionId = Long.valueOf(body.getOrDefault("sessionId", "0").toString());
        String content = body.getOrDefault("content", "").toString();
        CsSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BizException(4003, "会话不存在");
        }
        if (!agentId.equals(session.getAgentAccountId())) {
            throw new BizException(4003, "非当前会话客服");
        }
        messageClient.sendGroupText(agentId, session.getGroupId(), content);

        CsMessage message = new CsMessage();
        message.setSessionId(sessionId);
        message.setMsgId("agent-" + System.currentTimeMillis());
        message.setSenderId(agentId);
        message.setSenderType(MsgSenderType.AGENT.name());
        message.setMsgType(MsgType.TXT.name());
        message.setContent(content);
        messageMapper.insert(message);

        session.setLastMsgTime(java.time.LocalDateTime.now());
        sessionMapper.updateById(session);
        return R.ok();
    }

    @GetMapping("/messages/{sessionId}")
    public R<List<Map<String, Object>>> messages(@PathVariable Long sessionId,
                                                 @RequestParam(defaultValue = "50") int limit) {
        List<CsMessage> list = messageMapper.selectRecentBySession(sessionId, limit);
        List<Map<String, Object>> data = new ArrayList<>(list.size());
        for (int i = list.size() - 1; i >= 0; i--) {
            CsMessage m = list.get(i);
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", m.getId());
            map.put("msgId", m.getMsgId());
            map.put("senderType", m.getSenderType());
            map.put("senderId", m.getSenderId());
            map.put("content", m.getContent());
            map.put("createTime", m.getCreateTime() != null ? m.getCreateTime().format(TIME_FMT) : "");
            data.add(map);
        }
        return R.ok(data);
    }

    @PostMapping("/transfer")
    public R<Void> transfer(@RequestBody Map<String, Object> body) {
        Long sessionId = Long.valueOf(body.getOrDefault("sessionId", "0").toString());
        String agentId = currentUserId();
        CsSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BizException(4003, "会话不存在");
        }
        if (!agentId.equals(session.getAgentAccountId())) {
            throw new BizException(4003, "非当前会话客服");
        }
        sessionService.close(sessionId, "AGENT", body.getOrDefault("reason", "AGENT_TRANSFER").toString());
        return R.ok();
    }

    @PostMapping("/close")
    public R<Void> close(@RequestBody Map<String, Object> body) {
        Long sessionId = Long.valueOf(body.getOrDefault("sessionId", "0").toString());
        sessionService.close(sessionId, "AGENT", body.getOrDefault("reason", "AGENT_CLOSE").toString());
        return R.ok();
    }

    @GetMapping("/user/{userId}")
    public R<Map<String, Object>> userProfile(@PathVariable String userId) {
        return R.ok(Collections.emptyMap());
    }

    private String currentUserId() {
        Object loginId = StpUtil.getLoginId();
        return loginId != null ? loginId.toString() : "";
    }

    private Map<String, Object> sessionMap(CsSession s) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", s.getId());
        map.put("userAccountId", s.getUserAccountId());
        map.put("groupId", s.getGroupId());
        map.put("aiAccountId", s.getAiAccountId());
        map.put("status", s.getStatus());
        map.put("handlerType", s.getCurrentHandlerType());
        map.put("agentAccountId", s.getAgentAccountId());
        map.put("lastMsgTime", s.getLastMsgTime() != null ? s.getLastMsgTime().format(TIME_FMT) : "");
        return map;
    }
}
