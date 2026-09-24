package mr.gov.finances.sgci.web.dto.archive;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Compte rendu d'un import de relevé d'archive. */
@Data
public class ImportArchiveResultatDto {

    private Long certificatId;
    /** Vide juste après l'import : la référence lisible est attribuée au redémarrage suivant. */
    private String certificatReference;
    private String certificatNumero;
    private String certificatStatut;

    private Long entrepriseId;
    private String entrepriseRaisonSociale;

    /** Demande de correction d'archive créée pour rattacher le dossier repris. */
    private Long demandeCorrectionId;
    private String demandeCorrectionNumero;
    private Long autoriteContractanteId;
    private Long conventionId;
    private Long marcheId;

    /**
     * Transfert repris du relevé, enregistré comme déjà exécuté. Absent si le relevé n'en comporte
     * aucun. Son montant est la seule part transférée vers le crédit intérieur (composante TVA).
     */
    private Long transfertCreditId;
    private BigDecimal transfertCreditMontant;

    /**
     * {@code true} si le crédit existait déjà : le relevé a complété le dossier au lieu d'en créer
     * un nouveau. Les compteurs ci-dessous ne portent alors que sur les lignes effectivement ajoutées.
     */
    private boolean certificatDejaExistant;

    /** Lignes du relevé déjà présentes en base, laissées de côté pour ne pas les compter deux fois. */
    private int utilisationsIgnorees;

    private int utilisationsDouanieres;
    private int utilisationsInterieures;
    private int lignesTaxeCreees;

    private BigDecimal soldeCordon;
    private BigDecimal soldeTVA;

    /** Anomalies relevées à la lecture, conservées pour traçabilité. */
    private List<String> anomalies = new ArrayList<>();
}
