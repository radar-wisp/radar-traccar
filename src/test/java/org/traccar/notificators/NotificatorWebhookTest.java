package org.traccar.notificators;

import org.junit.jupiter.api.Test;
import org.traccar.config.Config;
import org.traccar.config.ConfigKey;
import org.traccar.config.Keys;
import org.traccar.model.Device;
import org.traccar.model.Event;
import org.traccar.model.User;
import org.traccar.notification.NotificationFormatter;
import org.traccar.notification.NotificationMessage;
import org.traccar.session.cache.CacheManager;

import jakarta.ws.rs.client.AsyncInvoker;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.client.InvocationCallback;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Response;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class NotificatorWebhookTest {

    private Invocation.Builder builder;
    private AsyncInvoker async;
    private NotificatorWebhook notificator;

    private void init(Map<ConfigKey<String>, String> values, int status) {
        var config = new Config();
        values.forEach(config::setString);
        var client = mock(Client.class);
        var target = mock(WebTarget.class);
        builder = mock(Invocation.Builder.class);
        async = mock(AsyncInvoker.class);
        when(client.target(any(String.class))).thenReturn(target);
        when(target.request()).thenReturn(builder);
        when(builder.header(any(String.class), any())).thenReturn(builder);
        when(builder.async()).thenReturn(async);
        var response = mock(Response.class);
        when(response.getStatus()).thenReturn(status);
        when(response.readEntity(String.class)).thenReturn("error body");
        doAnswer(invocation -> {
            ((InvocationCallback<Response>) invocation.getArgument(1)).completed(response);
            return null;
        }).when(async).post(any(Entity.class), any(InvocationCallback.class));
        var cache = mock(CacheManager.class);
        var device = new Device();
        device.setId(1);
        when(cache.getObject(Device.class, 1L)).thenReturn(device);
        notificator = new NotificatorWebhook(config, mock(NotificationFormatter.class), client, cache);
    }

    private void send() {
        var event = new Event("deviceOnline", 1);
        notificator.sendAsync(new User(), new NotificationMessage("s", "d", "b", false), event, null).join();
    }

    @Test
    public void testBearer() {
        init(Map.of(Keys.NOTIFICATOR_WEBHOOK_URL, "http://x", Keys.NOTIFICATOR_WEBHOOK_AUTH_TYPE, "bearer",
                Keys.NOTIFICATOR_WEBHOOK_BEARER_TOKEN, "tok"), 200);
        send();
        verify(builder).header("Authorization", "Bearer tok");
        verify(async).post(any(Entity.class), any(InvocationCallback.class));
    }

    @Test
    public void testBasic() {
        init(Map.of(Keys.NOTIFICATOR_WEBHOOK_URL, "http://x", Keys.NOTIFICATOR_WEBHOOK_AUTH_TYPE, "basic",
                Keys.NOTIFICATOR_WEBHOOK_BASIC_USER, "u", Keys.NOTIFICATOR_WEBHOOK_BASIC_PASSWORD, "p"), 200);
        send();
        verify(builder).header("Authorization", "Basic dTpw");
    }

    @Test
    public void testApiKeyDefaultHeader() {
        init(Map.of(Keys.NOTIFICATOR_WEBHOOK_URL, "http://x", Keys.NOTIFICATOR_WEBHOOK_AUTH_TYPE, "x-api-key",
                Keys.NOTIFICATOR_WEBHOOK_API_KEY, "k"), 200);
        send();
        verify(builder).header("x-api-key", "k");
    }

    @Test
    public void testNoUrlSkips() {
        init(Map.<ConfigKey<String>, String>of(), 200);
        send();
        verify(async, never()).post(any(Entity.class), any(InvocationCallback.class));
    }

    @Test
    public void testErrorStatusFails() {
        init(Map.of(Keys.NOTIFICATOR_WEBHOOK_URL, "http://x"), 500);
        var ex = assertThrows(RuntimeException.class, this::send);
        assertNotNull(ex.getCause());
        assertEquals("error body", ex.getCause().getMessage());
    }

}
