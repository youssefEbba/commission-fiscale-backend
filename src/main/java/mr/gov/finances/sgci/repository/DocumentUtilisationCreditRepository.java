package mr.gov.finances.sgci.repository;

import mr.gov.finances.sgci.domain.entity.DocumentUtilisationCredit;
import mr.gov.finances.sgci.domain.enums.TypeDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentUtilisationCreditRepository extends JpaRepository<DocumentUtilisationCredit, Long> {

    List<DocumentUtilisationCredit> findByUtilisationCreditId(Long utilisationCreditId);

    List<DocumentUtilisationCredit> findByUtilisationCreditIdAndActifTrue(Long utilisationCreditId);

    Optional<DocumentUtilisationCredit> findByUtilisationCreditIdAndCodeDocumentAndActifTrue(Long utilisationCreditId, String codeDocument);

    /**
     * Variante liste, pour les codes qu'un même acte produit en plusieurs exemplaires.
     *
     * <p>{@code QUITTANCE_TRESOR} en est le cas : une saisie en écrit autant qu'il y a de
     * quittances. La dérivation {@code Optional} ci-dessus lèverait alors
     * {@code IncorrectResultSizeDataAccessException}.
     */
    List<DocumentUtilisationCredit> findByUtilisationCreditIdAndCodeDocumentAndActifTrueOrderByVersionDescIdDesc(
            Long utilisationCreditId, String codeDocument);

    /**
     * Retrouve une pièce par la fin de son chemin de stockage.
     *
     * <p>Sert à {@code LocalFileController}, qui ne reçoit qu'un nom de fichier : sans cette
     * résolution, il servirait les octets sans pouvoir appliquer la moindre règle.
     */
    Optional<DocumentUtilisationCredit> findFirstByCheminEndsWith(String suffixeChemin);
}
