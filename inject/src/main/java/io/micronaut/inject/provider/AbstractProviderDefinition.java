package io.micronaut.inject.provider;

import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.DefaultBeanContext;
import io.micronaut.context.Qualifier;
import io.micronaut.context.annotation.Any;
import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.exceptions.BeanInstantiationException;
import io.micronaut.context.exceptions.DisabledBeanException;
import io.micronaut.context.exceptions.NoSuchBeanException;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.naming.Named;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.ArgumentCoercible;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanFactory;
import io.micronaut.inject.ConstructorInjectionPoint;
import io.micronaut.inject.InjectionPoint;
import io.micronaut.inject.annotation.MutableAnnotationMetadata;
import io.micronaut.inject.qualifiers.Qualifiers;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Abstract bean definition for other providers to extend from.
 *
 * @param <T> The generic type
 * @since 3.0.0
 * @author graemerocher
 */
public abstract class AbstractProviderDefinition<T> implements BeanDefinition<T>, BeanFactory<T> {

    public static final AnnotationMetadata ANNOTATION_METADATA;

    static {
        MutableAnnotationMetadata metadata = new MutableAnnotationMetadata();
        metadata.addDeclaredAnnotation(Any.class.getName(), Collections.emptyMap());
        metadata.addDeclaredStereotype(
                Collections.singletonList(Any.class.getName()),
                javax.inject.Qualifier.class.getName(),
                Collections.emptyMap()
        );
        metadata.addDeclaredAnnotation(BootstrapContextCompatible.class.getName(), Collections.emptyMap());
        ANNOTATION_METADATA = metadata;
    }

    /**
     * Builds a provider implementation.
     * @param context The context
     * @param argument The argument
     * @param qualifier The qualifier
     * @param singleton Whether the bean is a singleton
     * @return The provider
     */
    protected abstract @NonNull T buildProvider(
            @NonNull BeanContext context,
            @NonNull Argument<Object> argument,
            @Nullable Qualifier<Object> qualifier,
            boolean singleton);

    @Override
    public T build(
            BeanResolutionContext resolutionContext,
            BeanContext context,
            BeanDefinition<T> definition) throws BeanInstantiationException {
        final BeanResolutionContext.Segment<?> segment = resolutionContext.getPath().currentSegment().orElse(null);
        if (segment != null) {
            final InjectionPoint<?> injectionPoint = segment.getInjectionPoint();
            if (injectionPoint instanceof ArgumentCoercible) {
                Argument<?> injectionPointArgument = ((ArgumentCoercible<?>) injectionPoint)
                        .asArgument();

                Argument<?> resolveArgument = injectionPointArgument;
                if (resolveArgument.isOptional()) {
                    resolveArgument = resolveArgument.getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT);
                }
                @SuppressWarnings("unchecked") Argument<Object> argument =
                        (Argument<Object>) resolveArgument
                                .getFirstTypeVariable()
                                .orElse(null);
                if (argument != null) {
                    Qualifier<Object> qualifier = (Qualifier<Object>) resolutionContext.getCurrentQualifier();
                    if (qualifier == null && segment.getDeclaringType().isIterable()) {
                        final Object n = resolutionContext.getAttribute(Named.class.getName());
                        if (n != null) {
                            qualifier = Qualifiers.byName(n.toString());
                        }
                    }

                    boolean hasBean = context.containsBean(argument, qualifier);
                    if (hasBean) {
                        return buildProvider(
                                context,
                                argument,
                                qualifier,
                                definition.isSingleton()
                        );
                    } else {
                        if (injectionPointArgument.isOptional()) {
                            return (T) Optional.empty();
                        } else if (injectionPointArgument.isNullable()) {
                            throw new DisabledBeanException("Nullable bean doesn't exist");
                        } else {
                            throw new NoSuchBeanException(argument, qualifier);
                        }
                    }

                }
            }
        }
        throw new UnsupportedOperationException("Cannot inject provider for Object type");
    }

    @Override
    public final boolean isAbstract() {
        return false;
    }

    @Override
    public final boolean isSingleton() {
        return false;
    }

    @Override
    public final ConstructorInjectionPoint<T> getConstructor() {
        return new ConstructorInjectionPoint<T>() {
            @Override
            public T invoke(Object... args) {
                throw new UnsupportedOperationException("Cannot be instantiated directly");
            }

            @Override
            public Argument<?>[] getArguments() {
                return Argument.ZERO_ARGUMENTS;
            }

            @Override
            public BeanDefinition<T> getDeclaringBean() {
                return AbstractProviderDefinition.this;
            }

            @Override
            public boolean requiresReflection() {
                return false;
            }
        };
    }

    @Override
    public final List<Argument<?>> getTypeArguments(Class<?> type) {
        if (type == getBeanType()) {
            return getTypeArguments();
        }
        return Collections.emptyList();
    }

    @Override
    public final List<Argument<?>> getTypeArguments() {
        return Collections.singletonList(
                Argument.OBJECT_ARGUMENT
        );
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return ANNOTATION_METADATA;
    }

    @Override
    public Qualifier<T> getDeclaredQualifier() {
        return null;
    }
}