package com.pigeonmq.transport.mqtt;

import com.pigeonmq.service.BrokerFacade;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.mqtt.MqttDecoder;
import io.netty.handler.codec.mqtt.MqttEncoder;
import io.netty.handler.timeout.IdleStateHandler;

import java.util.concurrent.TimeUnit;

public class MqttChannelInitializer extends ChannelInitializer<SocketChannel> {

    private static final int MAX_PAYLOAD_BYTES = 1024 * 1024;
    private static final int IDLE_TIMEOUT_SECONDS = 120;

    private final BrokerFacade brokerFacade;

    public MqttChannelInitializer(BrokerFacade brokerFacade) {
        this.brokerFacade = brokerFacade;
    }

    @Override
    protected void initChannel(SocketChannel ch) {
        ch.pipeline()
                .addLast("idleState", new IdleStateHandler(IDLE_TIMEOUT_SECONDS, 0, 0, TimeUnit.SECONDS))
                .addLast("mqttDecoder", new MqttDecoder(MAX_PAYLOAD_BYTES))
                .addLast("mqttEncoder", MqttEncoder.INSTANCE)
                .addLast("handler", new MqttPacketHandler(brokerFacade));
    }
}
