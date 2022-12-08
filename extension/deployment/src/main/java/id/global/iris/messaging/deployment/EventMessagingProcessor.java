package id.global.iris.messaging.deployment;

import id.global.iris.common.annotations.Scope;
import id.global.iris.messaging.deployment.builditem.MessageHandlerInfoBuildItem;
import id.global.iris.messaging.deployment.builditem.MessageInfoBuildItem;
import id.global.iris.messaging.deployment.builditem.ProducerDefinedExchangeBuildItem;
import id.global.iris.messaging.deployment.scanner.Scanner;
import id.global.iris.messaging.runtime.BasicPropertiesProvider;
import id.global.iris.messaging.runtime.EventAppInfoProvider;
import id.global.iris.messaging.runtime.InstanceInfoProvider;
import id.global.iris.messaging.runtime.QueueNameProvider;
import id.global.iris.messaging.runtime.TimestampProvider;
import id.global.iris.messaging.runtime.auth.GidJwtValidator;
import id.global.iris.messaging.runtime.channel.ConsumerChannelService;
import id.global.iris.messaging.runtime.channel.ProducerChannelService;
import id.global.iris.messaging.runtime.configuration.IrisBuildConfiguration;
import id.global.iris.messaging.runtime.configuration.IrisRabbitMQConfig;
import id.global.iris.messaging.runtime.connection.ConnectionFactoryProvider;
import id.global.iris.messaging.runtime.connection.ConsumerConnectionProvider;
import id.global.iris.messaging.runtime.connection.ProducerConnectionProvider;
import id.global.iris.messaging.runtime.consumer.ConsumerContainer;
import id.global.iris.messaging.runtime.consumer.FrontendEventConsumer;
import id.global.iris.messaging.runtime.context.EventAppContext;
import id.global.iris.messaging.runtime.context.EventContext;
import id.global.iris.messaging.runtime.context.IrisContext;
import id.global.iris.messaging.runtime.context.MethodHandleContext;
import id.global.iris.messaging.runtime.exception.IrisExceptionHandler;
import id.global.iris.messaging.runtime.health.IrisLivenessCheck;
import id.global.iris.messaging.runtime.health.IrisReadinessCheck;
import id.global.iris.messaging.runtime.producer.CorrelationIdProvider;
import id.global.iris.messaging.runtime.producer.EventProducer;
import id.global.iris.messaging.runtime.recorder.ConsumerInitRecorder;
import id.global.iris.messaging.runtime.recorder.EventAppRecorder;
import id.global.iris.messaging.runtime.recorder.MethodHandleRecorder;
import id.global.iris.messaging.runtime.requeue.MessageRequeueHandler;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.BeanContainerBuildItem;
import io.quarkus.arc.deployment.UnremovableBeanBuildItem;
import io.quarkus.arc.processor.DotNames;
import io.quarkus.bootstrap.classloading.QuarkusClassLoader;
import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.Capability;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.ApplicationInfoBuildItem;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.IndexDependencyBuildItem;
import io.quarkus.smallrye.health.deployment.spi.HealthBuildItem;
import org.jboss.jandex.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

class EventMessagingProcessor {

    public static class EventMessagingEnabled implements BooleanSupplier {

        IrisBuildConfiguration config;

        @Override
        public boolean getAsBoolean() {
            return config.enabled;
        }
    }

    private static final String FEATURE = "quarkus-iris";
    private static final Logger log = LoggerFactory.getLogger(EventMessagingProcessor.class);

