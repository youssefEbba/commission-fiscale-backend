package mr.gov.finances.sgci.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.domain.enums.StatutDemandeRattachement;
import mr.gov.finances.sgci.domain.enums.TypeEntiteRattachement;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Vue d'une demande de rattachement.
 *
 * <p>N'expose <strong>jamais</strong> le hachage du mot de passe porté par l'entité : la demande est
 * lue par le Président, et un hachage n'a aucune raison de traverser l'API.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DemandeRattachementDto {

    private Long id;
    private String reference;

    private String username;
    private String nomComplet;
    private String email;
    private String telephone;
    private Role roleDemande;

    private TypeEntiteRattachement typeEntite;

    /** Renseignés quand la demande vise une entité existante. */
    private Long entiteId;
    private String entiteNomResolu;

    /** Vrai quand l'approbation créera l'entité décrite par les champs ci-dessous. */
    private boolean entiteNouvelle;

    private String entiteNom;
    private String entiteSigle;
    private String entiteNif;
    private String entiteAdresse;
    private String entiteActivite;
    private String entiteMinistereTutelle;

    private StatutDemandeRattachement statut;
    private Instant dateCreation;
    private Instant dateTraitement;
    private String traiteParUsername;
    private String motifRefus;

    /** Compte créé par l'approbation, s'il y en a eu une. */
    private Long utilisateurCreeId;

    @Builder.Default
    private List<DocumentDemandeRattachementDto> documents = new ArrayList<>();
}
