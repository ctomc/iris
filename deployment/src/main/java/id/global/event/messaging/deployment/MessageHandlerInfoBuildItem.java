package id.global.event.messaging.deployment;

import java.util.List;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.Type;

import id.global.common.annotations.amqp.ExchangeType;
import id.global.common.annotations.amqp.Scope;
import io.quarkus.builder.item.MultiBuildItem;

public final class MessageHandlerInfoBuildItem extends MultiBuildItem {
    private final ClassInfo declaringClass;
    private final Type parameterType;
    private final Type returnType;
    private final String methodName;
    private final String name;
    private final ExchangeType exchangeType;
    private final List<String> bindingKeys;
    private final Scope scope;
    private final boolean durable;
    private final boolean autoDelete;
    private final boolean queuePerInstance;
    private final int prefetchCount;
    private final long ttl;
    private final String deadLetterQueue;
    private final String eventName;

    public MessageHandlerInfoBuildItem(ClassInfo declaringClass,
            Type parameterType,
            Type returnType,
            String methodName,
            String name,
            ExchangeType exchangeType,
            List<String> bindingKeys,
            Scope scope,
            boolean durable,
            boolean autoDelete,
            boolean queuePerInstance,
            int prefetchCount,
            long ttl,
            String deadLetterQueue, final String eventName) {
        this.declaringClass = declaringClass;
        this.parameterType = parameterType;
        this.returnType = returnType;
        this.methodName = methodName;
        this.name = name;
        this.exchangeType = exchangeType;
        this.bindingKeys = bindingKeys;
        this.scope = scope;
        this.durable = durable;
        this.autoDelete = autoDelete;
        this.queuePerInstance = queuePerInstance;
        this.prefetchCount = prefetchCount;
        this.ttl = ttl;
        this.deadLetterQueue = deadLetterQueue;
        this.eventName = eventName;
    }

    public ClassInfo getDeclaringClass() {
        return declaringClass;
    }

    public Type getParameterType() {
        return parameterType;
    }

    public Type getReturnType() {
        return returnType;
    }

    public String getName() {
        return name;
    }

    public String getMethodName() {
        return methodName;
    }

    public ExchangeType getExchangeType() {
        return exchangeType;
    }

    public List<String> getBindingKeys() {
        return bindingKeys;
    }

    public Scope getScope() {
        return scope;
    }

    public boolean isDurable() {
        return durable;
    }

    public boolean isAutoDelete() {
        return autoDelete;
    }

    public boolean isQueuePerInstance() {
        return queuePerInstance;
    }

    public int getPrefetchCount() {
        return prefetchCount;
    }

    public long getTtl() {
        return ttl;
    }

    public String getDeadLetterQueue() {
        return deadLetterQueue;
    }

    public String getEventName() {
        return eventName;
    }

    @Override
    public String toString() {
        return "MessageHandlerInfoBuildItem{" +
                "declaringClass=" + declaringClass +
                ", parameterType=" + parameterType +
                ", returnType=" + returnType +
                ", methodName='" + methodName + '\'' +
                ", exchange='" + name + '\'' +
                ", exchangeType=" + exchangeType +
                ", bindingKeys=" + String.join(",", bindingKeys) +
                ", scope=" + scope +
                ", durable=" + durable +
                ", autoDelete=" + autoDelete +
                ", queuePerInstance=" + queuePerInstance +
                ", prefetchCount=" + prefetchCount +
                ", ttl=" + ttl +
                ", deadLetterQueue='" + deadLetterQueue + '\'' +
                ", eventName='" + eventName + '\'' +
                '}';
    }

}
