package io.quarkus.rest.runtime.jaxrs;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Priority;
import javax.ws.rs.ConstrainedTo;
import javax.ws.rs.Consumes;
import javax.ws.rs.Priorities;
import javax.ws.rs.Produces;
import javax.ws.rs.RuntimeType;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.client.ClientResponseFilter;
import javax.ws.rs.client.RxInvokerProvider;
import javax.ws.rs.core.Configuration;
import javax.ws.rs.core.Feature;
import javax.ws.rs.core.FeatureContext;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.ReaderInterceptor;
import javax.ws.rs.ext.WriterInterceptor;

import io.quarkus.rest.runtime.core.Serialisers;
import io.quarkus.rest.runtime.core.UnmanagedBeanFactory;
import io.quarkus.rest.runtime.model.ResourceReader;
import io.quarkus.rest.runtime.model.ResourceWriter;
import io.quarkus.rest.runtime.util.MultivaluedTreeMap;
import io.quarkus.rest.runtime.util.QuarkusMultivaluedHashMap;
import io.quarkus.rest.runtime.util.Types;

public class QuarkusRestConfiguration implements Configuration {

    private final RuntimeType runtimeType;
    private final Map<String, Object> properties;
    private final Map<Class<?>, Object> allInstances;
    private final List<Feature> enabledFeatures;
    private final Map<Class<?>, Map<Class<?>, Integer>> contracts;
    private final MultivaluedMap<Integer, ClientRequestFilter> requestFilters;
    private final MultivaluedMap<Integer, ClientResponseFilter> responseFilters;
    private final MultivaluedMap<Integer, WriterInterceptor> writerInterceptors;
    private final MultivaluedMap<Integer, ReaderInterceptor> readerInterceptors;
    private final MultivaluedMap<Class<?>, ResourceWriter> resourceWriters;
    private final MultivaluedMap<Class<?>, ResourceReader> resourceReaders;
    private final MultivaluedMap<Class<?>, RxInvokerProvider<?>> rxInvokerProviders;

    public QuarkusRestConfiguration(RuntimeType runtimeType) {
        this.runtimeType = runtimeType;
        this.properties = new HashMap<>();
        this.allInstances = new HashMap<>();
        this.enabledFeatures = new ArrayList<>();
        this.contracts = new HashMap<>();
        this.requestFilters = new MultivaluedTreeMap<>();
        this.responseFilters = new MultivaluedTreeMap<>(Collections.reverseOrder());
        this.readerInterceptors = new MultivaluedTreeMap<>();
        this.writerInterceptors = new MultivaluedTreeMap<>(Collections.reverseOrder());
        this.resourceReaders = new QuarkusMultivaluedHashMap<>();
        this.resourceWriters = new QuarkusMultivaluedHashMap<>();
        this.rxInvokerProviders = new QuarkusMultivaluedHashMap<>();
    }

