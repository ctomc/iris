package id.global.iris.messaging.runtime.api.message;

import id.global.common.annotations.amqp.ExchangeType;
import id.global.common.annotations.amqp.Message;
import id.global.common.annotations.amqp.Scope;

@Message(name = "resource-update", exchangeType = ExchangeType.TOPIC)
public record ResourceUpdate(String resourceType, String resourceId, Scope scope, Object payload) {
}