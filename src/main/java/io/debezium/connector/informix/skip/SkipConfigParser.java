package io.debezium.connector.informix.skip;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class SkipConfigParser {
    private static final Logger LOGGER = LoggerFactory.getLogger(SkipConfigParser.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static List<CaptureSkipConfig> parse(String skipConfigJson) {
        if (skipConfigJson == null || skipConfigJson.trim().isEmpty()) {
            return Collections.emptyList();
        }

        try {
            Map<String, List<Map<String, Object>>> rootMap = MAPPER.readValue(skipConfigJson,
                    new TypeReference<Map<String, List<Map<String, Object>>>>() {
                    });

            List<Map<String, Object>> configMaps = rootMap.get("captureSkipConfig");
            if (configMaps == null) {
                // Try parsing as direct list for backward compatibility
                configMaps = MAPPER.readValue(skipConfigJson,
                        new TypeReference<List<Map<String, Object>>>() {
                        });
            }

            List<CaptureSkipConfig> configs = new ArrayList<>();

            for (Map<String, Object> configMap : configMaps) {
                configs.add(parseConfig(configMap));
            }

            return configs;
        }
        catch (JsonProcessingException e) {
            LOGGER.error("Failed to parse skip configuration JSON: {}", skipConfigJson, e);
            return Collections.emptyList();
        }
    }

    @SuppressWarnings("unchecked")
    private static CaptureSkipConfig parseConfig(Map<String, Object> configMap) {
        String tableName = (String) configMap.get("tableName");
        List<String> operations = (List<String>) configMap.get("skipOperations");
        List<Map<String, Object>> conditionMaps = (List<Map<String, Object>>) configMap.get("conditions");

        List<SkipOperation> skipOperations = new ArrayList<>();
        for (String op : operations) {
            skipOperations.add(SkipOperation.fromCode(op));
        }

        List<Condition> conditions = new ArrayList<>();
        if (conditionMaps != null) {
            for (Map<String, Object> conditionMap : conditionMaps) {
                conditions.add(parseCondition(conditionMap));
            }
        }

        return new CaptureSkipConfig(tableName, skipOperations, conditions);
    }

    private static Condition parseCondition(Map<String, Object> conditionMap) {
        String columnName = (String) conditionMap.get("columnName");
        String operator = (String) conditionMap.get("operator");
        Object rawValue = conditionMap.get("value");
        String logicalOperator = (String) conditionMap.get("logicalOperator");

        // Handle function if present
        Function function = null;
        if ("FUNCTION".equals(operator)) {
            String functionName = (String) conditionMap.get("function");
            @SuppressWarnings("unchecked")
            List<Object> arguments = (List<Object>) conditionMap.get("arguments");
            function = new Function(functionName, arguments);

            // Use operator2 for the actual comparison
            operator = (String) conditionMap.get("operator2");
            if (operator == null) {
                operator = "="; // Default operator if not specified
            }
        }

        // Process value based on operator
        Object processedValue = processValue(operator, rawValue);

        return new Condition(columnName, operator, processedValue, logicalOperator, function);
    }

    @SuppressWarnings("unchecked")
    private static Object processValue(String operator, Object value) {
        if (value == null || "IS NULL".equals(operator) || "IS NOT NULL".equals(operator)) {
            return null;
        }

        if ("BETWEEN".equalsIgnoreCase(operator)) {
            if (!(value instanceof List) || ((List<?>) value).size() != 2) {
                throw new IllegalArgumentException("BETWEEN operator requires an array with exactly 2 elements");
            }
            List<?> range = (List<?>) value;
            return Arrays.asList(processScalarValue(range.get(0)), processScalarValue(range.get(1)));
        }

        if ("IN".equalsIgnoreCase(operator) || "NOT IN".equalsIgnoreCase(operator)) {
            if (!(value instanceof List)) {
                throw new IllegalArgumentException(operator + " operator requires an array of values");
            }
            List<?> list = (List<?>) value;
            return list.stream()
                    .map(SkipConfigParser::processScalarValue)
                    .collect(Collectors.toList());
        }

        return processScalarValue(value);
    }

    private static Object processScalarValue(Object value) {
        if (value instanceof String) {
            String strValue = (String) value;

            // Try parsing as ISO datetime
            if (strValue.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}")) {
                try {
                    return Instant.parse(strValue);
                }
                catch (Exception e) {
                    LOGGER.warn("Failed to parse datetime string: {}", strValue);
                }
            }

            // Try parsing as date
            if (strValue.matches("\\d{4}-\\d{2}-\\d{2}")) {
                try {
                    return LocalDate.parse(strValue);
                }
                catch (Exception e) {
                    LOGGER.warn("Failed to parse date string: {}", strValue);
                }
            }

            // Try parsing as number
            try {
                if (strValue.contains(".")) {
                    return Double.parseDouble(strValue);
                }
                return Long.parseLong(strValue);
            }
            catch (NumberFormatException e) {
                // Not a number, return as string
            }
        }

        return value;
    }
}
