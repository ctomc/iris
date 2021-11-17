/*
 * Copyright 2019 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.smallrye.asyncapi.runtime.scanner;

import static io.smallrye.asyncapi.runtime.util.GidAnnotationParser.getBindingKeysCsv;
import static io.smallrye.asyncapi.runtime.util.GidAnnotationParser.getEventAutodelete;
import static io.smallrye.asyncapi.runtime.util.GidAnnotationParser.getEventDurable;
import static io.smallrye.asyncapi.runtime.util.GidAnnotationParser.getEventScope;
import static io.smallrye.asyncapi.runtime.util.GidAnnotationParser.getExchange;
import static io.smallrye.asyncapi.runtime.util.GidAnnotationParser.getExchangeType;
import static io.smallrye.asyncapi.runtime.util.GidAnnotationParser.getRolesAllowed;
import static io.smallrye.asyncapi.runtime.util.GidAnnotationParser.getRoutingKey;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import id.global.common.annotations.amqp.Message;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.Type;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.victools.jsonschema.generator.Option;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfig;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;
import com.github.victools.jsonschema.module.jackson.JacksonModule;
import com.github.victools.jsonschema.module.jackson.JacksonOption;

import id.global.common.annotations.amqp.MessageHandler;
import id.global.common.annotations.amqp.Scope;
import io.apicurio.datamodels.asyncapi.models.AaiSchema;
import io.apicurio.datamodels.asyncapi.v2.models.Aai20Document;
import io.smallrye.asyncapi.api.AsyncApiConfig;
import io.smallrye.asyncapi.api.util.MergeUtil;
import io.smallrye.asyncapi.runtime.generator.CustomDefinitionProvider;
import io.smallrye.asyncapi.runtime.io.components.ComponentReader;
import io.smallrye.asyncapi.runtime.io.info.InfoReader;
import io.smallrye.asyncapi.runtime.io.server.ServerReader;
import io.smallrye.asyncapi.runtime.scanner.model.ChannelInfo;
import io.smallrye.asyncapi.runtime.scanner.model.JsonSchemaInfo;
import io.smallrye.asyncapi.runtime.util.ChannelInfoGenerator;
import io.smallrye.asyncapi.runtime.util.JandexUtil;
import io.smallrye.asyncapi.runtime.util.SchemeIdGenerator;
import io.smallrye.asyncapi.spec.annotations.EventApp;

/**
 * Scans a deployment (using the archive and jandex annotation index) for relevant annotations. These
 * annotations, if found, are used to generate a valid AsyncAPI model.
 *
 * @author gasper.vrhovsek@gmail.com
 */
public class GidAnnotationScanner extends BaseAnnotationScanner {
    private static final Logger LOG = Logger.getLogger(GidAnnotationScanner.class);

    public static final DotName DOTNAME_EVENT_APP_DEFINITION = DotName.createSimple(EventApp.class.getName());
    public static final DotName DOTNAME_MESSAGE = DotName.createSimple(Message.class.getName());
    private final SchemaGenerator schemaGenerator;

    /**
     * Constructor.
     *
     * @param config AsyncApiConfig instance
     * @param index  IndexView of deployment
     */
    public GidAnnotationScanner(AsyncApiConfig config, IndexView index) {
        super(config, index);
        schemaGenerator = initSchemaGenerator(config);
    }

    public GidAnnotationScanner(AsyncApiConfig config, IndexView index, ClassLoader classLoader) {
        super(config, index, classLoader);
        schemaGenerator = initSchemaGenerator(config);
    }

    /**
     * Scan the deployment for relevant annotations. Returns an AsyncAPI data model that was
     * built from those found annotations.
     *
     * @return Document generated from scanning annotations
     */
    public Aai20Document scan() {
        LOG.debug("Scanning deployment for Async Annotations.");
        Aai20Document messageHandlerAaiDocument;
        try {
            messageHandlerAaiDocument = scanGidEventAppAnnotations();
        } catch (ClassNotFoundException e) {
            LOG.error("Could not create AaiDocument", e);
            throw new RuntimeException("Could not create AaiDocument", e);
        }
        return messageHandlerAaiDocument;
    }

    private Aai20Document scanGidEventAppAnnotations() throws ClassNotFoundException {
        Aai20Document asyncApi = this.annotationScannerContext.getAsyncApi();
        // Process @EventApp
        processEventAppDefinition(annotationScannerContext, asyncApi);
        // Process @Message
        List<AnnotationInstance> allMessageAnnotations = getClassAnnotations(Message.class, this.annotationScannerContext.getIndex()).collect(Collectors.toList());

        // Process @MessageHandler
        List<AnnotationInstance> consumedEventAnnotations = processMessageHandlerAnnotations(annotationScannerContext, asyncApi);

        processProducedEventAnnotations(annotationScannerContext, asyncApi, allMessageAnnotations, consumedEventAnnotations);
        processContextDefinitionReferencedSchemas(annotationScannerContext, asyncApi);

        return asyncApi;
    }

