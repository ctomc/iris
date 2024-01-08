package org.iris_events.deployment;

import static io.quarkus.deployment.annotations.ExecutionTime.STATIC_INIT;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.spi.DeploymentException;

import org.jboss.jandex.*;
import org.jboss.jandex.Type;
import org.jboss.logging.Logger;

import io.quarkus.arc.deployment.*;
import io.quarkus.arc.deployment.UnremovableBeanBuildItem.BeanClassAnnotationExclusion;
import io.quarkus.arc.processor.*;
import io.quarkus.deployment.GeneratedClassGizmoAdaptor;
import io.quarkus.deployment.annotations.*;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.GeneratedClassBuildItem;
import io.quarkus.deployment.builditem.RunTimeConfigurationDefaultBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.pkg.builditem.ArtifactResultBuildItem;
import io.quarkus.deployment.recording.RecorderContext;
import io.quarkus.gizmo.*;
import io.quarkus.runtime.util.HashUtil;
import io.quarkus.smallrye.reactivemessaging.deployment.ReactiveMessagingDotNames;
import io.quarkus.smallrye.reactivemessaging.deployment.items.InjectedChannelBuildItem;
import io.quarkus.smallrye.reactivemessaging.deployment.items.InjectedEmitterBuildItem;
import io.quarkus.smallrye.reactivemessaging.deployment.items.MediatorBuildItem;
import io.quarkus.smallrye.reactivemessaging.runtime.*;
import io.quarkus.smallrye.reactivemessaging.runtime.SmallRyeReactiveMessagingRecorder.SmallRyeReactiveMessagingContext;
import io.smallrye.reactive.messaging.EmitterConfiguration;
import io.smallrye.reactive.messaging.Invoker;
import io.smallrye.reactive.messaging.annotations.Blocking;
import io.smallrye.reactive.messaging.providers.extension.ChannelConfiguration;

public class IrisReactiveMessagingProcessor {

    static final String DEFAULT_VIRTUAL_THREADS_MAX_CONCURRENCY = "1024";
    static final String INVOKER_SUFFIX = "_IrisInvoker";
    private static final Logger LOGGER = Logger
            .getLogger("io.quarkus.smallrye-reactive-messaging.deployment.processor");

    @BuildStep
    AnnotationsTransformerBuildItem transformBeanScope(BeanArchiveIndexBuildItem index,
            CustomScopeAnnotationsBuildItem scopes) {
        return new AnnotationsTransformerBuildItem(new AnnotationsTransformer() {

            @Override
            public boolean appliesTo(AnnotationTarget.Kind kind) {
                return kind == AnnotationTarget.Kind.CLASS;
            }

            @Override
            public void transform(TransformationContext ctx) {
                if (ctx.isClass()) {
                    ClassInfo clazz = ctx.getTarget().asClass();
                    Map<DotName, List<AnnotationInstance>> annotations = clazz.annotationsMap();
                    if (scopes.isScopeDeclaredOn(clazz)
                            || annotations.containsKey(ReactiveMessagingDotNames.JAXRS_PATH)
                            || annotations.containsKey(ReactiveMessagingDotNames.REST_CONTROLLER)
                            || annotations.containsKey(ReactiveMessagingDotNames.JAXRS_PROVIDER)) {
                        // Skip - has a built-in scope annotation or is a JAX-RS endpoint/provider
                        return;
                    }
                    if (annotations.containsKey(IrisDotNames.MESSAGE_HANDLER)) {
                        LOGGER.debugf(
                                "Found iris annotations on a class %s with no scope defined - adding @Dependent",
                                ctx.getTarget());
                        ctx.transform().add(Dependent.class).done();
                    }
                }
            }
        });
    }

    @BuildStep
    public List<UnremovableBeanBuildItem> removalExclusions() {
        return Arrays.asList(
                new UnremovableBeanBuildItem(
                        new BeanClassAnnotationExclusion(
                                IrisDotNames.MESSAGE_HANDLER)),
                new UnremovableBeanBuildItem(
                        new BeanClassAnnotationExclusion(
                                IrisDotNames.MESSAGE)));
    }

