package mr.gov.finances.sgci.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import mr.gov.finances.sgci.domain.enums.StatutUtilisation;

import java.time.Instant;

/**
 * État d'émission du certificat d'utilisation, du point de vue du rôle qui consulte.
 *
 * <p>Permet au front de piloter le bouton « Émettre le certificat » et son message sans réécrire la
 * règle : {@code codeBlocage} et {@code motifBlocage} proviennent de la même méthode que celle qui
 * lève l'exception côté écriture.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CertificatUtilisationEmissionDto {

    /** {@code true} si le consultant peut émettre maintenant. Faux aussi quand c'est déjà fait. */
    private boolean emissible;

    /** {@code null}, {@code ROLE_NON_HABILITE} ou {@code STATUT_INCOMPATIBLE}. */
    private String codeBlocage;

    /** Message associé au code, à afficher tel quel. */
    private String motifBlocage;

    private StatutUtilisation statut;

    /** Le calcul DGTCP attendu avant l'émission : {@code LIQUIDEE} en douane, {@code APUREE} en TVA. */
    private StatutUtilisation statutPrealableAttendu;

    /** {@code CU-001/2026} une fois émis, {@code null} avant. */
    private String numeroCertificatUtilisation;

    private Instant dateCertificatUtilisation;

    /** {@code true} si une version active du document {@code CERTIFICAT_UTILISATION} est au dossier. */
    private boolean certificatSigneDepose;
}
