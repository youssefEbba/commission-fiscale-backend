package mr.gov.finances.sgci.domain.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Un export de période de l'ancienne application (tous les crédits émis sur la période, avec leurs
 * utilisations), lu une fois et gardé : l'opérateur complète les crédits à son rythme et importe
 * ceux qui sont prêts. Supprimer le lot ne touche pas aux crédits déjà importés.
 */
@Entity
@Table(name = "lot_import_archive")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LotImportArchive {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nomFichier;

    /** Empreinte du fichier : le même fichier versé deux fois rouvre le lot existant. */
    @Column(nullable = false, unique = true, length = 64)
    private String empreinteSha256;

    @Column(nullable = false)
    private Instant dateAnalyse;

    private Long analyseParId;
    private String analyseParNom;

    @OneToMany(mappedBy = "lot", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("ordre ASC")
    @Builder.Default
    private List<LotImportArchiveCredit> credits = new ArrayList<>();
}
