package com.pigeonmq.sdk;

/**
 * Callback invoked for every message delivered to a subscription.
 * All invocations are dispatched on the callback executor configured in
 * {@link PigeonClientBuilder#callbackExecutor(java.util.concurrent.Executor)}.
 */
@FunctionalInterface
public interface MessageHandler {

    void onMessage(PigeonMessage message);
}
