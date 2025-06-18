package io.debezium.connector.informix.skip;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.Temporal;
import java.util.List;
import java.util.Objects;

public class Function {
    private final String name;
    private final List<Object> arguments;

    public Function(String name, List<Object> arguments) {
        this.name = name;
        this.arguments = arguments;
    }

    public Object evaluate(Object value) {
        switch (name.toUpperCase()) {
            case "UPPER":
                validateArgCount(0);
                return value instanceof String ? ((String) value).toUpperCase() : value;

            case "LOWER":
                validateArgCount(0);
                return value instanceof String ? ((String) value).toLowerCase() : value;

            case "LENGTH":
                validateArgCount(0);
                return value instanceof String ? ((String) value).length() : 0;

            case "TRIM":
                validateArgCount(0);
                return value instanceof String ? ((String) value).trim() : value;

            case "SUBSTRING":
                validateArgCount(2);
                if (value instanceof String) {
                    int start = ((Number) arguments.get(0)).intValue();
                    int length = ((Number) arguments.get(1)).intValue();
                    String str = (String) value;
                    return str.substring(Math.min(start, str.length()),
                            Math.min(start + length, str.length()));
                }
                return value;

            case "DATE_PART":
                validateArgCount(1);
                String part = arguments.get(0).toString().toUpperCase();
                if (value instanceof Instant) {
                    Instant instant = (Instant) value;
                    switch (part) {
                        case "YEAR":
                            return instant.atZone(java.time.ZoneOffset.UTC).getYear();
                        case "MONTH":
                            return instant.atZone(java.time.ZoneOffset.UTC).getMonthValue();
                        case "DAY":
                            return instant.atZone(java.time.ZoneOffset.UTC).getDayOfMonth();
                        case "HOUR":
                            return instant.atZone(java.time.ZoneOffset.UTC).getHour();
                        case "MINUTE":
                            return instant.atZone(java.time.ZoneOffset.UTC).getMinute();
                        case "SECOND":
                            return instant.atZone(java.time.ZoneOffset.UTC).getSecond();
                    }
                }
                else if (value instanceof LocalDate) {
                    LocalDate date = (LocalDate) value;
                    switch (part) {
                        case "YEAR":
                            return date.getYear();
                        case "MONTH":
                            return date.getMonthValue();
                        case "DAY":
                            return date.getDayOfMonth();
                    }
                }
                return null;

            case "DATE_DIFF":
                validateArgCount(2);
                String unit = arguments.get(0).toString().toUpperCase();
                Object targetDate = arguments.get(1);

                if (value instanceof Temporal && targetDate instanceof Temporal) {
                    Instant date1 = value instanceof Instant ? (Instant) value
                            : ((LocalDate) value).atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
                    Instant date2 = targetDate instanceof Instant ? (Instant) targetDate
                            : ((LocalDate) targetDate).atStartOfDay(java.time.ZoneOffset.UTC).toInstant();

                    switch (unit) {
                        case "YEARS":
                            return ChronoUnit.YEARS.between(date1, date2);
                        case "MONTHS":
                            return ChronoUnit.MONTHS.between(date1, date2);
                        case "DAYS":
                            return ChronoUnit.DAYS.between(date1, date2);
                        case "HOURS":
                            return ChronoUnit.HOURS.between(date1, date2);
                        case "MINUTES":
                            return ChronoUnit.MINUTES.between(date1, date2);
                        case "SECONDS":
                            return ChronoUnit.SECONDS.between(date1, date2);
                    }
                }
                return null;

            default:
                throw new IllegalArgumentException("Unknown function: " + name);
        }
    }

    private void validateArgCount(int expectedCount) {
        if (arguments != null && arguments.size() != expectedCount) {
            throw new IllegalArgumentException(
                    String.format("Function %s expects %d arguments, but got %d",
                            name, expectedCount, arguments.size()));
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Function function = (Function) o;
        return Objects.equals(name, function.name) &&
                Objects.equals(arguments, function.arguments);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, arguments);
    }

    @Override
    public String toString() {
        return String.format("%s(%s)", name, arguments);
    }
}
