package org.iris_events.deployment.scanner;

import java.util.Objects;
import java.util.Optional;

import org.iris_events.annotations.CachedMessage;
import org.iris_events.deployment.IrisDotNames;
import org.jboss.jandex.*;

public class ScannerUtils {

    public static AnnotationInstance getMessageAnnotation(final MethodInfo methodInfo, final IndexView index) {
        final var consumedClassInfo = getConsumedEventClassInfo(methodInfo, index);
        final var annotationInstance = consumedClassInfo.declaredAnnotation(IrisDotNames.MESSAGE);

        if (Objects.isNull(annotationInstance)) {
            throw new IllegalArgumentException(String.format("Consumed Event requires %s annotation for method %s in class %s.",
                    IrisDotNames.MESSAGE, methodInfo.name(), methodInfo.declaringClass()));
        }

        return annotationInstance;
    }

    private static ClassInfo getConsumedEventClassInfo(final MethodInfo methodInfo, final IndexView index) {
        final var parameters = methodInfo.parameterTypes();
        final var consumedEventTypes = parameters.stream()
                .map(Type::name)
                .map(index::getClassByName)
                .filter(Objects::nonNull)
                .toList();

        if (consumedEventTypes.isEmpty()) {
            throw new IllegalArgumentException(String.format("Consumed Event not found for method %s in class %s.",
                    methodInfo.name(), methodInfo.declaringClass()));
        }
        if (consumedEventTypes.size() > 1) {
            throw new IllegalArgumentException(String.format(
                    "Multiple consumed Events detected for method %s in class %s. Message handler can only handle one event type.",
                    methodInfo.name(), methodInfo.declaringClass()));
        }

        return consumedEventTypes.get(0);
    }

    /**
     * Returns cacheable annotation if message is annotated as {@link CachedMessage}.
     *
     * @param classInfo Message class info
     * @return Cacheable annotation if present
     */
    public static Optional<AnnotationInstance> getCacheableAnnotation(final ClassInfo classInfo) {
        return Optional.ofNullable(classInfo.declaredAnnotation(IrisDotNames.DOT_NAME_CACHED_MESSAGE));
    }
}
