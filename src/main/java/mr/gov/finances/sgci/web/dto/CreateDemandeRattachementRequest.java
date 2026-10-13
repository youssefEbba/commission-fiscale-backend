package mr.gov.finances.sgci.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.domain.enums.TypeEntiteRattachement;

/**
 * Dépôt d'une demande de rattachement, par une personne qui n'a pas encore de compte.
 *
 * <p>Lié en {@code multipart/form-data} : les justificatifs accompagnent ces champs dans la même
 * requête, chacun nommé par son code document.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateDemandeRattachementRequest {

    @NotBlank(message = "Le nom d'utilisateur est obligatoire")
    private String username;

    @NotBlank(message = "Le mot de passe est obligatoire")
    private String password;

    @NotBlank(message = "Le nom complet est obligatoire")
    private String nomComplet;

    /** Obligatoire : c'est par là que la décision du Président est communiquée. */
    @NotBlank(message = "L'adresse e-mail est obligatoire")
    @Email(message = "Adresse e-mail invalide")
    private String email;

    private String telephone;

    @NotNull(message = "La qualité demandée est obligatoire")
    private Role roleDemande;

    @NotNull(message = "Le type d'entité est obligatoire")
    private TypeEntiteRattachement typeEntite;

    /**
     * Entité choisie dans le référentiel. Laissée vide, la demande doit décrire l'entité à créer.
     */
    private Long entiteId;

    // ── descriptif d'une entité absente du référentiel ─────────────────────────────────────────

    private String entiteNom;
    private String entiteSigle;
    private String entiteNif;
    private String entiteAdresse;
    private String entiteActivite;
    private String entiteMinistereTutelle;
}
