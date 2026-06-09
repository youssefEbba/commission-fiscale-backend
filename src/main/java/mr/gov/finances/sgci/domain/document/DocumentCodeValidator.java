package mr.gov.finances.sgci.domain.document;

import java.util.Locale;
import java.util.regex.Pattern;

public final class DocumentCodeValidator {

    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Z0-9_]{2,64}$");

    private DocumentCodeValidator() {
    }

    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim().toUpperCase(Locale.ROOT);
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static boolean isValid(String code) {
        String n = normalize(code);
        return n != null && CODE_PATTERN.matcher(n).matches();
    }
}