    private Stream<AnnotationInstance> getMethodAnnotations(Class<?> annotationClass, IndexView index) {
        DotName annotationName = DotName.createSimple(annotationClass.getName());
        return index.getAnnotations(annotationName).stream().filter(this::annotatedMethods);
    }

    private Stream<AnnotationInstance> getClassAnnotations(Class<?> annotationClass, IndexView index) {
        DotName annotationName = DotName.createSimple(annotationClass.getName());
        return index.getAnnotations(annotationName).stream().filter(this::annotatedClasses);
    }

    private void processProducedEventAnnotations(AnnotationScannerContext context,
                                                 Aai20Document asyncApi,
                                                 List<AnnotationInstance> allMessageAnnotations,
                                                 List<AnnotationInstance> consumedEventAnnotations)
            throws ClassNotFoundException {
        FilteredIndexView index = context.getIndex();
        allMessageAnnotations.removeAll(consumedEventAnnotations);

        List<ChannelInfo> channelInfos = new ArrayList<>();
        Map<String, JsonSchemaInfo> producedEvents = new HashMap<>();
        Map<String, Scope> messageScopes = new HashMap<>();
        for (AnnotationInstance anno : allMessageAnnotations) {
            ClassInfo classInfo = anno.target().asClass();
            String classSimpleName = classInfo.simpleName();

            messageScopes.put(classSimpleName, getEventScope(anno));
            producedEvents.put(classSimpleName, generateProducedEventSchemaInfo(classInfo));

            final var routingKey = getRoutingKey(anno, classSimpleName);
            final var exchangeType = getExchangeType(anno);
            final var exchange = getExchange(anno, context.getProjectId(), exchangeType);
            final var rolesAllowed = getRolesAllowed(anno);
            final var durable = getEventDurable(anno, index);
            final var autodelete = getEventAutodelete(anno, index);

            channelInfos.add(ChannelInfoGenerator.generateSubscribeChannelInfo(
                    exchange,
                    routingKey,
                    classSimpleName,
                    exchangeType,
                    durable,
                    autodelete,
                    rolesAllowed
            ));
        }

        insertComponentSchemas(context, producedEvents, asyncApi);

        // TODO check what's with the types

        createChannels(channelInfos, messageScopes, asyncApi);
    }

    private List<AnnotationInstance> processMessageHandlerAnnotations(AnnotationScannerContext context, Aai20Document asyncApi)
            throws ClassNotFoundException {

        List<AnnotationInstance> consumedMessages = new ArrayList<>();

        FilteredIndexView index = context.getIndex();
        final var methodAnnotationInstances = getMethodAnnotations(MessageHandler.class, index).collect(Collectors.toList());

        final var incomingEvents = new HashMap<String, JsonSchemaInfo>();
        final var channelInfos = new ArrayList<ChannelInfo>();
        final var messageTypes = new HashMap<String, Scope>();

        for (AnnotationInstance annotationInstance : methodAnnotationInstances) {

            final var annotationName = annotationInstance.name();
            final var annotationValues = annotationInstance.values();
            final var methodInfo = (MethodInfo) annotationInstance.target();
            final var methodParameters = methodInfo.parameters();

            final var eventAnnotation = getEventAnnotation(methodParameters, index);
            consumedMessages.add(eventAnnotation);

            final var eventClass = eventAnnotation.target().asClass();
            final var eventClassSimpleName = eventClass.simpleName();

            final var bindingKeys = getBindingKeysCsv(eventAnnotation, eventClassSimpleName);
            final var exchangeType = getExchangeType(eventAnnotation);
            final var exchange = getExchange(eventAnnotation, context.getProjectId(), exchangeType);
            final var scope = getEventScope(eventAnnotation);

            final var durable = getEventDurable(eventAnnotation, index);
            final var autodelete = getEventAutodelete(eventAnnotation, index);

            final var isGeneratedClass = isGeneratedClass(eventClass);

            final var jsonSchemaInfo = generateJsonSchemaInfo(
                    annotationName,
                    eventClass.name().toString(),
                    annotationValues,
                    isGeneratedClass);

            final var subscribeChannelInfo = ChannelInfoGenerator.generatePublishChannelInfo(
                    exchange,
                    bindingKeys,
                    eventClassSimpleName,
                    exchangeType,
                    durable,
                    autodelete,
                    getRolesAllowed(annotationInstance));

            messageTypes.put(eventClassSimpleName, scope);
            incomingEvents.put(eventClassSimpleName, jsonSchemaInfo);
            channelInfos.add(subscribeChannelInfo);
        }

        insertComponentSchemas(context, incomingEvents, asyncApi);
        createChannels(channelInfos, messageTypes, asyncApi);

        return consumedMessages;
    }

