package com.sky.job.notification.provider;

import com.sky.job.notification.NotificationChannelType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class NotificationProviderRegistry {
    private final Map<NotificationChannelType, NotificationProvider> providers;

    public NotificationProviderRegistry(List<NotificationProvider> providers) {
        EnumMap<NotificationChannelType, NotificationProvider> mapped = new EnumMap<>(NotificationChannelType.class);
        for (NotificationProvider provider : providers) {
            if (mapped.put(provider.type(), provider) != null) {
                throw new IllegalStateException("Duplicate notification provider: " + provider.type());
            }
        }
        this.providers = Map.copyOf(mapped);
    }

    public NotificationProvider get(NotificationChannelType type) {
        NotificationProvider provider = providers.get(type);
        if (provider == null) {
            throw new IllegalArgumentException("Unsupported notification channel type: " + type);
        }
        return provider;
    }
}
