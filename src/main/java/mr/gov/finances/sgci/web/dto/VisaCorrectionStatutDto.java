package mr.gov.finances.sgci.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import mr.gov.finances.sgci.domain.enums.Role;

import java.time.Instant;

/**
 * État d'un visa de la commission sur une demande de correction, du point de vue de
 * l'administrateur : qui doit viser, qui a déjà visé, quel document est exigé avant le visa et si
 * l'administrateur peut viser à la place du rôle titulaire.
 * <p>
 * Le rôle {@link Role#PRESIDENT} figure dans la liste comme dernière étape : son « visa » est
 * l'adoption de la demande ({@code EN_VALIDATION → ADOPTEE}), conditionnée par la lettre d'adoption.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VisaCorrectionStatutDto {

    /** Rôle titulaire du visa (DGD, DGTCP, DGI, DGB ou PRESIDENT). */
    private Role role;

    /** {@code true} si ce visa est attendu sur cette demande. */
    private boolean requis;

    /** {@code true} si le visa est déjà posé (ou, pour le Président, si la demande est adoptée). */
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

    /** Rôle dont le visa doit précéder celui-ci et qui n'a pas encore visé ({@code null} sinon). */
    private Role visaPrealableManquant;

    /** {@code true} si l'administrateur peut poser ce visa à la place du rôle titulaire, maintenant. */
    private boolean visableParAdmin;

    /** Raison du blocage lorsque {@link #visableParAdmin} vaut {@code false} ({@code null} sinon). */
    private String motifBlocage;
}