    public QuarkusRestConfiguration(Configuration configuration) {
        this.runtimeType = configuration.getRuntimeType();
        this.properties = new HashMap<>(configuration.getProperties());
        if (configuration instanceof QuarkusRestConfiguration) {
            // we want to preserve all the registration metadata
            QuarkusRestConfiguration quarkusRestConfiguration = (QuarkusRestConfiguration) configuration;
            this.enabledFeatures = new ArrayList<>(quarkusRestConfiguration.enabledFeatures);
            this.contracts = new HashMap<>(quarkusRestConfiguration.contracts);
            this.allInstances = new HashMap<>(quarkusRestConfiguration.allInstances);
            this.requestFilters = new MultivaluedTreeMap<>();
            this.requestFilters.putAll(quarkusRestConfiguration.requestFilters);
            this.responseFilters = new MultivaluedTreeMap<>(Collections.reverseOrder());
            this.responseFilters.putAll(quarkusRestConfiguration.responseFilters);
            this.readerInterceptors = new MultivaluedTreeMap<>();
            this.readerInterceptors.putAll(quarkusRestConfiguration.readerInterceptors);
            this.writerInterceptors = new MultivaluedTreeMap<>(Collections.reverseOrder());
            this.writerInterceptors.putAll(quarkusRestConfiguration.writerInterceptors);
            this.resourceReaders = new QuarkusMultivaluedHashMap<>();
            this.resourceReaders.putAll(quarkusRestConfiguration.resourceReaders);
            this.resourceWriters = new QuarkusMultivaluedHashMap<>();
            this.resourceWriters.putAll(quarkusRestConfiguration.resourceWriters);
            this.rxInvokerProviders = new QuarkusMultivaluedHashMap<>();
            this.rxInvokerProviders.putAll(quarkusRestConfiguration.rxInvokerProviders);
        } else {
            this.allInstances = new HashMap<>();
            this.enabledFeatures = new ArrayList<>();
            this.contracts = new HashMap<>();
            this.requestFilters = new MultivaluedTreeMap<>();
            this.responseFilters = new MultivaluedTreeMap<>(
                    Collections.reverseOrder());
            this.readerInterceptors = new MultivaluedTreeMap<>();
            this.writerInterceptors = new MultivaluedTreeMap<>(Collections.reverseOrder());
            this.resourceReaders = new QuarkusMultivaluedHashMap<>();
            this.resourceWriters = new QuarkusMultivaluedHashMap<>();
            this.rxInvokerProviders = new QuarkusMultivaluedHashMap<>();
            // this is the best we can do - we don't have any of the metadata associated with the registration
            for (Object i : configuration.getInstances()) {
                register(i);
            }
        }
    }

    @Override
    public RuntimeType getRuntimeType() {
        return runtimeType;
    }

    @Override
    public Map<String, Object> getProperties() {
        return Collections.unmodifiableMap(properties);
    }

    @Override
    public Object getProperty(String name) {
        return properties.get(name);
    }

    @Override
    public Collection<String> getPropertyNames() {
        return properties.keySet();
    }

    @Override
    public boolean isEnabled(Feature feature) {
        return enabledFeatures.contains(feature);
    }