    @BuildStep
    @Produce(ArtifactResultBuildItem.class)
    public void deleteExistingContext(SynthesisFinishedBuildItem finishedBuildItem) {
        BeanInfo toRemove = null;
        for (var b : finishedBuildItem.getBeans()) {
            LOGGER.info("bean: " + b);
            if (b.getBeanClass().toString().contains("SmallRyeReactiveMessagingContext")) {
                LOGGER.info("bean: " + b);
                toRemove = b;
                break;
            }
        }
        if (toRemove != null) {
            finishedBuildItem.getBeans().remove(toRemove);
        }
    }

    @BuildStep
    @Produce(ArtifactResultBuildItem.class)
    public void deleteExistingContext2(BeanDiscoveryFinishedBuildItem finishedBuildItem) {
        BeanInfo toRemove = null;
        for (var b : finishedBuildItem.getBeans()) {
            LOGGER.info("bean: " + b);
            if (b.getBeanClass().toString().contains("SmallRyeReactiveMessagingContext")) {
                LOGGER.info("bean: " + b);
                toRemove = b;
                break;
            }
        }
        if (toRemove != null) {
            finishedBuildItem.getBeans().remove(toRemove);
        }
    }

    @BuildStep
    @Record(STATIC_INIT)
    public void build(SmallRyeReactiveMessagingRecorder recorder,
            RecorderContext recorderContext,
            BuildProducer<SyntheticBeanBuildItem> syntheticBeans,
            List<MediatorBuildItem> mediatorMethods,
            List<InjectedEmitterBuildItem> emitterFields,
            List<InjectedChannelBuildItem> channelFields,
            BuildProducer<GeneratedClassBuildItem> generatedClass,
            BuildProducer<ReflectiveClassBuildItem> reflectiveClass,
            BuildProducer<RunTimeConfigurationDefaultBuildItem> defaultConfig,
            ReactiveMessagingConfiguration conf) {

        ClassOutput classOutput = new GeneratedClassGizmoAdaptor(generatedClass, true);

        List<QuarkusMediatorConfiguration> mediatorConfigurations = new ArrayList<>(mediatorMethods.size());
        List<WorkerConfiguration> workerConfigurations = new ArrayList<>();
        List<EmitterConfiguration> emittersConfigurations = new ArrayList<>();
        List<ChannelConfiguration> channelConfigurations = new ArrayList<>();

        /*
         * Go through the collected MediatorMethods and build up the corresponding MediaConfiguration
         * This includes generating an invoker for each method
         * The configuration will then be captured and used at static init time to push data into smallrye
         */
        for (MediatorBuildItem mediatorMethod : mediatorMethods) {
            MethodInfo methodInfo = mediatorMethod.getMethod();
            BeanInfo bean = mediatorMethod.getBean();

            if (methodInfo.hasAnnotation(IrisDotNames.BLOCKING) || methodInfo.hasAnnotation(IrisDotNames.SMALLRYE_BLOCKING)
                    || methodInfo.hasAnnotation(IrisDotNames.RUN_ON_VIRTUAL_THREAD)
                    || methodInfo.hasAnnotation(IrisDotNames.TRANSACTIONAL)) {
                // Just in case both annotation are used, use @Blocking value.
                String poolName = methodInfo.hasAnnotation(IrisDotNames.RUN_ON_VIRTUAL_THREAD)
                        ? QuarkusWorkerPoolRegistry.DEFAULT_VIRTUAL_THREAD_WORKER
                        : Blocking.DEFAULT_WORKER_POOL;

                // If the method is annotated with the SmallRye Reactive Messaging @Blocking, extract the worker pool name if any
                if (methodInfo.hasAnnotation(IrisDotNames.BLOCKING)) {
                    AnnotationInstance blocking = methodInfo.annotation(IrisDotNames.BLOCKING);
                    poolName = blocking.value() == null ? Blocking.DEFAULT_WORKER_POOL : blocking.value().asString();
                }
                if (methodInfo.hasAnnotation(IrisDotNames.RUN_ON_VIRTUAL_THREAD)) {
                    defaultConfig.produce(new RunTimeConfigurationDefaultBuildItem(
                            "smallrye.messaging.worker." + poolName + ".max-concurrency",
                            DEFAULT_VIRTUAL_THREADS_MAX_CONCURRENCY));
                }
                workerConfigurations.add(new WorkerConfiguration(methodInfo.declaringClass().toString(),
                        methodInfo.name(), poolName, methodInfo.hasAnnotation(IrisDotNames.RUN_ON_VIRTUAL_THREAD)));
            }

            try {
                boolean isSuspendMethod = KotlinUtils.isKotlinSuspendMethod(methodInfo);

                QuarkusMediatorConfiguration mediatorConfiguration = IrisMediatorConfigurationUtil
                        .create(methodInfo, isSuspendMethod, bean, recorderContext,
                                Thread.currentThread().getContextClassLoader(), conf.strict);
                mediatorConfigurations.add(mediatorConfiguration);

                String generatedInvokerName = generateInvoker(bean, methodInfo, isSuspendMethod, mediatorConfiguration,
                        classOutput);
                /*
                 * We need to register the invoker's constructor for reflection since it will be called inside smallrye.
                 * We could potentially lift this restriction with some extra CDI bean generation, but it's probably not worth
                 * it
                 */
                reflectiveClass
                        .produce(ReflectiveClassBuildItem.builder(generatedInvokerName).build());
                mediatorConfiguration
                        .setInvokerClass((Class<? extends Invoker>) recorderContext.classProxy(generatedInvokerName));
            } catch (IllegalArgumentException e) {
                throw new DeploymentException(e); // needed to pass the TCK
            }
        }

        for (InjectedEmitterBuildItem it : emitterFields) {
            emittersConfigurations.add(it.getEmitterConfig());
        }
        for (InjectedChannelBuildItem it : channelFields) {
            channelConfigurations.add(it.getChannelConfig());
        }
        /*
         * for (SyntheticBeanBuildItem b : syntheticBeansList){
         * b.equals()
         * }
         */

        //syntheticBeansList.stream().filter(itm -> ((SyntheticBeanBuildItem)itm).)
        syntheticBeans.produce(
                SyntheticBeanBuildItem.configure(SmallRyeReactiveMessagingContext.class)
                        .supplier(recorder.createContext(mediatorConfigurations, workerConfigurations, emittersConfigurations,
                                channelConfigurations))
                        //.addQualifier(IrisDotNames.DEPENDENT)
                        //.priority(-1000)
                        .defaultBean()
                        .identifier("irisMessageContext")
                        .done());
    }

