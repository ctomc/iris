package org.iris_events.deployment.validation;

import static org.iris_events.deployment.IrisDotNames.DOT_NAME_PRODUCED_EVENT;

import org.iris_events.deployment.MessageHandlerValidationException;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.Type;

public class MethodReturnTypeAnnotationValidator implements AnnotationInstanceValidator {

    private final IndexView index;
    private final MessageAnnotationValidator messageAnnotationValidator;

    public MethodReturnTypeAnnotationValidator(IndexView index, MessageAnnotationValidator messageAnnotationValidator) {
        this.index = index;
        this.messageAnnotationValidator = messageAnnotationValidator;
    }

    @Override
    public void validate(AnnotationInstance annotationInstance) {
        final var methodInfo = annotationInstance.target().asMethod();
        final var returnType = methodInfo.returnType();

        if (returnType.kind() == Type.Kind.VOID) {
            return;
        }

        if (returnType.kind() != Type.Kind.CLASS) {
            throw new MessageHandlerValidationException(
                    String.format(
                            "MessageHandler annotated method %s::%s must either have a class or void return type.",
                            methodInfo.declaringClass(),
                            methodInfo.name()));
        }

        final var classInfo = index.getClassByName(returnType.name());
        final var annotation = classInfo.declaredAnnotation(DOT_NAME_PRODUCED_EVENT);
        if (annotation == null) {
            throw new MessageHandlerValidationException(
                    String.format(
                            "MessageHandler annotated method %s::%s must either have a return object class annotated with @%s annotation or have a void return type.",
                            methodInfo.declaringClass(),
                            methodInfo.name(),
                            DOT_NAME_PRODUCED_EVENT.withoutPackagePrefix()));
        }

        messageAnnotationValidator.validate(annotation);
    }

}