    @Override
    public boolean isEnabled(Class<? extends Feature> featureClass) {
        for (Feature enabledFeature : enabledFeatures) {
            if (enabledFeature.getClass().equals(featureClass)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isRegistered(Object component) {
        return allInstances.get(component.getClass()) == component;
    }

    @Override
    public boolean isRegistered(Class<?> componentClass) {
        return allInstances.containsKey(componentClass);
    }

    @Override
    public Map<Class<?>, Integer> getContracts(Class<?> componentClass) {
        Map<Class<?>, Integer> componentContracts = contracts.get(componentClass);
        if (componentContracts == null) {
            return Collections.emptyMap();
        }
        return componentContracts;
    }

    @Override
    public Set<Class<?>> getClasses() {
        return new HashSet<>(allInstances.keySet());
    }

    @Override
    public Set<Object> getInstances() {
        return new HashSet<>(allInstances.values());
    }

    public void addEnabledFeature(Feature feature) {
        enabledFeatures.add(feature);
    }

    public String toString(Object value) {
        // FIXME: this is weird
        return value.toString();
    }

    public void property(String name, Object value) {
        properties.put(name, value);
    }

    public void register(Class<?> componentClass) {
        try {
            register(componentClass.getDeclaredConstructor().newInstance());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void register(Class<?> componentClass, int priority) {
        try {
            register(componentClass.getDeclaredConstructor().newInstance(), priority);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void register(Class<?> componentClass, Class<?>... contracts) {
        try {
            register(componentClass.getDeclaredConstructor().newInstance(), contracts);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void register(Object component) {
        register(component, (Integer) null);
    }

    private void register(Object component, Integer priority) {
        if (allInstances.containsKey(component.getClass())) {
            return;
        }
        boolean added = false;
        if (component instanceof Feature) {
            Feature thisFeature = (Feature) component;
            added = true;
            if (thisFeature.configure(new ConfigFeatureContext())) {
                enabledFeatures.add(thisFeature);
            }
        }
        if (component instanceof ClientRequestFilter) {
            added = true;
            int effectivePriority = priority != null ? priority : determinePriority(component);
            requestFilters.add(effectivePriority, (ClientRequestFilter) component);
        }
        if (component instanceof ClientResponseFilter) {
            added = true;
            int effectivePriority = priority != null ? priority : determinePriority(component);
            responseFilters.add(effectivePriority, (ClientResponseFilter) component);
        }
        if (component instanceof WriterInterceptor) {
            added = true;
            int effectivePriority = priority != null ? priority : determinePriority(component);
            writerInterceptors.add(effectivePriority, (WriterInterceptor) component);
        }
        if (component instanceof ReaderInterceptor) {
            added = true;
            int effectivePriority = priority != null ? priority : determinePriority(component);
            readerInterceptors.add(effectivePriority, (ReaderInterceptor) component);
        }
        if (component instanceof MessageBodyReader) {
            added = true;
            Class<?> componentClass = component.getClass();
            ConstrainedTo constrainedTo = componentClass.getAnnotation(ConstrainedTo.class);
            if ((constrainedTo == null) || (constrainedTo.value() == runtimeType)) {
                ResourceReader resourceReader = new ResourceReader();
                resourceReader.setFactory(new UnmanagedBeanFactory(component));
                Consumes consumes = componentClass.getAnnotation(Consumes.class);
                resourceReader
                        .setMediaTypeStrings(
                                consumes != null ? Arrays.asList(consumes.value()) : Serialisers.WILDCARD_STRING_LIST);
                Type[] args = Types.findParameterizedTypes(componentClass, MessageBodyReader.class);
                resourceReaders.add(args != null && args.length == 1 ? Types.getRawType(args[0]) : Object.class,
                        resourceReader);
            }
        }
        if (component instanceof MessageBodyWriter) {
            added = true;
            Class<?> componentClass = component.getClass();
            ConstrainedTo constrainedTo = componentClass.getAnnotation(ConstrainedTo.class);
            if ((constrainedTo == null) || (constrainedTo.value() == runtimeType)) {
                ResourceWriter resourceWriter = new ResourceWriter();
                resourceWriter.setFactory(new UnmanagedBeanFactory(component));
                Produces produces = componentClass.getAnnotation(Produces.class);
                resourceWriter
                        .setMediaTypeStrings(
                                produces != null ? Arrays.asList(produces.value()) : Serialisers.WILDCARD_STRING_LIST);
                Type[] args = Types.findParameterizedTypes(componentClass, MessageBodyWriter.class);
                resourceWriters.add(args != null && args.length == 1 ? Types.getRawType(args[0]) : Object.class,
                        resourceWriter);
            }
        }
        if (component instanceof RxInvokerProvider) {
            added = true;
            Class<?> componentClass = component.getClass();
            Type[] args = Types.findParameterizedTypes(componentClass, RxInvokerProvider.class);
            rxInvokerProviders.add(args != null && args.length == 1 ? Types.getRawType(args[0]) : Object.class,
                    (RxInvokerProvider<?>) component);
        }
        if (added) {
            allInstances.put(component.getClass(), component);
        }
    }

    public void register(Object component, Class<?>[] contracts) {
        if (contracts == null || contracts.length == 0) {
            return;
        }
        Map<Class<?>, Integer> priorities = new HashMap<>();
        for (Class<?> i : contracts) {
            priorities.put(i, determinePriority(i));
        }
        register(component, priorities);
    }

    public void register(Class<?> componentClass, Map<Class<?>, Integer> contracts) {
        try {
            register(componentClass.getDeclaredConstructor().newInstance(), contracts);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void register(Object component, Map<Class<?>, Integer> componentContracts) {
        if (componentContracts == null || componentContracts.isEmpty()) {
            return;
        }
        Class<?> componentClass = component.getClass();
        if (allInstances.containsKey(componentClass)) {
            return;
        }
        boolean added = false;
        Integer priority = componentContracts.get(Feature.class);
        if (component instanceof Feature && priority != null) {
            Feature thisFeature = (Feature) component;
            added = true;
            if (thisFeature.configure(new ConfigFeatureContext())) {
                enabledFeatures.add(priority, (Feature) component);
            }
        }
        priority = componentContracts.get(ClientRequestFilter.class);
        if (component instanceof ClientRequestFilter && priority != null) {
            added = true;
            requestFilters.add(priority, (ClientRequestFilter) component);
        }
        priority = componentContracts.get(ClientResponseFilter.class);
        if (component instanceof ClientResponseFilter && priority != null) {
            added = true;
            responseFilters.add(priority, (ClientResponseFilter) component);
        }
        priority = componentContracts.get(WriterInterceptor.class);
        if (component instanceof WriterInterceptor && priority != null) {
            added = true;
            writerInterceptors.add(priority, (WriterInterceptor) component);
        }
        priority = componentContracts.get(ReaderInterceptor.class);
        if (component instanceof ReaderInterceptor && priority != null) {
            added = true;
            readerInterceptors.add(priority, (ReaderInterceptor) component);
        }
        priority = componentContracts.get(MessageBodyReader.class);
        if (component instanceof MessageBodyReader && priority != null) {
            added = true;
            ConstrainedTo constrainedTo = componentClass.getAnnotation(ConstrainedTo.class);
            if ((constrainedTo == null) || (constrainedTo.value() == runtimeType)) {
                ResourceReader resourceReader = new ResourceReader();
                resourceReader.setFactory(new UnmanagedBeanFactory(component));
                Consumes consumes = componentClass.getAnnotation(Consumes.class);
                resourceReader
                        .setMediaTypeStrings(
                                consumes != null ? Arrays.asList(consumes.value()) : Serialisers.WILDCARD_STRING_LIST);
                Type[] args = Types.findParameterizedTypes(componentClass, MessageBodyReader.class);
                resourceReaders.add(args != null && args.length == 1 ? Types.getRawType(args[0]) : Object.class,
                        resourceReader);
            }
        }
        priority = componentContracts.get(MessageBodyWriter.class);
        if (component instanceof MessageBodyWriter && priority != null) {
            added = true;
            ConstrainedTo constrainedTo = componentClass.getAnnotation(ConstrainedTo.class);
            if ((constrainedTo == null) || (constrainedTo.value() == runtimeType)) {
                ResourceWriter resourceWriter = new ResourceWriter();
                resourceWriter.setFactory(new UnmanagedBeanFactory(component));
                Produces produces = componentClass.getAnnotation(Produces.class);
                resourceWriter
                        .setMediaTypeStrings(
                                produces != null ? Arrays.asList(produces.value()) : Serialisers.WILDCARD_STRING_LIST);
                Type[] args = Types.findParameterizedTypes(componentClass, MessageBodyWriter.class);
                resourceWriters.add(args != null && args.length == 1 ? Types.getRawType(args[0]) : Object.class,
                        resourceWriter);
            }
        }
        if (added) {
            allInstances.put(componentClass, component);
            contracts.put(componentClass, componentContracts);
        }

    }

    public void register(Object component, int priority) {
        register(component, Integer.valueOf(priority));
    }

    public List<ClientRequestFilter> getRequestFilters() {
        if (requestFilters.isEmpty()) {
            return Collections.emptyList();
        }
        List<ClientRequestFilter> result = new ArrayList<>(requestFilters.size() * 2);
        for (List<ClientRequestFilter> requestFilters : requestFilters.values()) {
            result.addAll(requestFilters);
        }
        return result;
    }

    public List<ClientResponseFilter> getResponseFilters() {
        if (responseFilters.isEmpty()) {
            return Collections.emptyList();
        }
        List<ClientResponseFilter> result = new ArrayList<>(responseFilters.size() * 2);
        for (List<ClientResponseFilter> responseFilters : responseFilters.values()) {
            result.addAll(responseFilters);
        }
        return result;
    }

    public List<WriterInterceptor> getWriterInterceptors() {
        if (writerInterceptors.isEmpty()) {
            return Collections.emptyList();
        }
        List<WriterInterceptor> result = new ArrayList<>(writerInterceptors.size() * 2);
        for (List<WriterInterceptor> writerInterceptors : writerInterceptors.values()) {
            result.addAll(writerInterceptors);
        }
        return result;
    }

    public List<ReaderInterceptor> getReaderInterceptors() {
        if (readerInterceptors.isEmpty()) {
            return Collections.emptyList();
        }
        List<ReaderInterceptor> result = new ArrayList<>(readerInterceptors.size() * 2);
        for (List<ReaderInterceptor> readerInterceptors : readerInterceptors.values()) {
            result.addAll(readerInterceptors);
        }
        return result;
    }

    public RxInvokerProvider<?> getRxInvokerProvider(Class<?> wantedClass) {
        List<RxInvokerProvider<?>> candidates = rxInvokerProviders.get(wantedClass);
        if (candidates == null) {
            return null;
        }
        for (RxInvokerProvider<?> invokerProvider : candidates) {
            if (invokerProvider.isProviderFor(wantedClass))
                return invokerProvider;
        }
        return null;
    }

    // TODO: we could generate some kind of index at build time in order to obtain these values without using the annotation
    private int determinePriority(Object object) {
        return determinePriority(object.getClass());
    }

    private int determinePriority(Class<?> object) {
        Priority priority = object.getDeclaredAnnotation(Priority.class);
        if (priority == null) {
            return Priorities.USER;
        }
        return priority.value();
    }

    public MultivaluedMap<Class<?>, ResourceReader> getResourceReaders() {
        return resourceReaders;
    }

    public MultivaluedMap<Class<?>, ResourceWriter> getResourceWriters() {
        return resourceWriters;
    }

    private class ConfigFeatureContext implements FeatureContext {
        @Override
        public Configuration getConfiguration() {
            return QuarkusRestConfiguration.this;
        }

        @Override
        public FeatureContext property(String name, Object value) {
            QuarkusRestConfiguration.this.property(name, value);
            return this;
        }

        @Override
        public FeatureContext register(Class<?> componentClass) {
            QuarkusRestConfiguration.this.register(componentClass);
            return this;
        }

        @Override
        public FeatureContext register(Class<?> componentClass, int priority) {
            QuarkusRestConfiguration.this.register(componentClass, priority);
            return this;
        }

        @Override
        public FeatureContext register(Class<?> componentClass, Class<?>... contracts) {
            QuarkusRestConfiguration.this.register(componentClass, contracts);
            return this;
        }

        @Override
        public FeatureContext register(Class<?> componentClass, Map<Class<?>, Integer> contracts) {
            QuarkusRestConfiguration.this.register(componentClass, contracts);
            return this;
        }

        @Override
        public FeatureContext register(Object component) {
            QuarkusRestConfiguration.this.register(component);
            return this;
        }

        @Override
        public FeatureContext register(Object component, int priority) {
            QuarkusRestConfiguration.this.register(component, priority);
            return this;
        }

        @Override
        public FeatureContext register(Object component, Class<?>... contracts) {
            QuarkusRestConfiguration.this.register(component, contracts);
            return this;
        }

        @Override
        public FeatureContext register(Object component, Map<Class<?>, Integer> contracts) {
            QuarkusRestConfiguration.this.register(component, contracts);
            return this;
        }
    }
}
