package org.iris_events.deployment;

import static org.iris_events.deployment.WiringHelper.*;

import jakarta.enterprise.inject.spi.DeploymentException;

import org.iris_events.asyncapi.parsers.ExchangeParser;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.Type;
import org.jboss.logging.Logger;

import io.quarkus.arc.deployment.BeanDiscoveryFinishedBuildItem;
import io.quarkus.arc.deployment.TransformedAnnotationsBuildItem;
import io.quarkus.arc.deployment.ValidationPhaseBuildItem;
import io.quarkus.arc.processor.BeanInfo;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.ConfigDescriptionBuildItem;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.smallrye.reactivemessaging.deployment.items.*;

public class WiringProcessor {

    private static final Logger LOGGER = Logger
            .getLogger("org.iris_events.deployment.processor");

    @BuildStep
    void extractComponents(BeanDiscoveryFinishedBuildItem beanDiscoveryFinished,
            TransformedAnnotationsBuildItem transformedAnnotations,
            BuildProducer<ChannelBuildItem> appChannels,
            BuildProducer<MediatorBuildItem> mediatorMethods,
            BuildProducer<ValidationPhaseBuildItem.ValidationErrorBuildItem> validationErrors,
            BuildProducer<ConfigDescriptionBuildItem> configDescriptionBuildItemBuildProducer) {

        // We need to collect all business methods annotated with @Incoming/@Outgoing first
        for (BeanInfo bean : beanDiscoveryFinished.beanStream().classBeans()) {
            // TODO: add support for inherited business methods
            //noinspection OptionalGetWithoutIsPresent

            for (MethodInfo method : bean.getTarget().get().asClass().methods()) {

                AnnotationInstance messageHandler = transformedAnnotations.getAnnotation(method, IrisDotNames.MESSAGE_HANDLER);

                AnnotationInstance blocking = transformedAnnotations.getAnnotation(method, IrisDotNames.BLOCKING);

                if (messageHandler != null) {
                    final var messageAnnotation = method.parameters().get(0).type().annotation(IrisDotNames.MESSAGE);
                    //final var exchangeType = ExchangeType.valueOf(messageAnnotation.valueWithDefault(index, AnnotationInstanceParams.EXCHANGE_TYPE_PARAM).asString());

                    final var exchange = ExchangeParser.getFromAnnotationInstance(messageAnnotation);
                    //final var scope = MessageScopeParser.getFromAnnotationInstance(messageAnnotation, index);
                    configDescriptionBuildItemBuildProducer.produce(new ConfigDescriptionBuildItem(
                            "mp.messaging.incoming." + exchange + ".connector", null,
                            "The connector to use", null, null, ConfigPhase.BUILD_TIME));
                    produceIncomingChannel(appChannels, exchange);

                    if (method.returnType() != null && method.returnType().kind() == Type.Kind.CLASS) {
                        //todo this is completly wrong as needs to be loaded from @Message annotation
                        String eventName = method.returnType().name().withoutPackagePrefix().toLowerCase();

                        configDescriptionBuildItemBuildProducer.produce(new ConfigDescriptionBuildItem(
                                "mp.messaging.outgoing." + eventName + ".connector", null,
                                "The connector to use", null, null, ConfigPhase.BUILD_TIME));
                        produceOutgoingChannel(appChannels, eventName);

                    }

                    if (WiringHelper.isSynthetic(method)) {
                        continue;
                    }

                    mediatorMethods.produce(new MediatorBuildItem(bean, method));
                    LOGGER.debugf("Found mediator business method %s declared on %s", method, bean);
                } else if (blocking != null) {
                    validationErrors.produce(new ValidationPhaseBuildItem.ValidationErrorBuildItem(
                            new DeploymentException(
                                    "@Blocking used on " + method + " which has no @Incoming or @Outgoing annotation")));
                }
            }
        }

    }

}
