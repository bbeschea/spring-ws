/*
 * Copyright 2005-2010 the original author or authors.
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

package org.springframework.ws.server.endpoint.mapping;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContextException;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.ws.context.MessageContext;
import org.springframework.ws.server.endpoint.MethodEndpoint;

/**
 * Abstract base class for {@link MethodEndpoint} mappings.
 * <p/>
 * Subclasses typically implement {@link org.springframework.beans.factory.config.BeanPostProcessor} to look for beans
 * that qualify as endpoint. The methods of this bean are then registered under a specific key with {@link
 * #registerEndpoint(String,MethodEndpoint)}.
 *
 * @author Arjen Poutsma
 * @since 1.0.0
 */
public abstract class AbstractMethodEndpointMapping<T> extends AbstractEndpointMapping {

    private final Map<T, MethodEndpoint> endpointMap = new HashMap<T, MethodEndpoint>();

    /**
     * Lookup an endpoint for the given message. The extraction of the endpoint key is delegated to the concrete
     * subclass.
     *
     * @return the looked up endpoint, or <code>null</code>
     * @see #getLookupKeyForMessage(MessageContext)
     */
    @Override
    protected Object getEndpointInternal(MessageContext messageContext) throws Exception {
        T key = getLookupKeyForMessage(messageContext);
        if (key == null) {
            return null;
        }
        if (logger.isDebugEnabled()) {
            logger.debug("Looking up endpoint for [" + key + "]");
        }
        return lookupEndpoint(key);
    }

    /**
     * Returns the the endpoint keys for the given message context.
     *
     * @return the registration keys
     */
    protected abstract T getLookupKeyForMessage(MessageContext messageContext) throws Exception;

    /**
     * Looks up an endpoint instance for the given keys. All keys are tried in order.
     *
     * @param key key the beans are mapped to
     * @return the associated endpoint instance, or <code>null</code> if not found
     */
    protected MethodEndpoint lookupEndpoint(T key) {
        return endpointMap.get(key);
    }

    /**
     * Register the given endpoint instance under the key.
     *
     * @param key      the lookup key
     * @param endpoint the method endpoint instance
     * @throws BeansException if the endpoint could not be registered
     */
    protected void registerEndpoint(T key, MethodEndpoint endpoint) throws BeansException {
        Object mappedEndpoint = endpointMap.get(key);
        if (mappedEndpoint != null) {
            throw new ApplicationContextException("Cannot map endpoint [" + endpoint + "] on registration key [" + key +
                    "]: there's already endpoint [" + mappedEndpoint + "] mapped");
        }
        if (endpoint == null) {
            throw new ApplicationContextException("Could not find endpoint for key [" + key + "]");
        }
        endpointMap.put(key, endpoint);
        if (logger.isDebugEnabled()) {
            logger.debug("Mapped [" + key + "] onto endpoint [" + endpoint + "]");
        }
    }

    /**
     * Helper method that registers the methods of the given bean. This method iterates over the methods of the bean,
     * and calls {@link #getLookupKeyForMethod(Method)} for each. If this returns a string, the method is registered
     * using {@link #registerEndpoint(T,MethodEndpoint)}.
     *
     * @see #getLookupKeyForMethod(Method)
     */
    protected void registerMethods(final Object endpoint) {
        Assert.notNull(endpoint, "'endpoint' must not be null");
        Class<?> endpointClass = getEndpointClass(endpoint);
        ReflectionUtils.doWithMethods(endpointClass, new ReflectionUtils.MethodCallback() {

            public void doWith(Method method) throws IllegalArgumentException, IllegalAccessException {
                T key = getLookupKeyForMethod(method);
                if (key != null) {
                    registerEndpoint(key, new MethodEndpoint(endpoint, method));
                }
            }
        });
    }

    /**
     * Helper method that registers the methods of the given class. This method iterates over the methods of the class,
     * and calls {@link #getLookupKeyForMethod(Method)} for each. If this returns a string, the method is registered
     * using {@link #registerEndpoint(String,MethodEndpoint)}.
     *
     * @see #getLookupKeyForMethod(Method)
     */
    protected void registerMethods(final String beanName) {
        Assert.hasText(beanName, "'beanName' must not be empty");
        Class<?> endpointClass = getApplicationContext().getType(beanName);
        ReflectionUtils.doWithMethods(endpointClass, new ReflectionUtils.MethodCallback() {

            public void doWith(Method method) throws IllegalArgumentException, IllegalAccessException {
                T key = getLookupKeyForMethod(method);
                if (key != null) {
                    registerEndpoint(key, new MethodEndpoint(beanName, getApplicationContext(), method));
                }
            }

        });
    }

    /**
     * Returns the the endpoint keys for the given method. Returns <code>null</code> if the method is not to be
     * registered, which is the default.
     *
     * @param method the method
     * @return a registration key, or <code>null</code> if the method is not to be registered
     */
    protected T getLookupKeyForMethod(Method method) {
        return null;
    }

    /**
     * Return the class or interface to use for method reflection.
     * <p/>
     * Default implementation delegates to {@link AopUtils#getTargetClass(Object)}.
     *
     * @param endpoint the bean instance (might be an AOP proxy)
     * @return the bean class to expose
     */
    protected Class<?> getEndpointClass(Object endpoint) {
        if (AopUtils.isJdkDynamicProxy(endpoint)) {
            throw new IllegalArgumentException(ClassUtils.getShortName(getClass()) +
                    " does not work with JDK Dynamic Proxies. " +
                    "Please use CGLIB proxies, by setting proxy-target-class=\"true\" on the aop:aspectj-autoproxy " +
                    "or aop:config element.");
        }
        return AopUtils.getTargetClass(endpoint);
    }
}
