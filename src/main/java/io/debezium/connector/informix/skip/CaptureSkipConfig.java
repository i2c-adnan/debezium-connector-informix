package io.debezium.connector.informix.skip;

import java.util.List;
import java.util.Objects;

public class CaptureSkipConfig {
    private final String tableName;
    private final List<SkipOperation> skipOperations;
    private final List<Condition> conditions;

    public CaptureSkipConfig(String tableName, List<SkipOperation> skipOperations, List<Condition> conditions) {
        this.tableName = tableName;
        this.skipOperations = skipOperations;
        this.conditions = conditions;
    }

    public String getTableName() {
        return tableName;
    }

    public List<SkipOperation> getSkipOperations() {
        return skipOperations;
    }

    public List<Condition> getConditions() {
        return conditions;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        CaptureSkipConfig that = (CaptureSkipConfig) o;
        return Objects.equals(tableName, that.tableName) &&
                Objects.equals(skipOperations, that.skipOperations) &&
                Objects.equals(conditions, that.conditions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tableName, skipOperations, conditions);
    }

    @Override
    public String toString() {
        return "CaptureSkipConfig{" +
                "tableName='" + tableName + '\'' +
                ", skipOperations=" + skipOperations +
                ", conditions=" + conditions +
                '}';
    }
}
