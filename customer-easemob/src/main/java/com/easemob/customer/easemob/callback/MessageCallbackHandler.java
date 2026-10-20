package com.easemob.customer.easemob.callback;

/**
 * 环信消息回调处理器。由上层模块实现（编排 AI 应答/转人工），
 * customer-easemob 只负责接收回调并分发给所有处理器，避免模块间循环依赖。
 */
public interface MessageCallbackHandler {

    void handle(CallbackPayload payload);
}
