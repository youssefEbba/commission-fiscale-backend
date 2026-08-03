package mr.gov.finances.sgci.service;

import mr.gov.finances.sgci.domain.entity.CertificatCredit;
import mr.gov.finances.sgci.domain.entity.DemandeCorrection;
import mr.gov.finances.sgci.domain.enums.Role;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Set;

/**
 * Détermine l'ensemble des rôles dont le visa est requis pour une demande de correction ou un
 * certificat de crédit, ainsi que les documents attendus pour certains de ces visas.
 * <p>
 * Pour la demande de correction, les quatre rôles (DGD, DGTCP, DGI, DGB) visent <b>toujours</b>,
 * quel que soit le montage financier. Seul le document exigé pour valider le visa varie selon les
 * enveloppes de crédit déclarées :
 * <ul>
 *   <li>{@code creditExterieur > 0} (même si {@code creditInterieur > 0} aussi) → la <b>DGD</b> doit
 *   déposer l'offre fiscale corrigée avant de viser ;</li>
 *   <li>{@code creditExterieur == 0} et {@code creditInterieur > 0} → la <b>DGI</b> doit déposer le
 *   document crédit intérieur avant de viser.</li>
 * </ul>
 * Le certificat de crédit (mise en place) conserve son propre calcul, inchangé, basé sur les
 * enveloppes de crédit ({@link #requiredRolesForCertificat}).
 */
@Component
public class VisaRequirementResolver {

    /** Rôles de visa de la demande de correction : toujours les quatre, sans condition de montant. */
    private static final Set<Role> CORRECTION_BASE = EnumSet.of(Role.DGD, Role.DGTCP, Role.DGI, Role.DGB);
    /** Rôles de visa du certificat (mise en place). */
    private static final Set<Role> CERTIFICAT_BASE = EnumSet.of(Role.DGI, Role.DGD, Role.DGTCP);

    /** Ensemble des rôles requis pour la commission de correction : toujours les quatre rôles. */
    public Set<Role> requiredRolesForCorrection(DemandeCorrection demande) {
        return EnumSet.copyOf(CORRECTION_BASE);
    }

    /** {@code true} si la DGD doit déposer l'offre fiscale corrigée avant de viser (crédit extérieur positif). */
    public boolean isOffreFiscaleCorrigeeRequiredForCorrection(DemandeCorrection demande) {
        return demande != null && isPositive(demande.getCreditExterieur());
    }

    /**
     * {@code true} si la DGI doit déposer le document crédit intérieur avant de viser
     * (crédit intérieur positif et crédit extérieur nul — cas exclusivement intérieur).
     */
    public boolean isCreditInterieurDocumentRequiredForCorrection(DemandeCorrection demande) {
        return demande != null && isPositive(demande.getCreditInterieur()) && !isPositive(demande.getCreditExterieur());
    }

    /**
     * Rôle devant viser en premier pour la demande de correction : celui à qui un document
     * pré-visa est demandé (DGD si offre fiscale corrigée requise, sinon DGI si crédit intérieur
     * requis). Les autres rôles ne peuvent se prononcer tant que ce visa n'est pas posé.
     * {@code null} si aucun document pré-visa n'est exigé (aucune contrainte de séquencement).
     */
    public Role firstVisaRoleForCorrection(DemandeCorrection demande) {
        if (isOffreFiscaleCorrigeeRequiredForCorrection(demande)) {
            return Role.DGD;
        }
        if (isCreditInterieurDocumentRequiredForCorrection(demande)) {
            return Role.DGI;
        }
        return null;
    }

    /** Ensemble des rôles requis pour la mise en place du certificat. */
    public Set<Role> requiredRolesForCertificat(CertificatCredit certificat) {
        DemandeCorrection demande = certificat != null ? certificat.getDemandeCorrection() : null;
        if (demande != null && auMoinsUnCredit(demande.getCreditInterieur(), demande.getCreditExterieur())) {
            return filter(CERTIFICAT_BASE, demande.getCreditInterieur(), demande.getCreditExterieur());
        }
        return EnumSet.copyOf(CERTIFICAT_BASE);
    }

    private Set<Role> filter(Set<Role> base, BigDecimal creditInterieur, BigDecimal creditExterieur) {
        boolean hasInterieur = isPositive(creditInterieur);
        boolean hasExterieur = isPositive(creditExterieur);
        Set<Role> result = EnumSet.noneOf(Role.class);
        for (Role role : base) {
            if (role == Role.DGI && !hasInterieur) {
                continue;
            }
            if (role == Role.DGD && !hasExterieur) {
                continue;
            }
            result.add(role);
        }
        return result.isEmpty() ? EnumSet.copyOf(base) : result;
    }

    private boolean auMoinsUnCredit(BigDecimal creditInterieur, BigDecimal creditExterieur) {
        return isPositive(creditInterieur) || isPositive(creditExterieur);
    }

    private boolean isPositive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }
}
