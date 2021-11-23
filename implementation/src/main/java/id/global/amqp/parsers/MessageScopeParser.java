package id.global.amqp.parsers;

import java.lang.reflect.Method;
import java.util.Objects;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.IndexView;

import id.global.amqp.EdaAnnotationRuntimeException;
import id.global.common.annotations.amqp.Message;
import id.global.common.annotations.amqp.Scope;

public class MessageScopeParser {
    private static final String MESSAGE_SCOPE_PARAM = "scope";

    public static Scope getFromAnnotationClass(Message messageAnnotation) {
        final var scope = messageAnnotation.scope();
        if (Objects.nonNull(scope)) {
            return scope;
        }
        try {
            Method method = messageAnnotation.annotationType().getMethod(MESSAGE_SCOPE_PARAM);
            return (Scope) method.getDefaultValue();
        } catch (NoSuchMethodException e) {
            throw new EdaAnnotationRuntimeException(
                    String.format("Malformed %s annotation. Does not contain %s parameter default",
                            Message.class.getName(),
                            MESSAGE_SCOPE_PARAM));
        }

    }

    public static Scope getFromAnnotationInstance(AnnotationInstance messageAnnotation, IndexView index) {
        return Scope.valueOf(messageAnnotation.valueWithDefault(index, MESSAGE_SCOPE_PARAM).asString());
    }
}
