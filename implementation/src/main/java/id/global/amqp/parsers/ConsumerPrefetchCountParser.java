package id.global.amqp.parsers;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.IndexView;

import id.global.common.annotations.amqp.MessageHandler;

public class ConsumerPrefetchCountParser {

    private static final String MESSAGE_HANDLER_PREFETCH_COUNT_PARAM = "prefetchCount";

    public static int getFromAnnotationClass(MessageHandler messageHandlerAnnotation) {
        return messageHandlerAnnotation.prefetchCount();
    }

    public static int getFromAnnotationInstance(AnnotationInstance annotation, IndexView index) {
        return annotation.valueWithDefault(index, MESSAGE_HANDLER_PREFETCH_COUNT_PARAM).asInt();
    }
}