    /**
     * Generates an invoker class that looks like the following:
     *
     * <pre>
     * public class SomeName implements Invoker {
     *     private BeanType beanInstance;
     *
     *     public SomeName(Object var1) {
     *         this.beanInstance = var1;
     *     }
     *
     *     public Object invoke(Object[] args) {
     *         return this.beanInstance.doSomething(var1);
     *     }
     * }
     * </pre>
     */
    private String generateInvoker(BeanInfo bean, MethodInfo method, boolean isSuspendMethod,
            QuarkusMediatorConfiguration mediatorConfiguration,
            ClassOutput classOutput) {
        String baseName;
        if (bean.getImplClazz().enclosingClass() != null) {
            baseName = DotNames.simpleName(bean.getImplClazz().enclosingClass()) + "_"
                    + DotNames.simpleName(bean.getImplClazz().name());
        } else {
            baseName = DotNames.simpleName(bean.getImplClazz().name());
        }
        StringBuilder sigBuilder = new StringBuilder();
        sigBuilder.append(method.name()).append("_").append(method.returnType().name().toString());
        for (Type i : method.parameterTypes()) {
            sigBuilder.append(i.name().toString());
        }
        String targetPackage = DotNames.internalPackageNameWithTrailingSlash(bean.getImplClazz().name());
        String generatedName = targetPackage + baseName
                + INVOKER_SUFFIX + "_" + method.name() + "_"
                + HashUtil.sha1(sigBuilder.toString());

        if (isSuspendMethod
                && ((mediatorConfiguration.getIncoming().isEmpty()) && (mediatorConfiguration.getOutgoing() != null))) {
            // TODO: this restriction needs to be lifted
            throw new IllegalStateException(
                    "Currently suspend methods for Reactive Messaging are not supported on methods that are only annotated with @Outgoing");
        }

        if (!isSuspendMethod) {
            generateStandardInvoker(method, classOutput, generatedName);
        } else if (!mediatorConfiguration.getIncoming().isEmpty()) {
            generateSubscribingCoroutineInvoker(method, classOutput, generatedName);
        }

        return generatedName.replace('/', '.');
    }