    private void processContextDefinitionReferencedSchemas(AnnotationScannerContext context, Aai20Document asyncApi) {
        Map<String, AaiSchema> definitionSchemaMap = context.getDefinitionSchemaMap();
        asyncApi.components.schemas.putAll(definitionSchemaMap);
        context.clearDefinitionSchemaMap();
    }

    private Aai20Document processEventAppDefinition(final AnnotationScannerContext context, Aai20Document document) {
        final var annotations = context.getIndex().getAnnotations(DOTNAME_EVENT_APP_DEFINITION);
        final var packageDefs = annotations
                .stream()
                .filter(this::annotatedClasses)
                .collect(Collectors.toList());

        String projectVersion = context.getConfig().projectVersion();
        // Here we have packageDefs, now to build the AsyncAPI
        for (AnnotationInstance packageDef : packageDefs) {
            final var packageAai = new Aai20Document();
            try {
                final var projectId = JandexUtil.stringValue(packageDef, PROP_ID);
                final var projectSchemaId = SchemeIdGenerator.buildId(projectId);
                context.setProjectId(projectId);
                packageAai.id = projectSchemaId;
            } catch (URISyntaxException e) {
                LOG.error("Could not generate schema ID", e);
                throw new RuntimeException(e);
            }
            packageAai.info = InfoReader.readInfo(packageDef.value(PROP_INFO));
            if (projectVersion != null) {
                packageAai.info.version = projectVersion;
            }

            packageAai.servers = ServerReader.readServers(packageDef.value(PROP_SERVERS)).orElse(null);
            packageAai.components = ComponentReader.create();
            MergeUtil.merge(document, packageAai);
        }
        return document;
    }

    private JsonSchemaInfo generateProducedEventSchemaInfo(ClassInfo classInfo) throws ClassNotFoundException {
        final var className = classInfo.name().toString();
        final var loadedClass = loadClass(className);
        final var classSimpleName = loadedClass.getSimpleName();
        final var isGeneratedClass = isGeneratedClass(classInfo);

        ObjectNode generatedSchema = schemaGenerator.generateSchema(loadedClass);
        return new JsonSchemaInfo(
                null,
                classSimpleName,
                generatedSchema,
                null,
                isGeneratedClass);
    }

    private JsonSchemaInfo generateJsonSchemaInfo(DotName annotationName, String className,
            List<AnnotationValue> annotationValues, boolean isGeneratedClass) throws ClassNotFoundException {
        Class<?> loadedClass = loadClass(className);
        String eventSimpleName = loadedClass.getSimpleName();
        ObjectNode generatedSchema = schemaGenerator.generateSchema(loadedClass);
        return new JsonSchemaInfo(
                annotationName,
                eventSimpleName,
                generatedSchema,
                annotationValues,
                isGeneratedClass);
    }

    private Class<?> loadClass(String className) throws ClassNotFoundException {
        if (classLoader != null) {
            return classLoader.loadClass(className);
        } else {
            return Class.forName(className);
        }
    }

    private SchemaGenerator initSchemaGenerator(AsyncApiConfig config) {
        // Schema generator JsonSchema of components
        JacksonModule module = new JacksonModule(
                JacksonOption.FLATTENED_ENUMS_FROM_JSONPROPERTY
        );
        SchemaGeneratorConfigBuilder configBuilder = new SchemaGeneratorConfigBuilder(SchemaVersion.DRAFT_7,
                OptionPreset.PLAIN_JSON)
                .with(Option.DEFINITIONS_FOR_ALL_OBJECTS)
                .with(module);

        Set<String> excludeFromSchemas = config.excludeFromSchemas();
        if (!excludeFromSchemas.isEmpty()) {
            LOG.info("Registering custom definition providers for package prefixes: " + excludeFromSchemas);
            configBuilder.forTypesInGeneral()
                    .withCustomDefinitionProvider(CustomDefinitionProvider.convertUnknownTypeToObject(excludeFromSchemas));

            configBuilder.forFields()
                    .withCustomDefinitionProvider(CustomDefinitionProvider.convertUnknownFieldToObject(excludeFromSchemas));
        }

        SchemaGeneratorConfig schemaGeneratorConfig = configBuilder.build();
        return new SchemaGenerator(schemaGeneratorConfig);
    }

    private AnnotationInstance getEventAnnotation(final List<Type> parameters,
            final FilteredIndexView index) {

        final var consumedEventTypes = parameters.stream()
                .map(Type::name)
                .map(index::getClassByName)
                .filter(Objects::nonNull)
                .map(classInfo -> classInfo.classAnnotation(DOTNAME_MESSAGE))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (consumedEventTypes.isEmpty()) {
            throw new IllegalArgumentException("Consumed Event not found");
        }

        if (consumedEventTypes.size() > 1) {
            throw new IllegalArgumentException(
                    "Multiple consumed Events detected. Message handler can only handle one event type.");
        }

        return consumedEventTypes.get(0);
    }
}
