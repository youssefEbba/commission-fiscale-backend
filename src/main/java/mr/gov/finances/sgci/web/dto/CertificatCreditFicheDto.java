package mr.gov.finances.sgci.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Fiche consolidée d'un crédit d'impôt : identité, convention / marché / maître d'ouvrage,
 * documents, soldes et tableau d'utilisation (utilisations + stock TVA déductible).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CertificatCreditFicheDto {

    /** Certificat (numéro, référence, montants, soldes, statut, dates). */
    private CertificatCreditDto certificat;

    /** Entreprise titulaire effective (chef de file si le dossier est porté par un groupement). */
    private EntrepriseDto entreprise;

    /** Groupement porteur (null si demande individuelle). NIF affiché = NIF du chef de file. */
    private GroupementDto groupement;

    /** Convention rattachée (référence, intitulé, bailleur, projet). */
    private ConventionDto convention;

    /** Marché rattaché (via la demande de correction). */
    private MarcheDto marche;

    /** Maître d'ouvrage (autorité contractante), avec ministère de tutelle. */
    private AutoriteContractanteDto autoriteContractante;

    /** Intitulé libre du marché saisi à la demande de correction (si le marché réel n'existe pas encore). */
    private String intituleMarche;

    /** Documents rattachés au certificat. */
    private List<DocumentCertificatCreditDto> documents;

    /** Tableau d'utilisation consolidé (douane + TVA intérieure). */
    private List<UtilisationCreditDto> utilisations;

    /** Stock de TVA déductible constitué sur ce certificat. */
    private List<TvaDeductibleStockDto> tvaStock;
}
