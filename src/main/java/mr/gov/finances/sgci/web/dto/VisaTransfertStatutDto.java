package mr.gov.finances.sgci.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import mr.gov.finances.sgci.domain.enums.Role;

import java.time.Instant;

/**
 * État d'un visa du circuit de transfert (P7), dans l'ordre DGD → DGI → DGTCP → Président.
 *
 * <p>Alimente la file d'attente de chaque direction et le dossier consolidé que le Président
 * examine avant d'approuver. Le blocage est porté par un code stable : aucun client ne doit
 * réécrire la règle de séquencement ni interpréter le message.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VisaTransfertStatutDto {

    /** Rôle attendu à ce rang du circuit. */
    private Role role;

    /** Rang dans la séquence, de 1 (DGD) à 4 (Président). */
    private int rang;

    /** {@code true} si ce visa est déjà posé. */
    private boolean pose;

    private Long decisionId;
    private Instant datePose;
    private Long utilisateurId;
    private String utilisateurNom;

    /** {@code true} si ce rôle peut viser maintenant. */
    private boolean visablePourMoi;

    /**
     * Cause du blocage, {@code null} si le visa est possible :
     * {@code STATUT_INCOMPATIBLE}, {@code VISA_DEJA_POSE}, {@code REJET_TEMP_OUVERT},
     * {@code VISA_PREALABLE_MANQUANT}, {@code SOLDE_INSUFFISANT}.
     */
    private String codeBlocage;

    /** Message destiné à l'utilisateur, à afficher tel quel. */
    private String motifBlocage;

    /** Rôle dont le visa doit précéder celui-ci et qui n'a pas encore visé ({@code null} sinon). */
    private Role visaPrealableManquant;
}
