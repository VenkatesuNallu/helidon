/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.helidon.microprofile.faulttolerance;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.AfterDeploymentValidation;
import javax.enterprise.inject.spi.AnnotatedConstructor;
import javax.enterprise.inject.spi.AnnotatedField;
import javax.enterprise.inject.spi.AnnotatedMethod;
import javax.enterprise.inject.spi.AnnotatedType;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.BeforeBeanDiscovery;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.ProcessManagedBean;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.faulttolerance.Asynchronous;
import org.eclipse.microprofile.faulttolerance.Bulkhead;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;

/**
 * Class FaultToleranceExtension.
 */
public class FaultToleranceExtension implements Extension {

    static final String MP_FT_NON_FALLBACK_ENABLED = "MP_Fault_Tolerance_NonFallback_Enabled";
    static final String MP_FT_METRICS_ENABLED = "MP_Fault_Tolerance_Metrics_Enabled";

    private static boolean isFaultToleranceEnabled = true;

    private static boolean isFaultToleranceMetricsEnabled = true;

    private Set<BeanMethod> registeredMethods;

    private static class BeanMethod {

        private final Class<?> beanClass;
        private final Method method;

        BeanMethod(Class<?> beanClass, Method method) {
            this.beanClass = beanClass;
            this.method = method;
        }

        Class<?> beanClass() {
            return beanClass;
        }

        Method method() {
            return method;
        }
    }

    /**
     * Returns a boolean indicating if FT is enabled.
     *
     * @return Fault tolerance enabled or disabled.
     */
    static boolean isFaultToleranceEnabled() {
        return isFaultToleranceEnabled;
    }

    /**
     * Returns a boolean indicating if FT metrics are enabled.
     *
     * @return Fault tolerance metrics enabled or disabled.
     */
    static boolean isFaultToleranceMetricsEnabled() {
        return isFaultToleranceMetricsEnabled;
    }

    /**
     * Adds interceptor bindings and annotated types.
     *
     * @param discovery Event information.
     * @param bm Bean manager instance.
     */
    void registerInterceptorBindings(@Observes BeforeBeanDiscovery discovery, BeanManager bm) {
        // Check if fault tolerance and its metrics are enabled
        final Config config = ConfigProvider.getConfig();
        isFaultToleranceEnabled = config.getOptionalValue(MP_FT_NON_FALLBACK_ENABLED, Boolean.class)
                .orElse(true);      // default is enabled
        isFaultToleranceMetricsEnabled = config.getOptionalValue(MP_FT_METRICS_ENABLED, Boolean.class)
                .orElse(true);      // default is enabled

        discovery.addInterceptorBinding(new InterceptorBindingAnnotatedType<>(bm.createAnnotatedType(Retry.class)));
        discovery.addInterceptorBinding(new InterceptorBindingAnnotatedType<>(bm.createAnnotatedType(CircuitBreaker.class)));
        discovery.addInterceptorBinding(new InterceptorBindingAnnotatedType<>(bm.createAnnotatedType(Timeout.class)));
        discovery.addInterceptorBinding(new InterceptorBindingAnnotatedType<>(bm.createAnnotatedType(Asynchronous.class)));
        discovery.addInterceptorBinding(new InterceptorBindingAnnotatedType<>(bm.createAnnotatedType(Bulkhead.class)));
        discovery.addInterceptorBinding(new InterceptorBindingAnnotatedType<>(bm.createAnnotatedType(Fallback.class)));

        discovery.addAnnotatedType(bm.createAnnotatedType(CommandInterceptor.class), CommandInterceptor.class.getName());
    }

    /**
     * Collects all FT methods in a set for later processing.
     *
     * @param event Event information.
     */
    void registerFaultToleranceMethods(@Observes ProcessManagedBean<?> event) {
        AnnotatedType<?> type = event.getAnnotatedBeanClass();
        for (AnnotatedMethod<?> method : type.getMethods()) {
            if (isFaultToleranceMethod(type.getJavaClass(), method.getJavaMember())) {
                getRegisteredMethods().add(new BeanMethod(type.getJavaClass(), method.getJavaMember()));
            }
        }
    }