    private void generateStandardInvoker(MethodInfo method, ClassOutput classOutput, String generatedName) {
        try (ClassCreator invoker = ClassCreator.builder().classOutput(classOutput).className(generatedName)
                .interfaces(Invoker.class)
                .build()) {

            String beanInstanceType = method.declaringClass().name().toString();
            FieldDescriptor beanInstanceField = invoker.getFieldCreator("beanInstance", beanInstanceType)
                    .getFieldDescriptor();

            // generate a constructor that takes the bean instance as an argument
            // the method parameter needs to be of type Object because that is what is used as the call site in SmallRye
            // Reactive Messaging
            try (MethodCreator ctor = invoker.getMethodCreator("<init>", void.class, Object.class)) {
                ctor.setModifiers(Modifier.PUBLIC);
                ctor.invokeSpecialMethod(MethodDescriptor.ofConstructor(Object.class), ctor.getThis());
                ResultHandle self = ctor.getThis();
                ResultHandle beanInstance = ctor.getMethodParam(0);
                ctor.writeInstanceField(beanInstanceField, self, beanInstance);
                ctor.returnValue(null);
            }

            try (MethodCreator invoke = invoker.getMethodCreator(
                    MethodDescriptor.ofMethod(generatedName, "invoke", Object.class, Object[].class))) {

                int parametersCount = method.parametersCount();
                String[] argTypes = new String[parametersCount];
                ResultHandle[] args = new ResultHandle[parametersCount];
                for (int i = 0; i < parametersCount; i++) {
                    // the only method argument of io.smallrye.reactive.messaging.Invoker is an object array, so we need to pull out
                    // each argument and put it in the target method arguments array
                    args[i] = invoke.readArrayValue(invoke.getMethodParam(0), i);
                    argTypes[i] = method.parameterType(i).name().toString();
                }
                ResultHandle result = invoke.invokeVirtualMethod(
                        MethodDescriptor.ofMethod(beanInstanceType, method.name(),
                                method.returnType().name().toString(), argTypes),
                        invoke.readInstanceField(beanInstanceField, invoke.getThis()), args);
                if (IrisDotNames.VOID.equals(method.returnType().name())) {
                    invoke.returnValue(invoke.loadNull());
                } else {
                    invoke.returnValue(result);
                }
            }
        }
    }

    private void generateSubscribingCoroutineInvoker(MethodInfo method, ClassOutput classOutput, String generatedName) {
        try (ClassCreator invoker = ClassCreator.builder().classOutput(classOutput).className(generatedName)
                .superClass(IrisDotNames.ABSTRACT_SUBSCRIBING_COROUTINE_INVOKER.toString())
                .build()) {

            // generate a constructor that takes the bean instance as an argument
            // the method parameter type needs to be Object, because that is what is used as the call site in SmallRye
            // Reactive Messaging
            try (MethodCreator ctor = invoker.getMethodCreator("<init>", void.class, Object.class)) {
                ctor.setModifiers(Modifier.PUBLIC);
                ctor.invokeSpecialMethod(
                        MethodDescriptor.ofConstructor(
                                IrisDotNames.ABSTRACT_SUBSCRIBING_COROUTINE_INVOKER.toString(),
                                Object.class.getName()),
                        ctor.getThis(),
                        ctor.getMethodParam(0));
                ctor.returnValue(null);
            }

            try (MethodCreator invoke = invoker.getMethodCreator("invokeBean", Object.class, Object.class, Object[].class,
                    IrisDotNames.CONTINUATION.toString())) {
                ResultHandle[] args = new ResultHandle[method.parametersCount()];
                ResultHandle array = invoke.getMethodParam(1);
                for (int i = 0; i < method.parametersCount() - 1; ++i) {
                    args[i] = invoke.readArrayValue(array, i);
                }
                args[args.length - 1] = invoke.getMethodParam(2);
                ResultHandle result = invoke.invokeVirtualMethod(method, invoke.getMethodParam(0), args);
                invoke.returnValue(result);
            }
        }
    }

}
