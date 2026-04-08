package com.pigeonmq.transport.mqtt;

import com.pigeonmq.core.Broker;
import com.pigeonmq.core.ClientSession;
import com.pigeonmq.model.DestinationType;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.mqtt.*;
import io.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class MqttPacketHandler extends SimpleChannelInboundHandler<MqttMessage> {

    private static final Logger log = LoggerFactory.getLogger(MqttPacketHandler.class);

    private final Broker broker;
    private ClientSession session;

    public MqttPacketHandler(Broker broker) {
        this.broker = broker;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, MqttMessage msg) {
        if (msg.decoderResult().isFailure()) {
            log.warn("Bad MQTT packet, closing connection");
            ctx.close();
            return;
        }

        switch (msg.fixedHeader().messageType()) {
            case CONNECT     -> handleConnect(ctx, (MqttConnectMessage) msg);
            case PUBLISH     -> handlePublish(ctx, (MqttPublishMessage) msg);
            case PUBACK      -> handlePubAck(msg);
            case SUBSCRIBE   -> handleSubscribe(ctx, (MqttSubscribeMessage) msg);
            case UNSUBSCRIBE -> handleUnsubscribe(ctx, (MqttUnsubscribeMessage) msg);
            case PINGREQ     -> handlePingReq(ctx);
            case DISCONNECT  -> handleDisconnect(ctx);
            default -> log.debug("Unhandled MQTT packet type: {}", msg.fixedHeader().messageType());
        }
    }

    /* ═══════════════ CONNECT ═══════════════ */

    private void handleConnect(ChannelHandlerContext ctx, MqttConnectMessage msg) {
        String clientId = msg.payload().clientIdentifier();
        if (clientId == null || clientId.isBlank()) {
            clientId = "anon-" + ctx.channel().id().asShortText();
        }

        session = broker.registerClient(clientId, ctx.channel());

        MqttConnAckMessage connAck = MqttMessageBuilders.connAck()
                .returnCode(MqttConnectReturnCode.CONNECTION_ACCEPTED)
                .sessionPresent(false)
                .build();
        ctx.writeAndFlush(connAck);
        log.debug("CONNACK → {}", clientId);
    }

    /* ═══════════════ PUBLISH (from publisher) ═══════════════ */

    private void handlePublish(ChannelHandlerContext ctx, MqttPublishMessage msg) {
        requireSession(ctx);
        if (session == null) return;

        String topicName = msg.variableHeader().topicName();
        int packetId = msg.variableHeader().packetId();
        MqttQoS qos = msg.fixedHeader().qosLevel();

        ByteBuf payloadBuf = msg.payload();
        byte[] payload = new byte[payloadBuf.readableBytes()];
        payloadBuf.readBytes(payload);

        broker.publish(topicName, payload, null, 0);

        if (qos == MqttQoS.AT_LEAST_ONCE && packetId > 0) {
            MqttMessage pubAck = MqttMessageBuilders.pubAck()
                    .packetId(packetId)
                    .build();
            ctx.writeAndFlush(pubAck);
            log.debug("PUBACK → {} (pktId={})", session.getClientId(), packetId);
        }
    }

    /* ═══════════════ PUBACK (from subscriber, delivery ACK) ═══════════════ */

    private void handlePubAck(MqttMessage msg) {
        if (session == null) return;
        int packetId = ((MqttMessageIdVariableHeader) msg.variableHeader()).messageId();
        broker.acknowledgeDelivery(session, packetId);
    }

    /* ═══════════════ SUBSCRIBE ═══════════════ */

    private void handleSubscribe(ChannelHandlerContext ctx, MqttSubscribeMessage msg) {
        requireSession(ctx);
        if (session == null) return;

        int packetId = msg.variableHeader().messageId();
        List<MqttTopicSubscription> subs = msg.payload().topicSubscriptions();
        List<Integer> grantedQos = new ArrayList<>();

        for (MqttTopicSubscription sub : subs) {
            String filter = sub.topicName();

            if (filter.startsWith("$share/")) {
                String[] parts = filter.split("/", 3);
                if (parts.length == 3) {
                    broker.subscribe(session, parts[2], DestinationType.QUEUE);
                    grantedQos.add(MqttQoS.AT_LEAST_ONCE.value());
                } else {
                    grantedQos.add(MqttQoS.FAILURE.value());
                }
            } else {
                broker.subscribe(session, filter, DestinationType.TOPIC);
                grantedQos.add(MqttQoS.AT_LEAST_ONCE.value());
            }
        }

        MqttFixedHeader fixedHeader = new MqttFixedHeader(
                MqttMessageType.SUBACK, false, MqttQoS.AT_MOST_ONCE, false, 0);
        MqttMessageIdVariableHeader varHeader = MqttMessageIdVariableHeader.from(packetId);
        MqttSubAckPayload payload = new MqttSubAckPayload(grantedQos);
        ctx.writeAndFlush(new MqttSubAckMessage(fixedHeader, varHeader, payload));
        log.debug("SUBACK → {} (pktId={}, subs={})", session.getClientId(), packetId, subs.size());
    }

    /* ═══════════════ UNSUBSCRIBE ═══════════════ */

    private void handleUnsubscribe(ChannelHandlerContext ctx, MqttUnsubscribeMessage msg) {
        requireSession(ctx);
        if (session == null) return;

        int packetId = msg.variableHeader().messageId();
        for (String filter : msg.payload().topics()) {
            if (filter.startsWith("$share/")) {
                String[] parts = filter.split("/", 3);
                if (parts.length == 3) session.unsubscribeQueue(parts[2]);
            } else {
                session.unsubscribeTopic(filter);
            }
        }

        MqttFixedHeader fixedHeader = new MqttFixedHeader(
                MqttMessageType.UNSUBACK, false, MqttQoS.AT_MOST_ONCE, false, 0);
        ctx.writeAndFlush(new MqttMessage(fixedHeader, MqttMessageIdVariableHeader.from(packetId)));
        log.debug("UNSUBACK → {} (pktId={})", session.getClientId(), packetId);
    }

    /* ═══════════════ PING ═══════════════ */

    private void handlePingReq(ChannelHandlerContext ctx) {
        MqttFixedHeader fixedHeader = new MqttFixedHeader(
                MqttMessageType.PINGRESP, false, MqttQoS.AT_MOST_ONCE, false, 0);
        ctx.writeAndFlush(new MqttMessage(fixedHeader));
    }

    /* ═══════════════ DISCONNECT ═══════════════ */

    private void handleDisconnect(ChannelHandlerContext ctx) {
        if (session != null) {
            log.debug("DISCONNECT ← {}", session.getClientId());
            broker.removeClient(session.getClientId());
            session = null;
        }
        ctx.close();
    }

    /* ═══════════════ Lifecycle ═══════════════ */

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (session != null) {
            broker.removeClient(session.getClientId());
            session = null;
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof IdleStateEvent) {
            log.debug("Idle timeout, closing {}", session != null ? session.getClientId() : ctx.channel());
            ctx.close();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("Channel error ({}): {}", session != null ? session.getClientId() : "unknown", cause.getMessage());
        ctx.close();
    }

    private void requireSession(ChannelHandlerContext ctx) {
        if (session == null) {
            log.warn("Packet before CONNECT, closing");
            ctx.close();
        }
    }
}
