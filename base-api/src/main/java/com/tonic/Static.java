package com.tonic;

import com.google.inject.Injector;
import com.tonic.api.TClient;
import com.tonic.headless.HeadlessMode;
import com.tonic.model.RuneLite;
import lombok.Getter;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public class Static
{
    @Getter
    private static boolean headless = false;
    private static Object CLIENT_OBJECT;
    private static RuneLite RL;

    public static <T> T getClient()
    {
        return (T) CLIENT_OBJECT;
    }
    public static RuneLite getRuneLite()
    {
        return RL;
    }

    public static Injector getInjector()
    {
        return RL.getInjector().getInjector();
    }

    public static void set(Object object, String name)
    {
        switch (name)
        {
            case "RL_CLIENT":
                CLIENT_OBJECT = Objects.requireNonNull(object, "RL_CLIENT cannot be null");
                break;
            case "RL":
                RL = (RuneLite) Objects.requireNonNull(object, "RL cannot be null");
                break;
            default:
                throw new IllegalArgumentException("Unknown class name: " + name);
        }
    }

    /**
     * invoke on client thread with return
     *
     * @param supplier runnable block
     * @return return value
     */
    public static <T> T invoke(Supplier<T> supplier) {
        TClient T_CLIENT = (TClient) CLIENT_OBJECT;
        if (!T_CLIENT.isClientThread()) {
            CompletableFuture<T> future = new CompletableFuture<>();
            Runnable runnable = () -> future.complete(supplier.get());
            invoke(runnable);
            return future.join();
        } else {
            return supplier.get();
        }
    }

    public static void invoke(Runnable runnable) {
        TClient T_CLIENT = (TClient) CLIENT_OBJECT;
        if (!T_CLIENT.isClientThread()) {
            getRuneLite().getClientThread().invoke(runnable);
        } else {
            runnable.run();
        }
    }

    public static void post(Object event)
    {
        getRuneLite().getEventBus().post(event);
    }

    public static void setHeadless(boolean headless) {
        Static.headless = headless;
        HeadlessMode.toggleHeadless(headless);
    }
}
