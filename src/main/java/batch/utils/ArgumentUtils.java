package batch.utils;

import org.springframework.boot.ApplicationArguments;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * application argument utility
 */
public abstract class ArgumentUtils {

    public static int getIntArgument(ApplicationArguments args, String name, int defaultValue) {
        Integer value = getIntegerArgument(args, name);
        if (value == null)
            return defaultValue;
        return value;
    }

    public static Integer getIntegerArgument(ApplicationArguments args, String name) {
        String argument = getArgument(args, name);
        if (!StringUtils.hasText(argument))
            return null;
        try {
            return Integer.parseInt(argument);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static long getLongArgument(ApplicationArguments args, String name, long defaultValue) {
        Long value = getLongArgument(args, name);
        if (value == null)
            return defaultValue;
        return value;
    }

    public static Long getLongArgument(ApplicationArguments args, String name) {
        String argument = getArgument(args, name);
        if (!StringUtils.hasText(argument))
            return null;
        try {
            return Long.parseLong(argument);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static String getArgument(ApplicationArguments args, String name) {
        List<String> optionValues = args.getOptionValues(name);
        if (CollectionUtils.isEmpty(optionValues))
            return "";

        return optionValues.get(0);
    }

    public static List<String> getArguments(ApplicationArguments args, String name) {
        List<String> optionValues = args.getOptionValues(name);
        if (CollectionUtils.isEmpty(optionValues))
            return Collections.emptyList();

        return optionValues.stream().filter(arg -> arg != null && !arg.isBlank()).collect(Collectors.toList());
    }
}
