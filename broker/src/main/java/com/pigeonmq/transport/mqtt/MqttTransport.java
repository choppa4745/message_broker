package com.pigeonmq.transport.mqtt;

import com.pigeonmq.config.BrokerProperties;
import com.pigeonmq.service.BrokerFacade;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

@Component
public class MqttTransport implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(MqttTransport.class);
    private static final int BACKLOG = 128;

    private final BrokerProperties properties;
    private final BrokerFacade brokerFacade;

    private volatile boolean running;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    public MqttTransport(BrokerProperties properties, BrokerFacade brokerFacade) {
        this.properties = properties;
        this.brokerFacade = brokerFacade;
    }

    @Override
    public void start() {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap bootstrap = new ServerBootstrap()
                    .group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new MqttChannelInitializer(brokerFacade))
                    .option(ChannelOption.SO_BACKLOG, BACKLOG)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childOption(ChannelOption.TCP_NODELAY, true);

            serverChannel = bootstrap.bind(properties.getMqttPort()).sync().channel();
            running = true;
            log.info("MQTT transport listening on port {}", properties.getMqttPort());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("MQTT transport failed to start", e);
        }
    }

    @Override
    public void stop() {
        running = false;
        if (serverChannel != null) {
            serverChannel.close().syncUninterruptibly();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        log.info("MQTT transport stopped");
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }
}
