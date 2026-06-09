package mr.gov.finances.sgci.config;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.entity.CertificatCredit;
import mr.gov.finances.sgci.domain.entity.DemandeCorrection;
import mr.gov.finances.sgci.domain.entity.Dqe;
import mr.gov.finances.sgci.domain.entity.Entreprise;
import mr.gov.finances.sgci.domain.entity.ModeleFiscal;
import mr.gov.finances.sgci.domain.entity.UtilisationDouaniere;
import mr.gov.finances.sgci.domain.entity.UtilisationTVAInterieure;
import mr.gov.finances.sgci.domain.enums.StatutCertificat;
import mr.gov.finances.sgci.domain.enums.StatutDemande;
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
 * Données d'intégration (certificat ouvert + utilisations) uniquement pour le profil {@code test}.
 * Hors {@link DataInitializer} pour ne pas polluer les environnements de démo / prod.
 */
@Component
@Profile("test")
@Order(200)
@RequiredArgsConstructor
public class TestWorkflowDataSeed implements CommandLineRunner {

    private static final String NUMERO_CERT_OUVERT = "CI-TEST-OUVERT";
    private static final String NUMERO_CERT_EN_CONTROLE = "CI-TEST-EN-CONTROLE";
    private static final String NUMERO_DEMANDE_EXPLICATION = "DC-TEST-EXPLICATION";

    private final CertificatCreditRepository certificatCreditRepository;
    private final DemandeCorrectionRepository demandeCorrectionRepository;
    private final EntrepriseRepository entrepriseRepository;
    private final UtilisationCreditRepository utilisationCreditRepository;

    @Override
    public void run(String... args) {
        Entreprise entreprise = entrepriseRepository.findByNif("NIF_DEFAULT").orElse(null);
        if (entreprise == null) {
            return;
        }
        DemandeCorrection demandeRecue = ensureDemandeRecuePourExplication(entreprise);
        ensureCertificatOuvret(entreprise, demandeRecue);
        ensureCertificatEnControle(entreprise, demandeRecue);
    }

    private DemandeCorrection ensureDemandeRecuePourExplication(Entreprise entreprise) {
        return demandeCorrectionRepository.findByNumero(NUMERO_DEMANDE_EXPLICATION)
                .orElseGet(() -> {
                    DemandeCorrection template = demandeCorrectionRepository.findByNumero("DC-DEFAULT-DEMO")
                            .orElseGet(() -> demandeCorrectionRepository.findAll().stream()
                                    .filter(d -> d.getEntreprise() != null
                                            && d.getEntreprise().getId().equals(entreprise.getId()))
                                    .findFirst()
                                    .orElse(null));
                    if (template == null) {
                        return null;
                    }
                    DemandeCorrection demande = DemandeCorrection.builder()
                            .numero(NUMERO_DEMANDE_EXPLICATION)
                            .dateDepot(Instant.now())
                            .statut(StatutDemande.RECUE)
                            .autoriteContractante(template.getAutoriteContractante())
                            .entreprise(template.getEntreprise())
                            .convention(template.getConvention())
                            .build();
                    ModeleFiscal modeleFiscal = ModeleFiscal.builder().demandeCorrection(demande).build();
                    Dqe dqe = Dqe.builder().demandeCorrection(demande).build();
                    demande.setModeleFiscal(modeleFiscal);
                    demande.setDqe(dqe);
                    return demandeCorrectionRepository.save(demande);
                });
    }

    private void ensureCertificatOuvret(Entreprise entreprise, DemandeCorrection demande) {
        if (certificatCreditRepository.existsByNumero(NUMERO_CERT_OUVERT)) {
            return;
        }
        DemandeCorrection linkedDemande = demande != null ? demande
                : demandeCorrectionRepository.findByNumero("DC-DEFAULT-DEMO").orElse(null);

        CertificatCredit certificat = CertificatCredit.builder()
                .numero(NUMERO_CERT_OUVERT)
                .dateEmission(Instant.now())
                .dateValidite(Instant.now().plusSeconds(365L * 24 * 3600))
                .montantCordon(BigDecimal.valueOf(5_000_000))
                .montantTVAInterieure(BigDecimal.valueOf(3_000_000))
                .soldeCordon(BigDecimal.valueOf(5_000_000))
                .soldeTVA(BigDecimal.valueOf(3_000_000))
                .statut(StatutCertificat.OUVERT)
                .entreprise(entreprise)
                .demandeCorrection(linkedDemande)
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

    private void ensureCertificatEnControle(Entreprise entreprise, DemandeCorrection demande) {
        CertificatCredit certEnControle = certificatCreditRepository.findByNumero(NUMERO_CERT_EN_CONTROLE)
                .orElseGet(() -> {
                    DemandeCorrection linkedDemande = demande != null ? demande
                            : demandeCorrectionRepository.findByNumero("DC-DEFAULT-DEMO").orElse(null);
                    CertificatCredit cert = CertificatCredit.builder()
                            .numero(NUMERO_CERT_EN_CONTROLE)
                            .dateEmission(Instant.now())
                            .dateValidite(Instant.now().plusSeconds(365L * 24 * 3600))
                            .montantCordon(BigDecimal.valueOf(100))
                            .montantTVAInterieure(BigDecimal.valueOf(20))
                            .soldeCordon(BigDecimal.valueOf(100))
                            .soldeTVA(BigDecimal.valueOf(20))
                            .droitsEtTaxesDouaneHorsTva(BigDecimal.valueOf(80))
                            .tvaImportationDouaneAccordee(BigDecimal.valueOf(20))
                            .tvaImportationDouane(BigDecimal.valueOf(20))
                            .tvaCollecteeTravaux(BigDecimal.valueOf(40))
                            .statut(StatutCertificat.EN_CONTROLE)
                            .entreprise(entreprise)
                            .demandeCorrection(linkedDemande)
                            .build();
                    return certificatCreditRepository.save(cert);
                });

        if (utilisationCreditRepository.findByCertificatCreditId(certEnControle.getId()).isEmpty()) {
            UtilisationDouaniere utilEnControle = new UtilisationDouaniere();
            utilEnControle.setDateDemande(Instant.now());
            utilEnControle.setStatut(StatutUtilisation.DEMANDEE);
            utilEnControle.setCertificatCredit(certEnControle);
            utilEnControle.setEntreprise(entreprise);
            utilEnControle.setNumeroDeclaration("DEC-EXPL-001");
            utilEnControle.setNumeroBulletin("BUL-EXPL-001");
            utilEnControle.setDateDeclaration(Instant.now());
            utilEnControle.setMontantDroits(BigDecimal.valueOf(10_000));
            utilEnControle.setMontantTVA(BigDecimal.valueOf(5_000));
            utilEnControle.setMontant(BigDecimal.valueOf(15_000));
            utilEnControle.setEnregistreeSYDONIA(true);
            utilisationCreditRepository.save(utilEnControle);
        }
    }
}
