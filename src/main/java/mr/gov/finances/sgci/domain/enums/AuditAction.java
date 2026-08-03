package mr.gov.finances.sgci.domain.enums;

public enum AuditAction {
    CREATE,
    UPDATE,
    DELETE,
    LEGACY_INJECT,
    /** Correction manuelle par un administrateur système (document ou information), motif obligatoire. */
    ADMIN_CORRECTION
}
