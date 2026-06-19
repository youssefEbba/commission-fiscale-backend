package mr.gov.finances.sgci.service;

import mr.gov.finances.sgci.domain.entity.AutoriteContractante;
import mr.gov.finances.sgci.domain.entity.Convention;
import mr.gov.finances.sgci.domain.entity.DemandeCorrection;
import mr.gov.finances.sgci.domain.entity.Marche;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Référence dossier GED lisible : DOSS-{lettre AC}-{yyyyMMdd}-{slug marché/convention}.
 */
final class DossierReferenceGenerator {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd")
            .withZone(ZoneOffset.UTC);
    private static final Pattern NON_ALNUM = Pattern.compile("[^A-Z0-9]");
    private static final int SLUG_MAX_LEN = 12;

    private DossierReferenceGenerator() {
    }

    static String buildReference(DemandeCorrection demande) {
        Instant when = demande.getDateDepot() != null ? demande.getDateDepot() : Instant.now();
        String acLetter = extractAcLetter(demande.getAutoriteContractante());
        String datePart = DATE_FMT.format(when);
        String scopePart = extractScopeSlug(demande);
        return "DOSS-" + acLetter + "-" + datePart + "-" + scopePart;
    }

    static String ensureUnique(String base, Predicate<String> exists) {
        if (!exists.test(base)) {
            return base;
        }
        for (int seq = 2; seq < 1000; seq++) {
            String candidate = base + "-" + String.format("%02d", seq);
            if (!exists.test(candidate)) {
                return candidate;
            }
        }
        return base + "-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase(Locale.ROOT);
    }

    private static String extractAcLetter(AutoriteContractante ac) {
        if (ac == null) {
            return "X";
        }
        if (ac.getCode() != null && !ac.getCode().isBlank()) {
            String code = ac.getCode().trim();
            if (code.regionMatches(true, 0, "AC_", 0, 3)) {
                code = code.substring(3);
            } else if (code.regionMatches(true, 0, "AC-", 0, 3)) {
                code = code.substring(3);
            }
            for (int i = 0; i < code.length(); i++) {
                char c = Character.toUpperCase(code.charAt(i));
                if (Character.isLetter(c)) {
                    return String.valueOf(c);
                }
            }
        }
        if (ac.getNom() != null && !ac.getNom().isBlank()) {
            for (int i = 0; i < ac.getNom().length(); i++) {
                char c = Character.toUpperCase(ac.getNom().charAt(i));
                if (Character.isLetter(c)) {
                    return String.valueOf(c);
                }
            }
        }
        return "X";
    }

    private static String extractScopeSlug(DemandeCorrection demande) {
        Marche marche = demande.getMarche();
        if (marche != null && marche.getNumeroMarche() != null && !marche.getNumeroMarche().isBlank()) {
            return slugFromHyphenatedCode(marche.getNumeroMarche());
        }
        Convention convention = demande.getConvention();
        if (convention != null && convention.getReference() != null && !convention.getReference().isBlank()) {
            return slugFromHyphenatedCode(convention.getReference());
        }
        return "NA";
    }

    static String slugFromHyphenatedCode(String raw) {
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        String[] parts = normalized.split("-");
        if (parts.length == 0) {
            return truncateSlug(sanitize(normalized));
        }
        if (parts.length == 1) {
            return truncateSlug(sanitize(parts[0]));
        }
        String last = sanitize(parts[parts.length - 1]);
        if (last.length() <= 2) {
            String prev = sanitize(parts[parts.length - 2]);
            return truncateSlug(prev + last);
        }
        return truncateSlug(last);
    }

    private static String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "NA";
        }
        String cleaned = NON_ALNUM.matcher(value.trim().toUpperCase(Locale.ROOT)).replaceAll("");
        return cleaned.isEmpty() ? "NA" : cleaned;
    }

    private static String truncateSlug(String slug) {
        if (slug.length() <= SLUG_MAX_LEN) {
            return slug;
        }
        return slug.substring(0, SLUG_MAX_LEN);
    }
}