    @SuppressWarnings("unused")
    @BuildStep(onlyIf = EventMessagingEnabled.class)
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @SuppressWarnings("unused")
    @BuildStep(onlyIf = EventMessagingEnabled.class)
    void declareIrisBeans(BuildProducer<AdditionalBeanBuildItem> additionalBeanBuildItemBuildProducer) {
        additionalBeanBuildItemBuildProducer.produce(
                new AdditionalBeanBuildItem.Builder()
                        .addBeanClasses(
                                InstanceInfoProvider.class,
                                ConsumerConnectionProvider.class,
                                ProducerConnectionProvider.class,
                                ConsumerChannelService.class,
                                ProducerChannelService.class,
                                ConsumerContainer.class,
                                EventAppInfoProvider.class,
                                EventContext.class,
                                EventProducer.class,
                                ConnectionFactoryProvider.class,
                                MessageRequeueHandler.class,
                                CorrelationIdProvider.class,
                                GidJwtValidator.class,
                                FrontendEventConsumer.class,
                                IrisExceptionHandler.class,
                                QueueNameProvider.class,
                                IrisReadinessCheck.class,
                                IrisLivenessCheck.class,
                                TimestampProvider.class,
                                BasicPropertiesProvider.class,
                                IrisRabbitMQConfig.class)
                        .setUnremovable()
                        .setDefaultScope(DotNames.APPLICATION_SCOPED)
                        .build());
    }

    @SuppressWarnings("unused")
    @BuildStep(onlyIf = EventMessagingEnabled.class)
    void scanForMessageHandlers(CombinedIndexBuildItem combinedIndexBuildItem, ApplicationInfoBuildItem appInfo,
                                BuildProducer<MessageHandlerInfoBuildItem> messageHandlerProducer) {

        final var index = combinedIndexBuildItem.getIndex();
        final var scanner = new Scanner(index, appInfo.getName());
        scanner.scanEventHandlerAnnotations()
                .forEach(messageHandlerProducer::produce);
    }

    @SuppressWarnings("unused")
    @BuildStep(onlyIf = EventMessagingEnabled.class)
    void scanForMessages(CombinedIndexBuildItem combinedIndexBuildItem, ApplicationInfoBuildItem appInfo, BuildProducer<MessageInfoBuildItem> messageInfoProducer) {
        log.info("Scanning for message annotations.");

        final var index = combinedIndexBuildItem.getIndex();
        final var scanner = new Scanner(index, appInfo.getName());
        scanner.scanMessageAnnotations()
                .forEach(messageInfoProducer::produce);
        scanner.scanMessageAnnotations()
                .forEach(annotation -> {
                    log.info("Got Message annotation instance {}", annotation);
                });
    }

    @SuppressWarnings("unused")
    @Record(ExecutionTime.STATIC_INIT)
    @BuildStep(onlyIf = EventMessagingEnabled.class)
    void initProducerDefinedExchangeRequests(List<MessageHandlerInfoBuildItem> messageHandlerInfoBuildItems, List<MessageInfoBuildItem> messageInfoBuildItems) {
        // Fills ProducedEventExchangeDeclarator with requests for producer defined exchanges

        // get classes that are messages (filter out those that are already generated)
        // get classes that are arguments in messageHandlers
        // subtract them from messages
        // those left are producerDefined, send them to an exchange declarator (see how we create consumers)
    }

    @SuppressWarnings("unused")
    @Record(ExecutionTime.RUNTIME_INIT)
    @BuildStep(onlyIf = EventMessagingEnabled.class)
    void declareProducerDefinedExchanges(List<ProducerDefinedExchangeBuildItem> messageHandlerInfoBuildItems) {
        // Reads requests for producer defined exchanges and creates them on the broker
    }

    @SuppressWarnings("unused")
    @BuildStep
    UnremovableBeanBuildItem unremovable() {
        // Any bean that contains or is annotated with annotation defined within the given package is considered unremovable
        return UnremovableBeanBuildItem.beanClassAnnotation("id.global.iris.common.annotations");
    }

    @SuppressWarnings("unused")
    @Record(ExecutionTime.RUNTIME_INIT)
    @BuildStep(onlyIf = EventMessagingEnabled.class)
    void configureConsumer(final BeanContainerBuildItem beanContainer, ConsumerInitRecorder consumerInitRecorder,
                           List<MessageHandlerInfoBuildItem> messageHandlerInfoBuildItems) {
        if (!messageHandlerInfoBuildItems.isEmpty()) {
            consumerInitRecorder.initConsumers(beanContainer.getValue());
        }
    }

