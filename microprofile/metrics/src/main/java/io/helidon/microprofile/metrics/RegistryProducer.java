/*
 * Copyright (c) 2018, 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.microprofile.metrics;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;

import io.helidon.common.metrics.InternalMetricRegistryBridge;
import io.helidon.metrics.RegistryFactory;

import org.eclipse.microprofile.metrics.MetricRegistry.Type;
import org.eclipse.microprofile.metrics.annotation.RegistryType;

/**
 * Producer of each type of registry.
 */
@ApplicationScoped
public final class RegistryProducer {
    private static final RegistryFactory REGISTRY_FACTORY = RegistryFactory.getInstance();

    private RegistryProducer() {
    }

    @Produces
    public static InternalMetricRegistryBridge getDefaultRegistry() {
        return getApplicationRegistry();
    }

    @Produces
    @RegistryType(type = Type.APPLICATION)
    public static InternalMetricRegistryBridge getApplicationRegistry() {
        return REGISTRY_FACTORY.getRegistry(Type.APPLICATION);
    }

    @Produces
    @RegistryType(type = Type.BASE)
    public static InternalMetricRegistryBridge getBaseRegistry() {
        return REGISTRY_FACTORY.getRegistry(Type.BASE);
    }

    @Produces
    @RegistryType(type = Type.VENDOR)
    public static InternalMetricRegistryBridge getVendorRegistry() {
        return REGISTRY_FACTORY.getRegistry(Type.VENDOR);
    }

    /**
     * Clears Application registry. This is required for the Metric TCKs as they
     * all run on the same VM and must not interfere with each other.
     */
    static void clearApplicationRegistry() {
        InternalMetricRegistryBridge applicationRegistry = getApplicationRegistry();
        applicationRegistry.getNames().forEach(applicationRegistry::remove);
    }
}
