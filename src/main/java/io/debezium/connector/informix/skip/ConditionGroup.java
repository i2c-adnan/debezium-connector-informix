package io.debezium.connector.informix.skip;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a group of conditions that can be nested with AND/OR operators.
 * For example: (status = 'inactive' AND last_login < '2023-01-01') OR (email LIKE '%example.com%')
 */
public class ConditionGroup {
    private final List<Object> conditions; // Can contain Condition or ConditionGroup
    private final String logicalOperator; // AND/OR operator for this group

    public ConditionGroup(List<Object> conditions, String logicalOperator) {
        this.conditions = conditions != null ? conditions : new ArrayList<>();
        this.logicalOperator = logicalOperator != null ? logicalOperator.toUpperCase() : "AND";
    }

    public boolean evaluate(Map<String, Object> rowData, SkipConfigProcessor processor) {
        if (conditions.isEmpty()) {
            return true;
        }

        boolean result = true;
        String prevOperator = "AND";

        for (Object condition : conditions) {
            boolean conditionResult;

            if (condition instanceof Condition) {
                conditionResult = evaluateCondition((Condition) condition, rowData, processor);
            }
            else if (condition instanceof ConditionGroup) {
                conditionResult = ((ConditionGroup) condition).evaluate(rowData, processor);
            }
            else {
                throw new IllegalArgumentException("Invalid condition type: " + condition.getClass());
            }

            if (prevOperator.equals("AND")) {
                result = result && conditionResult;
            }
            else if (prevOperator.equals("OR")) {
                result = result || conditionResult;
            }

            // Get the operator for the next iteration
            if (condition instanceof Condition) {
                prevOperator = ((Condition) condition).getLogicalOperator();
            }
            else if (condition instanceof ConditionGroup) {
                prevOperator = ((ConditionGroup) condition).getLogicalOperator();
            }
        }

        return result;
    }

    private boolean evaluateCondition(Condition condition, Map<String, Object> rowData, SkipConfigProcessor processor) {
        // Handle column comparison (e.g., updated_at > created_at)
        if (condition.getValue() instanceof String && ((String) condition.getValue()).startsWith("$")) {
            String compareColumnName = ((String) condition.getValue()).substring(1);
            Object columnValue = rowData.get(condition.getColumnName());
            Object compareValue = rowData.get(compareColumnName);

            if (compareValue == null) {
                processor.getLogger().warn("Compare column {} not found in row data", compareColumnName);
                return false;
            }

            return processor.compareValues(columnValue, condition.getOperator(), compareValue);
        }

        // Handle IS NULL and IS NOT NULL
        if (condition.getOperator().equalsIgnoreCase("IS NULL")) {
            return rowData.get(condition.getColumnName()) == null;
        }
        if (condition.getOperator().equalsIgnoreCase("IS NOT NULL")) {
            return rowData.get(condition.getColumnName()) != null;
        }

        // Handle function evaluation
        Object columnValue = rowData.get(condition.getColumnName());
        if (condition.getFunction() != null) {
            columnValue = condition.getFunction().evaluate(columnValue);
        }

        return processor.compareValues(columnValue, condition.getOperator(), condition.getValue());
    }

    public String getLogicalOperator() {
        return logicalOperator;
    }

    public List<Object> getConditions() {
        return conditions;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        ConditionGroup that = (ConditionGroup) o;
        return Objects.equals(conditions, that.conditions) &&
                Objects.equals(logicalOperator, that.logicalOperator);
    }

    @Override
    public int hashCode() {
        return Objects.hash(conditions, logicalOperator);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < conditions.size(); i++) {
            if (i > 0) {
                Object prevCondition = conditions.get(i - 1);
                String operator = prevCondition instanceof Condition ? ((Condition) prevCondition).getLogicalOperator()
                        : ((ConditionGroup) prevCondition).getLogicalOperator();
                sb.append(" ").append(operator).append(" ");
            }
            sb.append(conditions.get(i));
        }
        sb.append(")");
        return sb.toString();
    }
}
