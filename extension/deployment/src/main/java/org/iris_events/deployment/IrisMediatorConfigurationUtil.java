package org.iris_events.deployment;

import static io.quarkus.smallrye.reactivemessaging.deployment.ReactiveMessagingDotNames.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

import org.eclipse.microprofile.reactive.messaging.Acknowledgment;
import org.jboss.jandex.*;

import io.quarkus.arc.processor.BeanInfo;
import io.quarkus.deployment.recording.RecorderContext;
import io.quarkus.runtime.configuration.ConfigurationException;
import io.quarkus.smallrye.reactivemessaging.runtime.QuarkusMediatorConfiguration;
import io.quarkus.smallrye.reactivemessaging.runtime.QuarkusParameterDescriptor;
import io.quarkus.smallrye.reactivemessaging.runtime.QuarkusWorkerPoolRegistry;
import io.quarkus.smallrye.reactivemessaging.runtime.TypeInfo;
import io.smallrye.reactive.messaging.Shape;
import io.smallrye.reactive.messaging.annotations.Blocking;
import io.smallrye.reactive.messaging.providers.MediatorConfigurationSupport;

public final class IrisMediatorConfigurationUtil {

    private IrisMediatorConfigurationUtil() {
    }

    public static QuarkusMediatorConfiguration create(MethodInfo methodInfo, boolean isSuspendMethod, BeanInfo bean,
            RecorderContext recorderContext,
            ClassLoader cl, boolean strict) {

        Class[] parameterTypeClasses;
        Class<?> returnTypeClass;
        MediatorConfigurationSupport.GenericTypeAssignable genericReturnTypeAssignable;
        if (isSuspendMethod) {
            parameterTypeClasses = new Class[methodInfo.parametersCount() - 1];
            for (int i = 0; i < methodInfo.parametersCount() - 1; i++) {
                parameterTypeClasses[i] = load(methodInfo.parameterType(i).name().toString(), cl);
            }
            // the generated invoker will always return a CompletionStage
            // TODO: avoid hard coding this and use an SPI to communicate the info with the invoker generation code
            returnTypeClass = CompletionStage.class;
            genericReturnTypeAssignable = new JandexGenericTypeAssignable(determineReturnTypeOfSuspendMethod(methodInfo), cl);
        } else {
            parameterTypeClasses = new Class[methodInfo.parametersCount()];
            for (int i = 0; i < methodInfo.parametersCount(); i++) {
                parameterTypeClasses[i] = load(methodInfo.parameterType(i).name().toString(), cl);
            }
            returnTypeClass = load(methodInfo.returnType().name().toString(), cl);
            genericReturnTypeAssignable = new ReturnTypeGenericTypeAssignable(methodInfo, cl);
        }

        QuarkusMediatorConfiguration configuration = new QuarkusMediatorConfiguration();

        List<TypeInfo> gen = new ArrayList<>();
        for (int i = 0; i < methodInfo.parameterTypes().size(); i++) {
            TypeInfo ti = new TypeInfo();
            Type type = methodInfo.parameterTypes().get(i);
            ti.setName(recorderContext.classProxy(type.name().toString()));
            List<Class<?>> inner = new ArrayList<>();
            if (type.kind() == Type.Kind.PARAMETERIZED_TYPE) {
                inner = type.asParameterizedType().arguments().stream()
                        .map(t -> recorderContext.classProxy(t.name().toString()))
                        .collect(Collectors.toList());
            }
            ti.setGenerics(inner);
            gen.add(ti);
        }

        QuarkusParameterDescriptor descriptor = new QuarkusParameterDescriptor(gen);
        configuration.setParameterDescriptor(descriptor);

        MediatorConfigurationSupport mediatorConfigurationSupport = new MediatorConfigurationSupport(
                fullMethodName(methodInfo), returnTypeClass, parameterTypeClasses,
                genericReturnTypeAssignable,
                methodInfo.parameterTypes().isEmpty() ? new AlwaysInvalidIndexGenericTypeAssignable()
                        : new MethodParamGenericTypeAssignable(methodInfo, 0, cl));

        if (strict) {
            mediatorConfigurationSupport.strict();
        }

        configuration.setBeanId(bean.getIdentifier());
        configuration.setMethodName(methodInfo.name());

        String returnTypeName = returnTypeClass.getName();
        configuration.setReturnType(recorderContext.classProxy(returnTypeName));

        // We need to extract the value of @Incoming and @Incomings (which contains an array of @Incoming)
        List<String> incomingValues = new ArrayList<>(getValues(methodInfo, IrisDotNames.MESSAGE_HANDLER));
        //incomingValues.addAll(getIncomingValues(methodInfo));
        configuration.setIncomings(incomingValues);

        // We need to extract the value of @Outgoing and @Outgoings (which contains an array of @Outgoing)
        //todo get outgoings from message handler signature as return value + producer
        //List<String> outgoingValues = new ArrayList<>(getValues(methodInfo, OUTGOING));
        List<String> outgoingValues = new ArrayList<>();
        //outgoingValues.addAll(getOutgoingValues(methodInfo));
        configuration.setOutgoings(outgoingValues);

        Shape shape = mediatorConfigurationSupport.determineShape(incomingValues, outgoingValues);
        configuration.setShape(shape);
        Acknowledgment.Strategy acknowledgment = mediatorConfigurationSupport
                .processSuppliedAcknowledgement(incomingValues,
                        () -> {
                            AnnotationInstance instance = methodInfo.annotation(IrisDotNames.ACKNOWLEDGMENT);
                            if (instance != null) {
                                return Acknowledgment.Strategy.valueOf(instance.value().asEnum());
                            }
                            return null;
                        });
        configuration.setAcknowledgment(acknowledgment);

        MediatorConfigurationSupport.ValidationOutput validationOutput = mediatorConfigurationSupport.validate(shape,
                acknowledgment);
        configuration.setProduction(validationOutput.getProduction());
        configuration.setConsumption(validationOutput.getConsumption());
        configuration.setIngestedPayloadType(validationOutput.getIngestedPayloadType());
        configuration.setUseBuilderTypes(validationOutput.getUseBuilderTypes());
        configuration.setUseReactiveStreams(validationOutput.getUseReactiveStreams());

        if (acknowledgment == null) {
            acknowledgment = mediatorConfigurationSupport.processDefaultAcknowledgement(shape,
                    validationOutput.getConsumption(), validationOutput.getProduction());
            configuration.setAcknowledgment(acknowledgment);
        }

        configuration.setHasTargetedOutput(mediatorConfigurationSupport.processTargetedOutput());

        AnnotationInstance blockingAnnotation = methodInfo.annotation(IrisDotNames.BLOCKING);
        AnnotationInstance smallryeBlockingAnnotation = methodInfo.annotation(IrisDotNames.SMALLRYE_BLOCKING);
        AnnotationInstance transactionalAnnotation = methodInfo.annotation(IrisDotNames.TRANSACTIONAL);
        AnnotationInstance runOnVirtualThreadAnnotation = methodInfo.annotation(IrisDotNames.RUN_ON_VIRTUAL_THREAD);
        // IF @RunOnVirtualThread is used on the declaring class, it forces all @Blocking method to be run on virtual threads.
        AnnotationInstance runOnVirtualThreadClassAnnotation = methodInfo.declaringClass()
                .annotation(IrisDotNames.RUN_ON_VIRTUAL_THREAD);
        if (blockingAnnotation != null || smallryeBlockingAnnotation != null || transactionalAnnotation != null
                || runOnVirtualThreadAnnotation != null) {
            mediatorConfigurationSupport.validateBlocking(validationOutput);
            configuration.setBlocking(true);
            if (blockingAnnotation != null) {
                AnnotationValue ordered = blockingAnnotation.value("ordered");
                if (runOnVirtualThreadAnnotation != null || runOnVirtualThreadClassAnnotation != null) {
                    if (ordered != null && ordered.asBoolean()) {
                        throw new ConfigurationException(
                                "The method `" + methodInfo.name()
                                        + "` is using `@RunOnVirtualThread` but explicitly set as `@Blocking(ordered = true)`");
                    }
                    configuration.setBlockingExecutionOrdered(false);
                    configuration.setWorkerPoolName(QuarkusWorkerPoolRegistry.DEFAULT_VIRTUAL_THREAD_WORKER);
                } else {
                    configuration.setBlockingExecutionOrdered(ordered == null || ordered.asBoolean());
                }
                String poolName;
                if (blockingAnnotation.value() != null &&
                        !(poolName = blockingAnnotation.value().asString()).equals(Blocking.DEFAULT_WORKER_POOL)) {
                    configuration.setWorkerPoolName(poolName);
                }
            } else if (runOnVirtualThreadAnnotation != null || runOnVirtualThreadClassAnnotation != null) {
                configuration.setBlockingExecutionOrdered(false);
                configuration.setWorkerPoolName(QuarkusWorkerPoolRegistry.DEFAULT_VIRTUAL_THREAD_WORKER);
            } else {
                configuration.setBlockingExecutionOrdered(true);
            }
        }

        return configuration;
    }

