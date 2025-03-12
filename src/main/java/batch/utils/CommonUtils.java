package batch.utils;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.GregorianCalendar;
import java.util.Locale;

public class CommonUtils {

    public static Timestamp getCurrentTimeStamp()   {
        return  new Timestamp(new GregorianCalendar().getTime().getTime());
    }

    public static String formatTimestamp(Timestamp timestamp, String format, java.util.Locale locale) {
        SimpleDateFormat formatter = new SimpleDateFormat(format, locale);
        return formatter.format(timestamp);
    }

    public static String getCurrentDateString(String format)  {
        return formatTimestamp(getCurrentTimeStamp(), format, Locale.KOREA);
    }

    public static int castInt(Object value) {
        int out = 0;
        if (value == null || "".equals(value)) {
            out = 0;
        } else if (value instanceof Number) {
            out = ((Number) value).intValue();
        } else {
            out = Integer.parseInt(value.toString().trim());
        }
        return out;
    }

    public static long castLong(Object value) {
        long out = 0;
        if (value == null || "".equals(value)) {
            out = 0;
        } else if (value instanceof Number) {
            out = ((Number) value).longValue();
        } else {
            out = Long.parseLong(value.toString().trim());
        }
        return out;
    }
}