    /**
     * Registers metrics for all FT methods.
     *
     * @param validation Event information.
     */
    void registerFaultToleranceMetrics(@Observes AfterDeploymentValidation validation) {
        if (FaultToleranceMetrics.enabled()) {
            getRegisteredMethods().stream().forEach(beanMethod -> {
                final Method method = beanMethod.method();
                final Class<?> beanClass = beanMethod.beanClass();

                // Counters for all methods
                FaultToleranceMetrics.registerMetrics(method);

                // Metrics depending on the annotations present
                if (MethodAntn.isAnnotationPresent(beanClass, method, Retry.class)) {
                    FaultToleranceMetrics.registerRetryMetrics(method);
                    new RetryAntn(beanClass, method).validate();
                }
                if (MethodAntn.isAnnotationPresent(beanClass, method, CircuitBreaker.class)) {
                    FaultToleranceMetrics.registerCircuitBreakerMetrics(method);
                    new CircuitBreakerAntn(beanClass, method).validate();
                }
                if (MethodAntn.isAnnotationPresent(beanClass, method, Timeout.class)) {
                    FaultToleranceMetrics.registerTimeoutMetrics(method);
                    new TimeoutAntn(beanClass, method).validate();
                }
                if (MethodAntn.isAnnotationPresent(beanClass, method, Bulkhead.class)) {
                    FaultToleranceMetrics.registerBulkheadMetrics(method);
                    new BulkheadAntn(beanClass, method).validate();
                }
                if (MethodAntn.isAnnotationPresent(beanClass, method, Fallback.class)) {
                    FaultToleranceMetrics.registerFallbackMetrics(method);
                    new FallbackAntn(beanClass, method).validate();
                }
            });
        }
    }

    /**
     * Lazy initialization of set.
     *
     * @return The set.
     */
    private Set<BeanMethod> getRegisteredMethods() {
        if (registeredMethods == null) {
            registeredMethods = new CopyOnWriteArraySet<>();
        }
        return registeredMethods;
    }

    /**
     * Returns the real class of this object, skipping proxies.
     *
     * @param object The object.
     * @return Its class.
     */
    static Class<?> getRealClass(Object object) {
        Class<?> result = object.getClass();
        while (result.isSynthetic()) {
            result = result.getSuperclass();
        }
        return result;
    }

    /**
     * Determines if a method has any fault tolerance annotations. Only {@code @Fallback}
     * is considered if fault tolerance is disabled.
     *
     * @param beanClass The bean.
     * @param method The method to check.
     * @return Outcome of test.
     */
    static boolean isFaultToleranceMethod(Class<?> beanClass, Method method) {
        return MethodAntn.isAnnotationPresent(beanClass, method, Retry.class)
                || MethodAntn.isAnnotationPresent(beanClass, method, CircuitBreaker.class)
                || MethodAntn.isAnnotationPresent(beanClass, method, Bulkhead.class)
                || MethodAntn.isAnnotationPresent(beanClass, method, Timeout.class)
                || MethodAntn.isAnnotationPresent(beanClass, method, Asynchronous.class)
                || MethodAntn.isAnnotationPresent(beanClass, method, Fallback.class);
    }

    /**
     * Annotated type for annotation.
     *
     * @param <T> Annotation type.
     */
    public static class InterceptorBindingAnnotatedType<T extends Annotation> implements AnnotatedType<T> {

        private final AnnotatedType<T> delegate;

        private final Set<Annotation> annotations;

        /**
         * Constructor.
         *
         * @param delegate Type delegate.
         */
        public InterceptorBindingAnnotatedType(AnnotatedType<T> delegate) {
            this.delegate = delegate;
            annotations = new HashSet<>(delegate.getAnnotations());
            annotations.add(LiteralCommandBinding.getInstance());
        }

        public Class<T> getJavaClass() {
            return delegate.getJavaClass();
        }

        public Type getBaseType() {
            return delegate.getBaseType();
        }

        public Set<Type> getTypeClosure() {
            return delegate.getTypeClosure();
        }

        public Set<AnnotatedConstructor<T>> getConstructors() {
            return delegate.getConstructors();
        }

        public Set<AnnotatedMethod<? super T>> getMethods() {
            return delegate.getMethods();
        }

        public Set<AnnotatedField<? super T>> getFields() {
            return delegate.getFields();
        }

        /**
         * Gets the annotation.
         *
         * @param annotationType Annotation type.
         * @param <R> Annotation type param.
         * @return Annotation instance.
         */
        @SuppressWarnings("unchecked")
        public <R extends Annotation> R getAnnotation(Class<R> annotationType) {
            if (CommandBinding.class.equals(annotationType)) {
                return (R) LiteralCommandBinding.getInstance();
            }
            return delegate.getAnnotation(annotationType);
        }

        public Set<Annotation> getAnnotations() {
            return annotations;
        }

        /**
         * Determines if annotation is present.
         *
         * @param annotationType Annotation type.
         * @return Outcome of test.
         */
        public boolean isAnnotationPresent(Class<? extends Annotation> annotationType) {
            return CommandBinding.class.equals(annotationType) || delegate.isAnnotationPresent(annotationType);
        }
    }
}