    @SuppressWarnings("unused")
    @Record(ExecutionTime.STATIC_INIT)
    @BuildStep(onlyIf = EventMessagingEnabled.class)
    void declareMessageHandlers(final BeanContainerBuildItem beanContainer,
                                List<MessageHandlerInfoBuildItem> messageHandlerInfoBuildItems,
                                MethodHandleRecorder methodHandleRecorder) {
        QuarkusClassLoader cl = (QuarkusClassLoader) Thread.currentThread().getContextClassLoader();
        List<String> handlers = new ArrayList<>();
        messageHandlerInfoBuildItems.forEach(col -> {
            try {
                Class<?> handlerClass = cl.loadClass(col.getDeclaringClass().name().toString());
                Class<?> eventClass = cl.loadClass(col.getParameterType().asClassType().name().toString());
                Class<?> returnEventClass = col.getReturnType().kind() == Type.Kind.CLASS
                        ? cl.loadClass(col.getReturnType().asClassType().name().toString())
                        : null;

                MethodHandleContext methodHandleContext = new MethodHandleContext(handlerClass, eventClass,
                        returnEventClass, col.getMethodName());
                IrisContext irisContext = new IrisContext(col.getName(),
                        col.getBindingKeys(),
                        col.getExchangeType(),
                        col.getScope(),
                        col.isDurable(),
                        col.isAutoDelete(),
                        col.isQueuePerInstance(),
                        col.getPrefetchCount(),
                        col.getTtl(),
                        col.getDeadLetterQueue(),
                        col.getRolesAllowed());

                if (col.getScope() != Scope.FRONTEND) {
                    methodHandleRecorder.registerConsumer(beanContainer.getValue(), methodHandleContext, irisContext);
                } else {
                    methodHandleRecorder.registerFrontendCallback(beanContainer.getValue(), methodHandleContext, irisContext);
                }

                StringBuilder sb = new StringBuilder();
                sb.append(handlerClass.getSimpleName()).append("#").append(col.getMethodName());
                sb.append(" (").append(eventClass.getSimpleName());
                if (returnEventClass != null) {
                    sb.append(" -> ").append(returnEventClass.getSimpleName());
                }
                sb.append(")");

                handlers.add(sb.toString());

            } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | IOException e) {
                log.error("Could not record method handle. methodName: " + col.getMethodName(), e);
            }
        });
        log.info("Registered method handlers: " + String.join(", ", handlers));
    }

    @SuppressWarnings("unused")
    @Record(ExecutionTime.STATIC_INIT)
    @BuildStep(onlyIf = EventMessagingEnabled.class)
    void provideEventAppContext(final BeanContainerBuildItem beanContainer, ApplicationInfoBuildItem applicationInfoBuildItem,
                                EventAppRecorder eventAppRecorder) {
        eventAppRecorder.registerEventAppContext(beanContainer.getValue(), new EventAppContext(
                applicationInfoBuildItem.getName()));
    }

    @SuppressWarnings("unused")
    @BuildStep
    void addDependencies(BuildProducer<IndexDependencyBuildItem> indexDependency) {
        indexDependency.produce(new IndexDependencyBuildItem("id.global.common", "globalid-common"));
        indexDependency.produce(new IndexDependencyBuildItem("id.global.iris", "quarkus-iris"));
    }

    @SuppressWarnings("unused")
    @BuildStep
    HealthBuildItem addReadinessCheck(Capabilities capabilities, IrisBuildConfiguration configuration) {
        if (capabilities.isPresent(Capability.SMALLRYE_HEALTH)) {
            return new HealthBuildItem("id.global.iris.messaging.runtime.health.IrisReadinessCheck",
                    configuration.readinessCheckEnabled);
        } else {
            return null;
        }
    }

    @SuppressWarnings("unused")
    @BuildStep
    HealthBuildItem addLivenessCheck(Capabilities capabilities, IrisBuildConfiguration configuration) {
        if (capabilities.isPresent(Capability.SMALLRYE_HEALTH)) {
            return new HealthBuildItem("id.global.iris.messaging.runtime.health.IrisLivenessCheck",
                    configuration.livenessCheckEnabled);
        } else {
            return null;
        }
    }
}
