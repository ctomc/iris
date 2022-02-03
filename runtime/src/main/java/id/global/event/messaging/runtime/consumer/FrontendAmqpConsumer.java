package id.global.event.messaging.runtime.consumer;

import static id.global.common.headers.amqp.MessagingHeaders.QueueDeclaration.X_DEAD_LETTER_EXCHANGE;
import static id.global.common.headers.amqp.MessagingHeaders.QueueDeclaration.X_DEAD_LETTER_ROUTING_KEY;

import java.io.IOException;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.inject.Named;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.CancelCallback;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ConsumerShutdownSignalCallback;
import com.rabbitmq.client.DeliverCallback;

import id.global.event.messaging.runtime.InstanceInfoProvider;
import id.global.event.messaging.runtime.channel.ChannelService;
import id.global.event.messaging.runtime.exception.AmqpConnectionException;

@ApplicationScoped
public class FrontendAmqpConsumer {
    private static final Logger log = LoggerFactory.getLogger(FrontendAmqpConsumer.class);

    private final static String FRONTEND_EXCHANGE = "frontend";
    private final static String FRONTEND_QUEUE_POSTFIX = "frontend";

    private final ChannelService channelService;
    private final InstanceInfoProvider instanceInfoProvider;
    private final ConcurrentHashMap<String, DeliverCallbackProvider> deliverCallbackProviderMap;
    private final ConcurrentHashMap<String, DeliverCallback> deliverCallbackMap;

    private String channelId;

    @Inject
    public FrontendAmqpConsumer(
            @Named("consumerChannelService") final ChannelService channelService,
            final InstanceInfoProvider instanceInfoProvider) {
        this.channelService = channelService;
        this.instanceInfoProvider = instanceInfoProvider;
        this.deliverCallbackMap = new ConcurrentHashMap<>();
        this.deliverCallbackProviderMap = new ConcurrentHashMap<>();
        this.channelId = UUID.randomUUID().toString();
    }

    public void addDeliverCallbackProvider(String routingKey, DeliverCallbackProvider deliverCallbackProvider) {
        deliverCallbackProviderMap.put(routingKey, deliverCallbackProvider);
    }

    public void initChannel() {
        try {
            Channel channel = this.channelService.getOrCreateChannelById(this.channelId);
            channel.exchangeDeclare(FRONTEND_EXCHANGE, BuiltinExchangeType.TOPIC, false);
            String frontendQueue = getFrontendQueue();
            final var queueDeclarationArgs = new HashMap<String, Object>();
            queueDeclarationArgs.put(X_DEAD_LETTER_ROUTING_KEY, "dead." + frontendQueue);
            queueDeclarationArgs.put(X_DEAD_LETTER_EXCHANGE, "dead." + FRONTEND_EXCHANGE);
            channel.queueDeclare(frontendQueue, true, false, false, queueDeclarationArgs);

            setupDeliverCallbacks(channel);

            channel.basicConsume(frontendQueue, false, getDeliverCallback(), getCancelCallback(), getShutdownCallback());
        } catch (IOException e) {
            String msg = "Could not initialize frontend consumer";
            log.error(msg, e);
            throw new AmqpConnectionException(msg, e);
        }

    }

    private void setupDeliverCallbacks(Channel channel) {
        deliverCallbackProviderMap.forEach((routingKey, callbackProvider) -> {
            try {
                channel.queueBind(getFrontendQueue(), FRONTEND_EXCHANGE, routingKey);
                deliverCallbackMap.put(routingKey, callbackProvider.createDeliverCallback(channel));
            } catch (IOException e) {
                String msg = String.format("Could not setup deliver callback for routing key = %s", routingKey);
                log.error(msg);
                throw new AmqpConnectionException(msg, e);
            }
        });
    }

    private DeliverCallback getDeliverCallback() {
        return (consumerTag, message) -> {
            String msgRoutingKey = message.getEnvelope().getRoutingKey();

            DeliverCallback deliverCallback = deliverCallbackMap.get(msgRoutingKey);
            if (deliverCallback == null) {
                log.warn(String.format("No handler registered for frontend message with routingKey = %s, NACK-ing message",
                        msgRoutingKey));
                channelService.getOrCreateChannelById(channelId)
                        .basicNack(message.getEnvelope().getDeliveryTag(), false, false);
            } else {
                deliverCallback.handle(consumerTag, message);
            }
        };
    }

    private CancelCallback getCancelCallback() {
        return consumerTag -> log.warn("Channel canceled for {}",
                instanceInfoProvider.getApplicationName() + " frontend queue");
    }

    private ConsumerShutdownSignalCallback getShutdownCallback() {
        return (consumerTag, sig) -> {
            log.warn("Channel shut down for with signal:{}, queue: {}, consumer: {}", sig, getFrontendQueue(), consumerTag);
            try {
                channelService.removeChannel(channelId);
                channelId = UUID.randomUUID().toString();
                initChannel();
            } catch (IOException e) {
                log.error(String.format("Could not re-initialize channel for queue %s", getFrontendQueue()), e);
            }
        };
    }

    private String getFrontendQueue() {
        return String.format("%s.%s", this.instanceInfoProvider.getApplicationName(), FRONTEND_QUEUE_POSTFIX);
    }
}