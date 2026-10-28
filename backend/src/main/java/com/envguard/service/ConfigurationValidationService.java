package com.envguard.service;

import com.envguard.exception.ApiException;
import com.envguard.secrets.SecretAvailability;
import com.envguard.secrets.SecretsService;
import com.envguard.util.ChecksumUtil;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.representer.Representer;

import java.util.*;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ConfigurationValidationService {

    private static final Pattern VAR_NAME = Pattern.compile("^[A-Z][A-Z0-9_]*$");
    private static final Pattern EXECUTABLE = Pattern.compile(
            "(?i).*(;\\s*|\\|\\s*|&&|`|\\$\\(|<script|javascript:).*");

    private final SecretsService secretsService;

    @Data
    @Builder
    public static class ValidationResult {
        private boolean valid;
        private String environment;
        private Map<String, String> variables;
        private Map<String, String> secretReferences;
        private String checksum;
        private List<String> errors;
        private List<String> warnings;
        private Map<String, String> secretAvailability;
    }

    @Data
    @Builder
    public static class ConfigDiff {
        private List<String> addedVariables;
        private List<String> removedVariables;
        private List<String> changedVariables;
        private List<String> addedSecretRefs;
        private List<String> removedSecretRefs;
        private List<String> changedSecretRefs;
    }

    public String toYaml(Map<String, Object> config) {
        if (config == null) {
            return null;
        }
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()), new Representer(options), options);
        return yaml.dump(config);
    }

    public String resolveYaml(String configYaml, Map<String, Object> config) {
        if (configYaml != null && !configYaml.isBlank()) {
            return configYaml;
        }
        if (config != null && !config.isEmpty()) {
            return toYaml(config);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public ValidationResult validate(String configYaml, String expectedEnvironment, Set<String> requiredVariables) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Map<String, String> secretAvailability = new LinkedHashMap<>();

        if (configYaml == null || configYaml.isBlank()) {
            return ValidationResult.builder()
                    .valid(false)
                    .errors(List.of("configYaml or config map is required"))
                    .warnings(List.of())
                    .variables(Map.of())
                    .secretReferences(Map.of())
                    .secretAvailability(Map.of())
                    .build();
        }

        if (EXECUTABLE.matcher(configYaml).find()) {
            errors.add("Configuration contains disallowed executable content");
        }

        Map<String, Object> root;
        try {
            Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
            Object loaded = yaml.load(configYaml);
            if (!(loaded instanceof Map)) {
                throw ApiException.badRequest("Config YAML root must be a mapping");
            }
            root = (Map<String, Object>) loaded;
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            return ValidationResult.builder()
                    .valid(false)
                    .errors(List.of("Invalid YAML: " + e.getMessage()))
                    .warnings(List.of())
                    .variables(Map.of())
                    .secretReferences(Map.of())
                    .secretAvailability(Map.of())
                    .build();
        }

        Object envObj = root.get("environment");
        String environment = envObj == null ? null : String.valueOf(envObj).trim().toLowerCase(Locale.ROOT);
        if (environment == null || environment.isBlank()) {
            if (expectedEnvironment != null && !expectedEnvironment.isBlank()) {
                environment = expectedEnvironment.trim().toLowerCase(Locale.ROOT);
            } else {
                errors.add("environment is required");
            }
        } else if (expectedEnvironment != null && !expectedEnvironment.isBlank()
                && !environment.equals(expectedEnvironment.trim().toLowerCase(Locale.ROOT))) {
            errors.add("Config environment '" + environment + "' does not match requested '"
                    + expectedEnvironment.trim().toLowerCase(Locale.ROOT) + "'");
        }

        Map<String, String> variables = toStringMap(root.get("variables"), "variables", errors);
        Map<String, String> secretRefs = toStringMap(root.get("secretReferences"), "secretReferences", errors);

        validateNames(variables.keySet(), "variables", errors);
        validateNames(secretRefs.keySet(), "secretReferences", errors);

        for (Map.Entry<String, String> e : variables.entrySet()) {
            if (e.getValue() != null && EXECUTABLE.matcher(e.getValue()).find()) {
                errors.add("Variable " + e.getKey() + " contains disallowed executable content");
            }
        }

        if (requiredVariables != null) {
            for (String req : requiredVariables) {
                if (!variables.containsKey(req) && !secretRefs.containsKey(req)) {
                    errors.add("Required variable missing: " + req);
                }
            }
        }

        for (Map.Entry<String, String> ref : secretRefs.entrySet()) {
            if (ref.getValue() == null || ref.getValue().isBlank()) {
                errors.add("Secret reference for " + ref.getKey() + " is blank");
                secretAvailability.put(ref.getKey(), SecretAvailability.UNAVAILABLE.name());
            } else {
                SecretAvailability availability = secretsService.checkAvailability(ref.getValue());
                secretAvailability.put(ref.getKey(), availability.name());
                switch (availability) {
                    case UNAVAILABLE -> errors.add("Secret reference not available: " + ref.getKey());
                    case UNKNOWN -> warnings.add("Secret availability UNKNOWN: " + ref.getKey());
                    case SKIPPED -> warnings.add("Secret availability SKIPPED: " + ref.getKey());
                    case AVAILABLE -> { /* ok */ }
                }
            }
        }

        String checksum = ChecksumUtil.canonicalConfigChecksum(environment, variables, secretRefs);

        return ValidationResult.builder()
                .valid(errors.isEmpty())
                .environment(environment)
                .variables(variables)
                .secretReferences(secretRefs)
                .checksum(checksum)
                .errors(errors)
                .warnings(warnings)
                .secretAvailability(secretAvailability)
                .build();
    }

    public ValidationResult validate(String configYaml, Set<String> requiredVariables) {
        return validate(configYaml, null, requiredVariables);
    }

    public ConfigDiff diff(ValidationResult left, ValidationResult right) {
        Set<String> leftVars = left.getVariables() == null ? Set.of() : left.getVariables().keySet();
        Set<String> rightVars = right.getVariables() == null ? Set.of() : right.getVariables().keySet();
        Set<String> leftSecrets = left.getSecretReferences() == null ? Set.of() : left.getSecretReferences().keySet();
        Set<String> rightSecrets = right.getSecretReferences() == null ? Set.of() : right.getSecretReferences().keySet();

        List<String> addedVars = rightVars.stream().filter(k -> !leftVars.contains(k)).sorted().toList();
        List<String> removedVars = leftVars.stream().filter(k -> !rightVars.contains(k)).sorted().toList();
        List<String> changedVars = leftVars.stream()
                .filter(rightVars::contains)
                .filter(k -> !Objects.equals(left.getVariables().get(k), right.getVariables().get(k)))
                .sorted().toList();

        List<String> addedSecrets = rightSecrets.stream().filter(k -> !leftSecrets.contains(k)).sorted().toList();
        List<String> removedSecrets = leftSecrets.stream().filter(k -> !rightSecrets.contains(k)).sorted().toList();
        List<String> changedSecrets = leftSecrets.stream()
                .filter(rightSecrets::contains)
                .filter(k -> !Objects.equals(left.getSecretReferences().get(k), right.getSecretReferences().get(k)))
                .sorted().toList();

        return ConfigDiff.builder()
                .addedVariables(addedVars)
                .removedVariables(removedVars)
                .changedVariables(changedVars)
                .addedSecretRefs(addedSecrets)
                .removedSecretRefs(removedSecrets)
                .changedSecretRefs(changedSecrets)
                .build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> toStringMap(Object node, String field, List<String> errors) {
        if (node == null) {
            return new LinkedHashMap<>();
        }
        if (!(node instanceof Map<?, ?> map)) {
            errors.add(field + " must be a mapping");
            return new LinkedHashMap<>();
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : map.entrySet()) {
            String key = String.valueOf(e.getKey());
            Object val = e.getValue();
            result.put(key, val == null ? null : String.valueOf(val));
        }
        return result;
    }

    private void validateNames(Set<String> names, String field, List<String> errors) {
        for (String name : names) {
            if (!VAR_NAME.matcher(name).matches()) {
                errors.add(field + " key '" + name + "' must match [A-Z][A-Z0-9_]*");
            }
        }
    }
}
