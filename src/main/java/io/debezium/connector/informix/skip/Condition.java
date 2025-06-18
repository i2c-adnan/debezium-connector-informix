package io.debezium.connector.informix.skip;

import java.util.Objects;

public class Condition {
    private final String columnName;
    private final String operator;
    private final Object value;
    private final String logicalOperator;
    private final Function function;

    public Condition(String columnName, String operator, Object value, String logicalOperator, Function function) {
        this.columnName = columnName;
        this.operator = operator;
        this.value = value;
        this.logicalOperator = logicalOperator != null ? logicalOperator.toUpperCase() : "AND";
        this.function = function;
    }

    public String getColumnName() {
        return columnName;
    }

    public Function getFunction() {
        return function;
    }

    public String getOperator() {
        return operator;
    }

    public Object getValue() {
        return value;
    }

    public String getLogicalOperator() {
        return logicalOperator;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Condition condition = (Condition) o;
        return Objects.equals(columnName, condition.columnName) &&
                Objects.equals(operator, condition.operator) &&
                Objects.equals(value, condition.value) &&
                Objects.equals(logicalOperator, condition.logicalOperator);
    }

    @Override
    public int hashCode() {
        return Objects.hash(columnName, operator, value, logicalOperator);
    }

    @Override
    public String toString() {
        return "Condition{" +
                "columnName='" + columnName + '\'' +
                ", operator='" + operator + '\'' +
                ", value=" + value +
                ", logicalOperator='" + logicalOperator + '\'' +
                '}';
    }
}
