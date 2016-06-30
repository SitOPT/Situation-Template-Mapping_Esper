package mapping;

import java.util.Arrays;

/**
 * This class contains the aggregation User defined functions for Esper.
 *
 */
public class Aggregation {

    public static boolean check_avg(String operator, Integer condValueSize, Double... args) {
        Double[] condValues = new Double[condValueSize];
        Double[] values = new Double[args.length - condValueSize];
        System.arraycopy(args, 0, condValues, 0, condValues.length);
        System.arraycopy(args, condValueSize, values, 0, values.length);

        int size = values.length;

        Double sum = 0.0;
        for (Double currentValue : values) {
            sum += currentValue;
        }
        Double average = sum / size;

        return compare(average, operator, condValues);
    }

    public static boolean check_min(String operator, Integer condValueSize, Double... args) {
        Double[] condValues = new Double[condValueSize];
        Double[] values = new Double[args.length - condValueSize];
        System.arraycopy(args, 0, condValues, 0, condValues.length);
        System.arraycopy(args, condValueSize, values, 0, values.length);

        Arrays.sort(values);
        return compare(values[0], operator, condValues);
    }

    public static boolean check_max(String operator, Integer condValueSize, Double... args) {
        Double[] condValues = new Double[condValueSize];
        Double[] values = new Double[args.length - condValueSize];
        System.arraycopy(args, 0, condValues, 0, condValues.length);
        System.arraycopy(args, condValueSize, values, 0, values.length);

        Arrays.sort(values);
        return compare(values[values.length - 1], operator, condValues);
    }

    private static boolean compare(Double aggregatedValue, String operator, Double... condValues) {

        if ("<".equals(operator)) {
            if (aggregatedValue < condValues[0]) {
                return true;
            }
            else {
                return false;
            }

        } else if (">".equals(operator)) {
            if (aggregatedValue > condValues[0]) {
                return true;
            }
            else {
                return false;
            }

        } else if ("=".equals(operator)) {
            if (aggregatedValue == condValues[0]) {
                return true;
            }
            else {
                return false;
            }

        } else if ("!=".equals(operator)) {
            if (aggregatedValue != condValues[0]) {
                return true;
            }
            else {
                return false;
            }

        } else if ("between".equals(operator)) {
            if (aggregatedValue >= condValues[0] && aggregatedValue <= condValues[1]) {
                return true;
            } else {
                return false;
            }
        }

        return false;
    }
}
