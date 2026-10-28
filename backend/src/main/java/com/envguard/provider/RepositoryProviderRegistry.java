package com.envguard.provider;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class RepositoryProviderRegistry {

    private final Map<String, RepositoryProvider> providers;

    public RepositoryProviderRegistry(List<RepositoryProvider> providerList) {
        this.providers = providerList.stream()
                .collect(Collectors.toMap(
                        p -> p.getProviderName().toUpperCase(Locale.ROOT),
                        Function.identity()));
    }

    public RepositoryProvider get(String name) {
        RepositoryProvider provider = providers.get(name.toUpperCase(Locale.ROOT));
        if (provider == null) {
            throw new IllegalArgumentException("Unknown repository provider: " + name);
        }
        return provider;
    }
}
