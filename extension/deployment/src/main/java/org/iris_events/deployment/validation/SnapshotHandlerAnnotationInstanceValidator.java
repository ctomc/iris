package org.iris_events.deployment.validation;

import java.util.List;

import org.iris_events.deployment.IrisDotNames;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.IndexView;

public class SnapshotHandlerAnnotationInstanceValidator implements AnnotationInstanceValidator {

    private final List<AnnotationInstanceValidator> validators;

    public SnapshotHandlerAnnotationInstanceValidator(IndexView indexView, String serviceName) {
        final var messageAnnotationValidator = new MessageAnnotationValidator(serviceName, indexView);
        final var methodReturnTypeAnnotationValidator = new MethodReturnTypeAnnotationValidator(indexView,
                messageAnnotationValidator);

        final var requiredMessageValidator = new AllowedMessageValidator(List.of(IrisDotNames.SNAPSHOT_REQUESTED_DOT_NAME));
        final var snapshotMethodParameterTypeAnnotationValidator = new MethodParameterTypeAnnotationValidator(indexView,
                List.of(requiredMessageValidator, messageAnnotationValidator));
        final var resourceTypeParamAnnotationValidator = new ResourceTypeParamAnnotationValidator();

        this.validators = List.of(methodReturnTypeAnnotationValidator,
                snapshotMethodParameterTypeAnnotationValidator, resourceTypeParamAnnotationValidator);
    }

    @Override
    public void validate(AnnotationInstance annotationInstance) {
        validators.forEach(validator -> validator.validate(annotationInstance));
    }
}
