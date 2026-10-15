/*
 * Copyright 2026 Anton Tananaev (anton@traccar.org)
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
package org.traccar.notificators;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.traccar.config.Config;
import org.traccar.config.Keys;
import org.traccar.helper.DataConverter;
import org.traccar.helper.WebHelper;
import org.traccar.model.Device;
import org.traccar.model.Event;
import org.traccar.model.Group;
import org.traccar.model.Position;
import org.traccar.model.User;
import org.traccar.notification.MessageException;
import org.traccar.notification.NotificationFormatter;
import org.traccar.notification.NotificationMessage;
import org.traccar.session.cache.CacheManager;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

@Singleton
public class NotificatorWebhook extends Notificator {

    private final Client client;
    private final CacheManager cacheManager;

    private final String url;
    private final String authType;
    private final String apiKeyHeader;
    private final String apiKey;
    private final String bearerToken;
    private final String basicUser;
    private final String basicPassword;
    private final String customHeader;
    private final String customValue;

    public static class WebhookPayload {
        @JsonProperty("message")
        private NotificationMessage message;
        @JsonProperty("device")
        private Device device;
        @JsonProperty("group")
        private Group group;
        @JsonProperty("event")
        private Event event;
        @JsonProperty("position")
        private Position position;
    }

    @Inject
    public NotificatorWebhook(
            Config config, NotificationFormatter notificationFormatter, Client client, CacheManager cacheManager) {
        super(notificationFormatter);
        this.client = client;
        this.cacheManager = cacheManager;
        url = config.getString(Keys.NOTIFICATOR_WEBHOOK_URL);
        authType = config.getString(Keys.NOTIFICATOR_WEBHOOK_AUTH_TYPE);
        apiKeyHeader = config.getString(Keys.NOTIFICATOR_WEBHOOK_API_KEY_HEADER);
        apiKey = config.getString(Keys.NOTIFICATOR_WEBHOOK_API_KEY);
        bearerToken = config.getString(Keys.NOTIFICATOR_WEBHOOK_BEARER_TOKEN);
        basicUser = config.getString(Keys.NOTIFICATOR_WEBHOOK_BASIC_USER);
        basicPassword = config.getString(Keys.NOTIFICATOR_WEBHOOK_BASIC_PASSWORD);
        customHeader = config.getString(Keys.NOTIFICATOR_WEBHOOK_CUSTOM_HEADER);
        customValue = config.getString(Keys.NOTIFICATOR_WEBHOOK_CUSTOM_VALUE);
    }

    private Invocation.Builder createRequest() {
        var request = client.target(url).request();
        if (authType == null) {
            return request;
        }
        switch (authType) {
            case "x-api-key" -> {
                if (apiKey != null) {
                    request = request.header(apiKeyHeader != null ? apiKeyHeader : "x-api-key", apiKey);
                }
            }
            case "bearer" -> {
                if (bearerToken != null) {
                    request = request.header("Authorization", "Bearer " + bearerToken);
                }
            }
            case "basic" -> {
                if (basicUser != null && basicPassword != null) {
                    String credentials = DataConverter.printBase64(
                            (basicUser + ":" + basicPassword).getBytes(StandardCharsets.UTF_8));
                    request = request.header("Authorization", "Basic " + credentials);
                }
            }
            case "custom" -> {
                if (customHeader != null && customValue != null) {
                    request = request.header(customHeader, customValue);
                }
            }
            default -> { }
        }
        return request;
    }

    @Override
    public CompletableFuture<Void> sendAsync(
            User user, NotificationMessage message, Event event, Position position) {
        if (url == null || url.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        var payload = new WebhookPayload();
        payload.message = message;
        payload.event = event;
        payload.position = position;
        payload.device = cacheManager.getObject(Device.class, event.getDeviceId());
        if (payload.device != null && payload.device.getGroupId() > 0) {
            payload.group = cacheManager.getObject(Group.class, payload.device.getGroupId());
        }

        return WebHelper.post(createRequest(), Entity.json(payload), response -> {
            if (response.getStatus() / 100 != 2) {
                throw new MessageException(response.readEntity(String.class));
            }
        });
    }

}
