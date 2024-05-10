package org.zalando.baigan.proxy.handler;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanFactory;
import org.zalando.baigan.model.Configuration;
import org.zalando.baigan.context.ContextProvider;
import org.zalando.baigan.repository.ConfigurationRepository;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ContextAwareConfigurationMethodInvocationHandlerTest {

    private static final String key = "test.interface.get.some.value";
    private static final String expectedConfigValue = "some value";
    private static final Configuration<String> config = new Configuration<>(key, "description", Set.of(), expectedConfigValue);

    private final ConfigurationRepository repository = mock(ConfigurationRepository.class);
    private final ConditionsProcessor conditionsProcessor = mock(ConditionsProcessor.class);
    private final BeanFactory beanFactory = mock(BeanFactory.class);
    private final ContextAwareConfigurationMethodInvocationHandler handler = new ContextAwareConfigurationMethodInvocationHandler();

    @BeforeEach
    public void setup() {
        when(beanFactory.getBean(ConfigurationRepository.class)).thenReturn(repository);
        when(beanFactory.getBean(ConditionsProcessor.class)).thenReturn(conditionsProcessor);
        handler.setBeanFactory(beanFactory);
    }

    @Test
    public void whenConfigurationRepositoryReturnsEmpty_shouldReturnNull() {
        when(repository.get(key)).thenReturn(Optional.empty());
        final Object result = handler.handleInvocation((TestInterface) () -> null, TestInterface.class.getDeclaredMethods()[0], new Object[0]);
        assertThat(result, nullValue());
    }

    @Test
    public void whenContextIsEmpty_shouldReturnValueFromConditionsProcessorCalledWithEmptyContext() {
        final Configuration<Integer> configWithWrongType = new Configuration<>(key, "description", Set.of(), 1);
        when(repository.get(key)).thenReturn(Optional.of(configWithWrongType));
        when(conditionsProcessor.process(configWithWrongType, Map.of())).thenReturn(1);
        final Object result = handler.handleInvocation((TestInterface) () -> null, TestInterface.class.getDeclaredMethods()[0], new Object[0]);
        assertThat(result, nullValue());
    }

    @Test
    public void whenConfigReturnTypeDoesNotMatchMethodReturnType_shouldReturnNull() {
        when(repository.get(key)).thenReturn(Optional.of(config));
        when(conditionsProcessor.process(config, Map.of())).thenReturn(expectedConfigValue);
        final Object result = handler.handleInvocation((TestInterface) () -> null, TestInterface.class.getDeclaredMethods()[0], new Object[0]);
        assertThat(result, equalTo(expectedConfigValue));
    }

    @Test
    public void whenContextProvidersExist_shouldReturnValueFromConditionsProcessorCalledWithResultingContext() {
        when(repository.get(key)).thenReturn(Optional.of(config));

        final String param1 = "param1";
        final String param2 = "param2";

        when(conditionsProcessor.process(config, Map.of(param1, "value1", param2, "value2"))).thenReturn(expectedConfigValue);
        final Object result = handler.handleInvocation((TestInterface) () -> null, TestInterface.class.getDeclaredMethods()[0],new Object[]{new TestContextProvider()});
        assertThat(result, equalTo(expectedConfigValue));
    }

    @Test
    public void whenContextProvidersAreEmpty_shouldReturnValueFromConditionsProcessorCalledWithEmptyContext() {
        when(repository.get(key)).thenReturn(Optional.of(config));

        when(conditionsProcessor.process(config, Map.of())).thenReturn(expectedConfigValue);
        final Object result = handler.handleInvocation((TestInterface) () -> null, TestInterface.class.getDeclaredMethods()[0], new Object[0]);
        assertThat(result, equalTo(expectedConfigValue));
    }


    @Test
    public void shouldFailWhenMultipleContextProvidersExistForSingleParameter() {
        when(repository.get(key)).thenReturn(Optional.of(config));

        final String param1 = "param1";
        final String param2 = "param2";

        when(conditionsProcessor.process(config, Map.of(param1, "value1", param2, "value2"))).thenReturn(expectedConfigValue);
        Assertions.assertThrows(RuntimeException.class,
                () -> handler.handleInvocation((TestInterface) () -> null, TestInterface.class.getDeclaredMethods()[0], new Object[]{new TestContextProvider(),new TestContextProvider()}),
                "Cannot have more than one context provider for the same context key");
    }

    interface TestInterface {
        String getSomeValue();
    }

    static class TestContextProvider implements ContextProvider {

        private final Set<String> PARAMS = Set.of("param1","param2");

        @Override
        public String getContextParam(@NotNull final String name) {
            if("param1".equalsIgnoreCase(name)){
                return "value1";
            }
            return "value2";
        }

        @Override
        public Set<String> getProvidedContexts() {
            return PARAMS;
        }
    }
}
