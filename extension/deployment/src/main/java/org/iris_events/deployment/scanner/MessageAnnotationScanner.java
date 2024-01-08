package org.iris_events.deployment.scanner;

import static java.util.function.Predicate.not;

import java.util.List;
import java.util.stream.Collectors;

import org.iris_events.annotations.ExchangeType;
import org.iris_events.asyncapi.parsers.CacheableTtlParser;
import org.iris_events.asyncapi.parsers.ExchangeParser;
import org.iris_events.asyncapi.parsers.MessageScopeParser;
import org.iris_events.asyncapi.parsers.RoutingKeyParser;
import org.iris_events.asyncapi.parsers.RpcResponseClassParser;
import org.iris_events.deployment.IrisDotNames;
import org.iris_events.deployment.builditem.MessageInfoBuildItem;
import org.iris_events.deployment.constants.AnnotationInstanceParams;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.IndexView;

public class MessageAnnotationScanner {

    public List<MessageInfoBuildItem> scanMessageAnnotations(IndexView indexView) {
        return indexView.getAnnotations(IrisDotNames.MESSAGE)
                .stream()
                .filter(not(annotationInstance -> annotationInstance.target().asClass().isSynthetic()))
                .map(item -> build(item, indexView))
                .collect(Collectors.toList());
    }

    protected MessageInfoBuildItem build(AnnotationInstance annotationInstance, IndexView index) {
        final var exchangeType = ExchangeType.valueOf(
                annotationInstance.valueWithDefault(index, AnnotationInstanceParams.EXCHANGE_TYPE_PARAM).asString());
        final var exchange = ExchangeParser.getFromAnnotationInstance(annotationInstance);
        final var routingKey = RoutingKeyParser.getFromAnnotationInstance(annotationInstance);
        final var scope = MessageScopeParser.getFromAnnotationInstance(annotationInstance, index);
        final var classType = annotationInstance.target().asClass();
        final var optionalCachedAnnotation = ScannerUtils.getCacheableAnnotation(classType);
        final var cacheTtl = optionalCachedAnnotation
                .map(cachedAnnotation -> CacheableTtlParser.getFromAnnotationInstance(cachedAnnotation, index))
                .orElse(null);
        final var rpcResponseType = RpcResponseClassParser.getFromAnnotationInstance(annotationInstance, index);

        return new MessageInfoBuildItem(
                classType,
                exchangeType,
                exchange,
                routingKey,
                scope,
                cacheTtl,
                rpcResponseType);
    }
}