    // TODO: avoid hard coding CompletionStage handling
    private static Type determineReturnTypeOfSuspendMethod(MethodInfo methodInfo) {
        Type lastParamType = methodInfo.parameterType(methodInfo.parametersCount() - 1);
        if (lastParamType.kind() != Type.Kind.PARAMETERIZED_TYPE) {
            throw new IllegalStateException("Something went wrong during parameter type resolution - expected "
                    + lastParamType + " to be a Continuation with a generic type");
        }
        lastParamType = lastParamType.asParameterizedType().arguments().get(0);
        if (lastParamType.kind() != Type.Kind.WILDCARD_TYPE) {
            throw new IllegalStateException("Something went wrong during parameter type resolution - expected "
                    + lastParamType + " to be a Continuation with a generic type");
        }
        lastParamType = lastParamType.asWildcardType().superBound();
        if (lastParamType.name().equals(IrisDotNames.KOTLIN_UNIT)) {
            lastParamType = Type.create(IrisDotNames.VOID_CLASS, Type.Kind.CLASS);
        }
        lastParamType = ParameterizedType.create(IrisDotNames.COMPLETION_STAGE, new Type[] { lastParamType },
                Type.create(IrisDotNames.COMPLETION_STAGE, Type.Kind.CLASS));
        return lastParamType;
    }

