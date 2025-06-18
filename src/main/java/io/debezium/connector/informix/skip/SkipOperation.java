package io.debezium.connector.informix.skip;

public enum SkipOperation {
    INSERT("i"),
    UPDATE("u"),
    DELETE("d"),
    TRUNCATE("t");

    private final String code;

    SkipOperation(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static SkipOperation fromCode(String code) {
        for (SkipOperation op : values()) {
            if (op.getCode().equalsIgnoreCase(code)) {
                return op;
            }
        }
        throw new IllegalArgumentException("Invalid operation code: " + code);
    }
}
