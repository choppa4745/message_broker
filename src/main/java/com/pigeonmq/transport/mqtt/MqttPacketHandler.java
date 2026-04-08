package com.pigeonmq.transport.mqtt;

import com.pigeonmq.domain.ClientSession;
import com.pigeonmq.service.BrokerFacade;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.mqtt.*;
import io.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class MqttPacketHandler extends SimpleChannelInboundHandler<MqttMessage> {

    private static final Logger log = LoggerFactory.getLogger(MqttPacketHandler.class);

    private final BrokerFacade brokerFacade;
    private ClientSession session;

    public MqttPacketHandler(BrokerFacade brokerFacade) {
        this.brokerFacade = brokerFacade;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, MqttMessage msg) {
        if (msg.decoderResult().isFailure()) {
            log.warn("Malformed MQTT packet from {}", ctx.channel().remoteAddress());
            ctx.close();
            return;
        }

        switch (msg.fixedHeader().messageType()) {
            case CONNECT     -> handleConnect(ctx, (MqttConnectMessage) msg);
            case PUBLISH     -> handlePublish(ctx, (MqttPublishMessage) msg);
            case PUBACK      -> handlePuback(msg);
            case SUBSCRIBE   -> handleSubscribe(ctx, (MqttSubscribeMessage) msg);
            case UNSUBSCRIBE -> handleUnsubscribe(ctx, (MqttUnsubscribeMessage) msg);
            case PINGREQ     -> handlePing(ctx);
            case DISCONNECT  -> handleDisconnect(ctx);
            default          -> log.debug("Unhandled MQTT type: {}", msg.fixedHeader().messageType());
        }
    }

    private void handleConnect(ChannelHandlerContext ctx, MqttConnectMessage msg) {
        String clientId = msg.payload().clientIdentifier();
        if (clientId == null || clientId.isBlank()) {
            clientId = "anon-" + ctx.channel().id().asShortText();
        }

        session = brokerFacade.registerClient(clientId, ctx.channel());

        MqttConnAckMessage ack = MqttMessageBuilders.connAck()
                .returnCode(MqttConnectReturnCode.CONNECTION_ACCEPTED)
                .sessionPresent(false)
                .build();
        ctx.writeAndFlush(ack);
    }

    private void handlePublish(ChannelHandlerContext ctx, MqttPublishMessage msg) {
        if (!ensureSession(ctx)) return;

        String destination = msg.variableHeader().topicName();
        byte[] payload = new byte[msg.payload().readableBytes()];
        msg.payload().readBytes(payload);

        brokerFacade.publish(destination, payload, 0);

        if (msg.fixedHeader().qosLevel() == MqttQoS.AT_LEAST_ONCE) {
            MqttMessage ack = MqttMessageBuilders.pubAck()
                    .packetId(msg.variableHeader().packetId())
                    .build();
            ctx.writeAndFlush(ack);
        }
    }

    private void handlePuback(MqttMessage msg) {
        if (session == null) return;
        MqttMessageIdVariableHeader header = (MqttMessageIdVariableHeader) msg.variableHeader();
        brokerFacade.acknowledgeDelivery(session, header.messageId());
    }

    private void handleSubscribe(ChannelHandlerContext ctx, MqttSubscribeMessage msg) {
        if (!ensureSession(ctx)) return;

        List<MqttTopicSubscription> subs = msg.payload().topicSubscriptions();
        for (MqttTopicSubscription sub : subs) {
            brokerFacade.subscribe(session, sub.topicName());
        }

        MqttSubAckMessage subAck = MqttMessageBuilders.subAck()
                .packetId(msg.variableHeader().messageId())
                .addGrantedQoses(subs.stream()
                        .map(s -> MqttQoS.AT_LEAST_ONCE)
                        .toArray(MqttQoS[]::new))
                .build();
        ctx.writeAndFlush(subAck);
    }

    private void handleUnsubscribe(ChannelHandlerContext ctx, MqttUnsubscribeMessage msg) {
        if (!ensureSession(ctx)) return;

        for (String topicFilter : msg.payload().topics()) {
            brokerFacade.unsubscribe(session, topicFilter);
        }

        MqttUnsubAckMessage unsubAck = MqttMessageBuilders.unsubAck()
                .packetId(msg.variableHeader().messageId())
                .build();
        ctx.writeAndFlush(unsubAck);
    }

    private void handlePing(ChannelHandlerContext ctx) {
        MqttFixedHeader fixedHeader = new MqttFixedHeader(
                MqttMessageType.PINGRESP, false, MqttQoS.AT_MOST_ONCE, false, 0);
        ctx.writeAndFlush(new MqttMessage(fixedHeader));
    }

    private void handleDisconnect(ChannelHandlerContext ctx) {
        brokerFacade.removeClient(ctx.channel());
        ctx.close();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        brokerFacade.removeClient(ctx.channel());
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof IdleStateEvent) {
            ctx.close();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("Channel error [{}]: {}", ctx.channel().remoteAddress(), cause.getMessage());
        ctx.close();
    }

    private boolean ensureSession(ChannelHandlerContext ctx) {
        if (session != null) {
            return true;
        }
        log.warn("Packet before CONNECT from {}", ctx.channel().remoteAddress());
        ctx.close();
        return false;
    }
}