    private static Class<?> load(String className, ClassLoader cl) {
        switch (className) {
            case "boolean":
                return boolean.class;
            case "byte":
                return byte.class;
            case "short":
                return short.class;
            case "int":
                return int.class;
            case "long":
                return long.class;
            case "float":
                return float.class;
            case "double":
                return double.class;
            case "char":
                return char.class;
            case "void":
                return void.class;
        }
        try {
            return Class.forName(className, false, cl);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String getValue(MethodInfo methodInfo, DotName dotName) {
        AnnotationInstance annotationInstance = methodInfo.annotation(dotName);
        String value = null;

        if (annotationInstance != null) {
            if (annotationInstance.value() != null) {
                value = annotationInstance.value().asString();

            }
            if ((value == null) || value.isEmpty()) {
                throw new IllegalArgumentException(
                        "@" + dotName.withoutPackagePrefix() + " value cannot be blank. Offending method is: "
                                + fullMethodName(methodInfo));
            }

            // TODO: does it make sense to validate the name with the supplied configuration?
        }

        return value;
    }

    private static List<String> getValues(MethodInfo methodInfo, DotName dotName) {
        return methodInfo.annotations().stream().filter(ai -> ai.name().equals(dotName))
                .map(ai -> ai.value().asString())
                .collect(Collectors.toList());
    }

    private static String fullMethodName(MethodInfo methodInfo) {
        return methodInfo.declaringClass() + "#" + methodInfo.name();
    }

    public static class ReturnTypeGenericTypeAssignable extends JandexGenericTypeAssignable {

        public ReturnTypeGenericTypeAssignable(MethodInfo method, ClassLoader classLoader) {
            super(method.returnType(), classLoader);
        }
    }

    private static class JandexGenericTypeAssignable implements MediatorConfigurationSupport.GenericTypeAssignable {

        // will be used when we need to check assignability
        private final ClassLoader classLoader;
        private final Type type;

        public JandexGenericTypeAssignable(Type type, ClassLoader classLoader) {
            this.classLoader = classLoader;
            this.type = type;
        }

        @Override
        public Result check(Class<?> target, int index) {
            if (type.kind() != Type.Kind.PARAMETERIZED_TYPE) {
                return Result.NotGeneric;
            }
            List<Type> arguments = type.asParameterizedType().arguments();
            if (arguments.size() >= index + 1) {
                Class<?> argumentClass = load(arguments.get(index).name().toString(), classLoader);
                return target.isAssignableFrom(argumentClass) ? Result.Assignable : Result.NotAssignable;
            } else {
                return Result.InvalidIndex;
            }
        }

        @Override
        public java.lang.reflect.Type getType(int index) {
            Type t = extract(type, index);
            if (t != null) {
                return load(t.name().toString(), classLoader);
            }
            return null;
        }

        private Type extract(Type type, int index) {
            if (type.kind() != Type.Kind.PARAMETERIZED_TYPE) {
                return null;
            } else {
                List<Type> arguments = type.asParameterizedType().arguments();
                if (arguments.size() >= index + 1) {
                    Type result = arguments.get(index);
                    if (result.kind() == Type.Kind.WILDCARD_TYPE) {
                        return null;
                    }
                    return result;
                } else {
                    return null;
                }
            }
        }

        @Override
        public java.lang.reflect.Type getType(int index, int subIndex) {
            Type generic = extract(type, index);
            if (generic != null) {
                Type t = extract(generic, subIndex);
                if (t != null) {
                    return load(t.name().toString(), classLoader);
                }
            }
            return null;
        }
    }

    public static class AlwaysInvalidIndexGenericTypeAssignable
            implements MediatorConfigurationSupport.GenericTypeAssignable {

        @Override
        public Result check(Class<?> target, int index) {
            return Result.InvalidIndex;
        }

        @Override
        public java.lang.reflect.Type getType(int index) {
            return null;
        }

        @Override
        public java.lang.reflect.Type getType(int index, int subIndex) {
            return null;
        }
    }

    public static class MethodParamGenericTypeAssignable extends JandexGenericTypeAssignable {

        public MethodParamGenericTypeAssignable(MethodInfo method, int paramIndex, ClassLoader classLoader) {
            super(getGenericParameterType(method, paramIndex), classLoader);
        }

        public MethodParamGenericTypeAssignable(Type type, ClassLoader classLoader) {
            super(type, classLoader);
        }

        private static Type getGenericParameterType(MethodInfo method, int paramIndex) {
            List<Type> parameters = method.parameterTypes();
            if (parameters.size() < paramIndex + 1) {
                throw new IllegalArgumentException("Method " + method + " only has " + parameters.size()
                        + " so parameter with index " + paramIndex + " cannot be retrieved");
            }
            return parameters.get(paramIndex);
        }
    }
}
