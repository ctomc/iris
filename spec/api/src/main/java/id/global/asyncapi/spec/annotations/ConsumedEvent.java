package id.global.asyncapi.spec.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import id.global.asyncapi.spec.enums.ExchangeType;
import id.global.asyncapi.spec.enums.Scope;

@Target({ ElementType.TYPE, ElementType.RECORD_COMPONENT })
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface ConsumedEvent {
    /**
     * On which queue to listen for these events. If not specified generated from event class name.
     */
    String queue() default "";

    /**
     * On which exchange to listen to for direct event messages. If not specified, default exchange is used.
     */
    String exchange() default "";

    ExchangeType exchangeType() default ExchangeType.DIRECT;

    /**
     * Bindings keys for topic messages. @see <a href="https://www.rabbitmq.com/tutorials/tutorial-five-python.html">Rabbitmq
     * Topics</a>
     */
    String[] bindingKeys() default {};

    /**
     * Wether the event is an internal system event or an external communication event
     */
    Scope scope() default Scope.INTERNAL;
}
