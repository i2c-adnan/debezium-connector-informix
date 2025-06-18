package io.debezium.connector.informix.skip;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.Temporal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SkipConfigProcessor {
    private final Logger logger = LoggerFactory.getLogger(SkipConfigProcessor.class);

    private final Map<String, CaptureSkipConfig> configMap;
    private final Map<Condition, Pattern> patternCache = new ConcurrentHashMap<>();
    private final Map<Object, Temporal> temporalCache = new ConcurrentHashMap<>();

    public SkipConfigProcessor(List<CaptureSkipConfig> configList) {
        this.configMap = new HashMap<>();
        for (CaptureSkipConfig config : configList) {
            if (config.getTableName() != null) {
                configMap.put(config.getTableName().toLowerCase(), config);
            }
        }
    }

    public boolean shouldSkip(String tableName, SkipOperation operation, Map<String, Object> rowData) {
        if (tableName == null || operation == null || rowData == null) {
            logger.debug("Skip check aborted - null parameters: tableName={}, operation={}, rowData={}",
                    tableName, operation, rowData);
            return false;
        }

        logger.debug("Evaluating skip for table='{}' operation='{}' rowData={}",
                tableName, operation, rowData);

        CaptureSkipConfig config = configMap.get(tableName.toLowerCase());
        if (config == null) {
            logger.debug("No skip config found for table '{}'", tableName);
            return false;
        }

        if (!config.getSkipOperations().contains(operation)) {
            logger.debug("Operation '{}' not in skip operations {} for table '{}'",
                    operation, config.getSkipOperations(), tableName);
            return false;
        }

        boolean shouldSkip = evaluateConditions(config.getConditions(), rowData);
        logger.info("Skip decision for table='{}' operation='{}': {}",
                tableName, operation, shouldSkip);
        return shouldSkip;
    }

    private boolean evaluateConditions(List<Condition> conditions, Map<String, Object> rowData) {
        if (conditions == null || conditions.isEmpty()) {
            logger.debug("No conditions specified, skipping based on table and operation match only");
            return true;
        }

        boolean result = true;
        String prevLogicalOperator = "AND";

        logger.debug("Evaluating {} conditions", conditions.size());
        for (Condition condition : conditions) {
            logger.debug("Evaluating condition: {}", condition);
            Object columnValue = rowData.get(condition.getColumnName());
            String operator = condition.getOperator().toUpperCase();

            if (operator.equals("IS NULL")) {
                boolean conditionResult = (columnValue == null);
                if (prevLogicalOperator.equals("AND")) {
                    result = result && conditionResult;
                }
                else if (prevLogicalOperator.equals("OR")) {
                    result = result || conditionResult;
                }
                prevLogicalOperator = condition.getLogicalOperator();
                continue;
            }

            if (columnValue == null) {
                logger.warn("Column '{}' not found in row data {}", condition.getColumnName(), rowData);
                if (prevLogicalOperator.equals("AND")) {
                    logger.debug("AND condition with null column value, returning false");
                    return false;
                }
                logger.debug("OR condition with null column value, continuing evaluation");
                continue;
            }

            boolean conditionResult = evaluateCondition(columnValue, condition);
            logger.debug("Condition result: {} {} {} = {}",
                    condition.getColumnName(), condition.getOperator(), condition.getValue(), conditionResult);

            if (prevLogicalOperator.equals("AND")) {
                result = result && conditionResult;
                logger.debug("AND operation: previous={}, current={}, result={}", result, conditionResult, result);
            }
            else if (prevLogicalOperator.equals("OR")) {
                result = result || conditionResult;
                logger.debug("OR operation: previous={}, current={}, result={}", result, conditionResult, result);
            }

            prevLogicalOperator = condition.getLogicalOperator();
        }

        return result;
    }

    @SuppressWarnings("unchecked")
    private boolean evaluateCondition(Object columnValue, Condition condition) {
        try {
            logger.debug("Evaluating condition: column='{}' operator='{}' value='{}' columnType={} valueType={}",
                    condition.getColumnName(), condition.getOperator(), condition.getValue(),
                    columnValue.getClass().getSimpleName(),
                    condition.getValue() != null ? condition.getValue().getClass().getSimpleName() : "null");
            switch (condition.getOperator().toUpperCase()) {
                case "=":
                case "EQUALS":
                    return compareValues(columnValue, "=", condition.getValue());
                case "<":
                case "LESS_THAN":
                    return compareValues(columnValue, "<", condition.getValue());
                case ">":
                case "GREATER_THAN":
                    return compareValues(columnValue, ">", condition.getValue());
                case "<=":
                case "LESS_THAN_OR_EQUALS":
                    return compareValues(columnValue, "<=", condition.getValue());
                case ">=":
                case "GREATER_THAN_OR_EQUALS":
                    return compareValues(columnValue, ">=", condition.getValue());
                case "<>":
                case "!=":
                case "NOT_EQUALS":
                    return compareValues(columnValue, "<>", condition.getValue());
                case "IN":
                    if (!(condition.getValue() instanceof List)) {
                        throw new IllegalArgumentException("Value for IN operator must be a List");
                    }
                    return ((List<?>) condition.getValue()).contains(columnValue);
                case "NOT_IN":
                    if (!(condition.getValue() instanceof List)) {
                        throw new IllegalArgumentException("Value for NOT IN operator must be a List");
                    }
                    return !((List<?>) condition.getValue()).contains(columnValue);
                case "LIKE":
                    if (!(columnValue instanceof String) || !(condition.getValue() instanceof String)) {
                        throw new IllegalArgumentException("LIKE operator can only be used with String values");
                    }
                    return patternCache.computeIfAbsent(condition, k -> {
                        String regex = ((String) k.getValue()).replace("%", ".*").replace("_", ".");
                        return Pattern.compile(regex);
                    }).matcher((String) columnValue).matches();
                case "NOT_LIKE":
                    if (!(columnValue instanceof String) || !(condition.getValue() instanceof String)) {
                        throw new IllegalArgumentException("NOT LIKE operator can only be used with String values");
                    }
                    return !patternCache.computeIfAbsent(condition, k -> {
                        String regex = ((String) k.getValue()).replace("%", ".*").replace("_", ".");
                        return Pattern.compile(regex);
                    }).matcher((String) columnValue).matches();
                case "BETWEEN":
                    if (!(condition.getValue() instanceof List) || ((List<?>) condition.getValue()).size() != 2) {
                        throw new IllegalArgumentException("Value for BETWEEN operator must be a List with exactly 2 elements");
                    }
                    List<?> range = (List<?>) condition.getValue();
                    return compareValues(columnValue, ">=", range.get(0)) && compareValues(columnValue, "<=", range.get(1));
                default:
                    throw new IllegalArgumentException("Unknown operator: " + condition.getOperator());
            }
        }
        catch (Exception e) {
            logger.error("Error evaluating condition: {} for value: {}", condition, columnValue, e);
            return false;
        }
    }

    public Logger getLogger() {
        return logger;
    }

    public boolean compareValues(Object value1, String operator, Object value2) {
        int comparison = compareValuesInternal(value1, value2);
        boolean result;

        switch (operator.toUpperCase()) {
            case "=":
            case "EQUALS":
                result = comparison == 0;
                break;
            case "<":
            case "LESS_THAN":
                result = comparison < 0;
                break;
            case ">":
            case "GREATER_THAN":
                result = comparison > 0;
                break;
            case "<=":
            case "LESS_THAN_OR_EQUALS":
                result = comparison <= 0;
                break;
            case ">=":
            case "GREATER_THAN_OR_EQUALS":
                result = comparison >= 0;
                break;
            case "<>":
            case "!=":
            case "NOT_EQUALS":
                result = comparison != 0;
                break;
            default:
                throw new IllegalArgumentException("Unknown operator: " + operator);
        }

        logger.trace("Value comparison: {} {} {} = {}", value1, operator, value2, result);
        return result;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private int compareValuesInternal(Object value1, Object value2) {
        if (value1 == null || value2 == null) {
            throw new IllegalArgumentException("Cannot compare null values");
        }

        // Handle numeric comparisons
        if (value1 instanceof Number && value2 instanceof Number) {
            // Handle Long values separately to maintain precision
            if (value1 instanceof Long || value2 instanceof Long) {
                long l1 = ((Number) value1).longValue();
                long l2 = ((Number) value2).longValue();
                return Long.compare(l1, l2);
            }
            // For other numeric types, use double comparison
            double d1 = ((Number) value1).doubleValue();
            double d2 = ((Number) value2).doubleValue();
            return Double.compare(d1, d2);
        }

        // Handle temporal comparisons (Instant, LocalDate, java.sql.Date)
        if (value1 instanceof Temporal || value2 instanceof Temporal ||
                value1 instanceof java.sql.Date || value2 instanceof java.sql.Date) {
            Temporal t1 = convertToTemporal(value1);
            Temporal t2 = convertToTemporal(value2);
            if (t1 instanceof Instant && t2 instanceof Instant) {
                return ((Instant) t1).compareTo((Instant) t2);
            }
            if (t1 instanceof LocalDate && t2 instanceof LocalDate) {
                return ((LocalDate) t1).compareTo((LocalDate) t2);
            }
        }

        // Handle string comparisons
        if (value1 instanceof String && value2 instanceof String) {
            return ((String) value1).compareTo((String) value2);
        }

        // Handle comparable objects
        if (value1 instanceof Comparable && value1.getClass().isInstance(value2)) {
            return ((Comparable) value1).compareTo(value2);
        }

        throw new IllegalArgumentException(
                String.format("Cannot compare values of types %s and %s",
                        value1.getClass().getName(),
                        value2.getClass().getName()));
    }

    private Temporal convertToTemporal(Object value) {
        return temporalCache.computeIfAbsent(value, k -> {
            if (k instanceof Temporal) {
                return (Temporal) k;
            }
            if (k instanceof java.sql.Date) {
                // Convert java.sql.Date to Instant at start of day UTC
                return ((java.sql.Date) k).toLocalDate().atStartOfDay().toInstant(java.time.ZoneOffset.UTC);
            }
            if (k instanceof Number) {
                // Treat numeric values as hours in a day
                int hour = ((Number) k).intValue();
                if (hour >= 0 && hour <= 23) {
                    return LocalDate.now().atStartOfDay().plusHours(hour).toInstant(java.time.ZoneOffset.UTC);
                }
                throw new IllegalArgumentException("Hour value must be between 0 and 23: " + hour);
            }
            if (k instanceof String) {
                String strValue = (String) k;
                try {
                    // Try parsing as Instant first (ISO-8601 format)
                    if (strValue.contains("T") || strValue.contains("Z")) {
                        return Instant.parse(strValue);
                    }
                    // For date-only strings, parse as LocalDate and convert to Instant at start of day UTC
                    try {
                        // Standard ISO date format (yyyy-MM-dd)
                        return LocalDate.parse(strValue, DateTimeFormatter.ISO_DATE)
                                .atStartOfDay()
                                .toInstant(java.time.ZoneOffset.UTC);
                    }
                    catch (Exception e1) {
                        try {
                            // Try custom date formats
                            DateTimeFormatter[] formatters = {
                                    DateTimeFormatter.ofPattern("yyyy/MM/dd"),
                                    DateTimeFormatter.ofPattern("MM/dd/yyyy"),
                                    DateTimeFormatter.ofPattern("dd/MM/yyyy"),
                                    DateTimeFormatter.ofPattern("MM-dd-yyyy"),
                                    DateTimeFormatter.ofPattern("dd-MM-yyyy")
                            };

                            for (DateTimeFormatter formatter : formatters) {
                                try {
                                    return LocalDate.parse(strValue, formatter)
                                            .atStartOfDay()
                                            .toInstant(java.time.ZoneOffset.UTC);
                                }
                                catch (Exception ignored) {
                                    // Continue to next formatter
                                }
                            }
                            // If we get here, none of the formatters worked
                            throw new IllegalArgumentException("Could not parse date string: " + strValue);
                        }
                        catch (Exception e2) {
                            logger.warn("Failed to parse temporal value: {}", strValue);
                            throw new IllegalArgumentException("Invalid temporal value: " + strValue);
                        }
                    }
                }
                catch (Exception e) {
                    logger.warn("Failed to parse temporal value: {}", strValue);
                    throw new IllegalArgumentException("Invalid temporal value: " + strValue);
                }
            }
            throw new IllegalArgumentException("Cannot convert to temporal: " + value);
        });
    }
}
