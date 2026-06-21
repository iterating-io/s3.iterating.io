package io.iterating.s3.nats.messaging;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Duration;

import org.junit.jupiter.api.Test;

import io.iterating.s3.nats.config.NatsProperties;
import io.nats.client.JetStream;
import io.nats.client.JetStreamSubscription;
import io.nats.client.PullSubscribeOptions;

public class NatsJetStreamConsumerSubscribeTest {

    @Test
    void subscribe_buildsOptionsFromProperties() throws Exception {
        NatsProperties props = new NatsProperties(
                "nats://localhost:4222",
                "",
                "test-conn",
                "test-stream",
                "test-durable",
                "test-subject",
                1,
                Duration.ofSeconds(1),
                Duration.ofSeconds(1));

        final Object[] captured = new Object[2];

        // Prepare a JetStream invocation handler that captures subscribe arguments
        InvocationHandler handler = (proxy, method, args) -> {
            if ("subscribe".equals(method.getName()) && args != null && args.length >= 2) {
                captured[0] = args[0]; // subject
                captured[1] = args[1]; // options
                // return a simple JetStreamSubscription proxy that returns empty list for fetch
                return Proxy.newProxyInstance(JetStreamSubscription.class.getClassLoader(),
                        new Class[] { JetStreamSubscription.class },
                        (p, m, a) -> {
                            if ("fetch".equals(m.getName())) {
                                return java.util.Collections.emptyList();
                            }
                            return null;
                        });
            }
            return null;
        };

        JetStream jsProxy = (JetStream) Proxy.newProxyInstance(JetStream.class.getClassLoader(),
                new Class[] { JetStream.class }, handler);

        java.lang.reflect.InvocationHandler connHandler = (proxy, method, args) -> {
            if ("jetStream".equals(method.getName())) return jsProxy;
            if ("flush".equals(method.getName())) return null;
            return null;
        };

        io.nats.client.Connection connProxy = (io.nats.client.Connection) Proxy.newProxyInstance(
                io.nats.client.Connection.class.getClassLoader(),
                new Class[] { io.nats.client.Connection.class }, connHandler);

        NatsJetStreamConsumer consumer = new NatsJetStreamConsumer(connProxy, props, (p, s) -> {});

        

        Method subscribeMethod = NatsJetStreamConsumer.class.getDeclaredMethod("subscribe", JetStream.class);
        subscribeMethod.setAccessible(true);
        Object result = subscribeMethod.invoke(consumer, jsProxy);

        assertNotNull(result);
        assertEquals("test-subject", captured[0]);
        assertNotNull(captured[1]);
        Object opts = captured[1];
        System.out.println("Captured options class: " + (opts == null ? "null" : opts.getClass().getName()));
        // Reflectively try common accessor names for stream/durable
        String streamVal = tryGetString(opts, "stream", "getStream", "streamName", "getStreamName", "getStreamName");
        String durableVal = tryGetString(opts, "durable", "getDurable", "durableName", "getDurableName");
        if (streamVal == null && opts != null) {
            System.out.println("Available methods: ");
            for (Method m : opts.getClass().getDeclaredMethods()) {
                System.out.println("  " + m.getName());
            }
            System.out.println("Available fields: ");
            for (java.lang.reflect.Field f : opts.getClass().getDeclaredFields()) {
                System.out.println("  " + f.getName());
            }
        }
        assertEquals(props.stream(), streamVal);
        assertEquals(props.durable(), durableVal);
    }

    private static String tryGetString(Object obj, String... names) {
        if (obj == null) return null;
        Class<?> c = obj.getClass();
        for (String name : names) {
            try {
                try {
                    Method m = c.getMethod(name);
                    Object r = m.invoke(obj);
                    if (r != null) return r.toString();
                } catch (NoSuchMethodException e) {
                    // try declared
                    try {
                        Method m = c.getDeclaredMethod(name);
                        m.setAccessible(true);
                        Object r = m.invoke(obj);
                        if (r != null) return r.toString();
                    } catch (NoSuchMethodException ignored) {}
                }
            } catch (Exception ex) {
                // ignore and try next
            }
            try {
                try {
                    java.lang.reflect.Field f = c.getField(name);
                    Object r = f.get(obj);
                    if (r != null) return r.toString();
                } catch (NoSuchFieldException e) {
                    try {
                        java.lang.reflect.Field f = c.getDeclaredField(name);
                        f.setAccessible(true);
                        Object r = f.get(obj);
                        if (r != null) return r.toString();
                    } catch (NoSuchFieldException ignored) {}
                }
            } catch (Exception ex) {
                // ignore
            }
        }
        return null;
    }
}
