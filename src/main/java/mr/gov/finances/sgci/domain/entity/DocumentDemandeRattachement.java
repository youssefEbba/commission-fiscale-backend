package mr.gov.finances.sgci.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Justificatif joint à une demande de rattachement — mandat de représentation, pièce d'identité,
 * registre du commerce.
 *
 * <p>C'est la pièce sur laquelle le Président fonde sa décision : sans elle, approuver un
 * rattachement reviendrait à croire sur parole une personne qui n'a pas encore de compte.
 *
 * <p>Table dédiée, comme chaque famille de documents du projet ({@code DocumentConvention},
 * {@code DocumentUtilisationCredit}…), et non une table partagée : le rattachement est le seul
 * document déposé <em>avant</em> toute authentification, et le garder à part évite qu'une lecture
 * mal filtrée ailleurs ne l'expose.
 */
@Entity
@Table(name = "document_demande_rattachement")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentDemandeRattachement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "demande_rattachement_id", nullable = false)
    private DemandeRattachement demandeRattachement;

    @Column(name = "code_document", nullable = false, length = 64)
    private String codeDocument;

    @Column(name = "nom_fichier", nullable = false)
    private String nomFichier;

    @Column(nullable = false, length = 512)
    private String chemin;

    private Long taille;

    @Column(name = "date_upload", nullable = false)
    private Instant dateUpload;

    @PrePersist
    protected void onCreate() {
        if (dateUpload == null) {
            dateUpload = Instant.now();
        }
    }
}
