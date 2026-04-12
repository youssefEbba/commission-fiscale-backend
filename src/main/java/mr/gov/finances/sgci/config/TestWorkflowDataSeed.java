package mr.gov.finances.sgci.config;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.entity.CertificatCredit;
import mr.gov.finances.sgci.domain.entity.DemandeCorrection;
import mr.gov.finances.sgci.domain.entity.Entreprise;
import mr.gov.finances.sgci.domain.entity.UtilisationDouaniere;
import mr.gov.finances.sgci.domain.entity.UtilisationTVAInterieure;
import mr.gov.finances.sgci.domain.enums.StatutCertificat;
import mr.gov.finances.sgci.domain.enums.StatutUtilisation;
import mr.gov.finances.sgci.domain.enums.TypeAchat;
import mr.gov.finances.sgci.repository.CertificatCreditRepository;
import mr.gov.finances.sgci.repository.DemandeCorrectionRepository;
import mr.gov.finances.sgci.repository.EntrepriseRepository;
import mr.gov.finances.sgci.repository.UtilisationCreditRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Données d’intégration (certificat ouvert + utilisations) uniquement pour le profil {@code test}.
 * Hors {@link DataInitializer} pour ne pas polluer les environnements de démo / prod.
 */
@Component
@Profile("test")
@Order(200)
@RequiredArgsConstructor
public class TestWorkflowDataSeed implements CommandLineRunner {

    private final CertificatCreditRepository certificatCreditRepository;
    private final DemandeCorrectionRepository demandeCorrectionRepository;
    private final EntrepriseRepository entrepriseRepository;
    private final UtilisationCreditRepository utilisationCreditRepository;

    @Override
    public void run(String... args) {
        String numero = "CI-TEST-OUVERT";
        if (certificatCreditRepository.existsByNumero(numero)) {
            return;
        }

        Entreprise entreprise = entrepriseRepository.findByNif("NIF_DEFAULT").orElse(null);
        if (entreprise == null) {
            return;
        }

        DemandeCorrection demande = demandeCorrectionRepository.findAll().stream()
                .filter(d -> d.getEntreprise() != null && d.getEntreprise().getId().equals(entreprise.getId()))
                .findFirst()
                .orElse(null);

        CertificatCredit certificat = CertificatCredit.builder()
                .numero(numero)
                .dateEmission(Instant.now())
                .dateValidite(Instant.now().plusSeconds(365L * 24 * 3600))
                .montantCordon(BigDecimal.valueOf(5_000_000))
                .montantTVAInterieure(BigDecimal.valueOf(3_000_000))
                .soldeCordon(BigDecimal.valueOf(5_000_000))
                .soldeTVA(BigDecimal.valueOf(3_000_000))
                .statut(StatutCertificat.OUVERT)
                .entreprise(entreprise)
                .demandeCorrection(demande)
                .build();

        certificat = certificatCreditRepository.save(certificat);

        UtilisationDouaniere utilDouane = new UtilisationDouaniere();
        utilDouane.setDateDemande(Instant.now());
        utilDouane.setStatut(StatutUtilisation.DEMANDEE);
        utilDouane.setCertificatCredit(certificat);
        utilDouane.setEntreprise(entreprise);
        utilDouane.setNumeroDeclaration("DEC-SEED-001");
        utilDouane.setNumeroBulletin("BUL-SEED-001");
        utilDouane.setDateDeclaration(Instant.now());
        utilDouane.setMontantDroits(BigDecimal.valueOf(70_000));
        utilDouane.setMontantTVA(BigDecimal.valueOf(30_000));
        utilDouane.setMontant(BigDecimal.valueOf(100_000));
        utilDouane.setEnregistreeSYDONIA(true);
        utilisationCreditRepository.save(utilDouane);

        UtilisationTVAInterieure utilTva = new UtilisationTVAInterieure();
        utilTva.setDateDemande(Instant.now());
        utilTva.setStatut(StatutUtilisation.DEMANDEE);
        utilTva.setCertificatCredit(certificat);
        utilTva.setEntreprise(entreprise);
        utilTva.setTypeAchat(TypeAchat.ACHAT_LOCAL);
        utilTva.setNumeroFacture("FAC-SEED-001");
        utilTva.setDateFacture(Instant.now());
        utilTva.setMontantTVA(BigDecimal.valueOf(55_000));
        utilTva.setMontant(BigDecimal.valueOf(55_000));
        utilisationCreditRepository.save(utilTva);
    }
}
