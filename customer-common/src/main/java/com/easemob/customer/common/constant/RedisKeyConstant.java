package com.easemob.customer.common.constant;

public final class RedisKeyConstant {

    private RedisKeyConstant() {
    }

    public static final String EASEMOB_TOKEN = "cs:token:easemob";
    public static final String AGENT_ONLINE = "cs:agent:online";
    public static final String AGENT_LOAD = "cs:agent:load";
    public static final String AGENT_LAST_ASSIGN = "cs:agent:lastAssign";

    public static String queueKey(Long skillGroupId) {
        return "cs:queue:" + skillGroupId;
    }

    public static String currentSessionKey(String userId) {
        return "cs:session:cur:" + userId;
    }

    public static String msgIdemKey(String msgId) {
        return "cs:msg:idem:" + msgId;
    }

    public static String assignLockKey(String userId) {
        return "cs:lock:assign:" + userId;
    }

    public static String aiRoundRobinKey(Long aiGroupId) {
        return "cs:ai:rr:" + aiGroupId;
    }

    public static String strategyConfigKey() {
        return "cs:config:strategy";
    }
}
