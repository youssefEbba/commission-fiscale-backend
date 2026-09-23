package mr.gov.finances.sgci.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import mr.gov.finances.sgci.domain.enums.Role;

import java.time.Instant;

/**
 * État d'un visa de la commission sur une demande de mise en place, du point de vue de
 * l'administrateur : qui doit viser, qui a déjà visé, quel document est exigé avant le visa et si
 * l'administrateur peut viser à la place du rôle titulaire.
 * <p>
 * Le rôle {@link Role#PRESIDENT} figure dans la liste comme dernière étape : son « visa » est la
 * validation du certificat ({@code EN_VALIDATION_PRESIDENT → VALIDE_PRESIDENT}), conditionnée par
 * le dépôt du certificat signé. L'ouverture du crédit qui suit est une action distincte.
 * <p>
 * Jumeau de {@link VisaCorrectionStatutDto} : la structure est volontairement identique pour que le
 * front consomme le même modèle des deux côtés.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VisaCertificatStatutDto {

    /** Rôle titulaire du visa (DGI, DGD, DGTCP ou PRESIDENT). DGB ne vise pas le certificat. */
    private Role role;

    /** {@code true} si ce visa est attendu sur ce certificat, selon les enveloppes demandées. */
    private boolean requis;

    /** {@code true} si le visa est déjà posé (ou, pour le Président, si le certificat est validé). */
    private boolean pose;

    private Long decisionId;
    private Instant datePose;
    private Long utilisateurId;
    private String utilisateurNom;

    /** {@code true} si le visa déjà posé l'a été par l'administrateur à la place du rôle titulaire. */
    private boolean visaParAdmin;

    /** Code du document exigé avant ce visa ({@code null} si aucun document n'est requis). */
    private String codeDocumentRequis;

    /** {@code true} si le document exigé est déjà déposé (version active). */
    private boolean documentRequisPresent;

    /** {@code true} si un rejet temporaire de ce rôle est encore ouvert (visa bloqué). */
    private boolean rejetTempOuvert;

    /**
     * Toujours {@code null} sur le certificat : contrairement à la demande de correction, aucun
     * ordre n'est imposé entre les visas. Le champ est conservé pour aligner la structure sur
     * {@link VisaCorrectionStatutDto}.
     */
    private Role visaPrealableManquant;

    /** {@code true} si l'administrateur peut poser ce visa à la place du rôle titulaire, maintenant. */
    private boolean visableParAdmin;

    /** Raison du blocage lorsque {@link #visableParAdmin} vaut {@code false} ({@code null} sinon). */
    private String motifBlocage;
}
