package com.pigeonmq.transport.mqtt;

import com.pigeonmq.config.BrokerConfig;
import com.pigeonmq.core.Broker;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MqttTransport {

    private static final Logger log = LoggerFactory.getLogger(MqttTransport.class);

    private final BrokerConfig config;
    private final Broker broker;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    public MqttTransport(BrokerConfig config, Broker broker) {
        this.config = config;
        this.broker = broker;
    }

    public void start() throws InterruptedException {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        ServerBootstrap bootstrap = new ServerBootstrap()
                .group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new MqttChannelInitializer(broker))
                .option(ChannelOption.SO_BACKLOG, 128)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childOption(ChannelOption.TCP_NODELAY, true);

        serverChannel = bootstrap.bind(config.getMqttPort()).sync().channel();
        log.info("MQTT server listening on port {}", config.getMqttPort());
    }

    public void stop() {
        if (serverChannel != null) serverChannel.close();
        if (workerGroup != null) workerGroup.shutdownGracefully();
        if (bossGroup != null) bossGroup.shutdownGracefully();
        log.info("MQTT server stopped");
    }

    public void awaitTermination() throws InterruptedException {
        if (serverChannel != null) serverChannel.closeFuture().sync();
    }
}
