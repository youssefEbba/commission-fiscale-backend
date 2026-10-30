package mr.gov.finances.sgci;

import jakarta.persistence.EntityManager;
import mr.gov.finances.sgci.config.ReferenceFormatMigration;
import mr.gov.finances.sgci.domain.entity.CertificatCredit;
import mr.gov.finances.sgci.domain.entity.DemandeCorrection;
import mr.gov.finances.sgci.domain.entity.DemandeRattachement;
import mr.gov.finances.sgci.domain.entity.UtilisationCredit;
import mr.gov.finances.sgci.domain.entity.UtilisationDouaniere;
import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.domain.enums.StatutUtilisation;
import mr.gov.finances.sgci.domain.enums.TypeEntiteRattachement;
import mr.gov.finances.sgci.domain.enums.TypeUtilisation;
import mr.gov.finances.sgci.repository.CertificatCreditRepository;
import mr.gov.finances.sgci.repository.DemandeCorrectionRepository;
import mr.gov.finances.sgci.repository.DemandeRattachementRepository;
import mr.gov.finances.sgci.repository.EntrepriseRepository;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.repository.UtilisationCreditRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.CertificatCreditService;
import mr.gov.finances.sgci.service.DemandeRattachementService;
import mr.gov.finances.sgci.service.ReferenceSequenceGenerator;
import mr.gov.finances.sgci.service.VerificationPubliqueService;
import mr.gov.finances.sgci.web.dto.CreateDemandeRattachementRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le nouveau format des références ({@code DC-0010-10-2026}) : génération, conversion de l'existant,
 * et reconnaissance de l'ancien format à la saisie.
 *
 * <p>Niveau service : Tomcat ne démarre pas sur ce poste.
 */
@SpringBootTest
@ActiveProfiles("test")
class ReferenceNouveauFormatIT {

    private static final String DEMO_CERTIFICAT = "CI-DEMO-SCEN-E";

    @Autowired private ReferenceSequenceGenerator generator;
    @Autowired private ReferenceFormatMigration migration;
    @Autowired private VerificationPubliqueService verificationService;
    @Autowired private DemandeRattachementService rattachementService;
    @Autowired private CertificatCreditService certificatService;
    @Autowired private DemandeCorrectionRepository demandeRepository;
    @Autowired private DemandeRattachementRepository rattachementRepository;
    @Autowired private CertificatCreditRepository certificatRepository;
    @Autowired private UtilisationCreditRepository utilisationRepository;
    @Autowired private EntrepriseRepository entrepriseRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private EntityManager entityManager;

    // ── la génération ──────────────────────────────────────────────────────────────────────────

    @Test
    void lesNouvellesReferencesSuiventLeFormatDemande() {
        assertThat(generator.next("DC")).matches("^DC-\\d{4}-\\d{2}-\\d{4}$");
        assertThat(generator.nextSansMois("CU")).matches("^CU-\\d{4}-\\d{4}$");
    }

    // ── la conversion de l'existant ────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void lesReferencesExistantesSontConverties() {
        DemandeCorrection d = demandeRepository.findAll().stream().findFirst().orElseThrow();
        d.setReference("DC-991-10/2026");
        demandeRepository.saveAndFlush(d);
        UtilisationCredit u = utilisation("CU-991/2026");
        u.setReference("DU-991-10/2026");
        utilisationRepository.saveAndFlush(u);

        migration.run(null);
        entityManager.clear();

        assertThat(demandeRepository.findById(d.getId()).orElseThrow().getReference()).isEqualTo("DC-0991-10-2026");
        UtilisationCredit apres = utilisationRepository.findById(u.getId()).orElseThrow();
        assertThat(apres.getReference()).isEqualTo("DU-0991-10-2026");
        assertThat(apres.getNumeroCertificatUtilisation()).isEqualTo("CU-0991-2026");
    }

    @Test
    @Transactional
    void uneReferenceDejaAuNouveauFormatNEstPasTouchee() {
        DemandeCorrection d = demandeRepository.findAll().stream().findFirst().orElseThrow();
        d.setReference("DC-0992-10-2026");
        demandeRepository.saveAndFlush(d);

        migration.run(null);
        entityManager.clear();

        assertThat(demandeRepository.findById(d.getId()).orElseThrow().getReference()).isEqualTo("DC-0992-10-2026");
    }

    // ── l'ancien format, encore reconnu à la saisie ────────────────────────────────────────────

    @Test
    @Transactional
    void unQrCodeImprimeALAncienFormatEstEncoreVerifie() {
        utilisation("CU-0042-2026");

        assertThat(verificationService.verifier("CU-042/2026").isTrouve()).isTrue();
    }

    @Test
    @Transactional
    void leSuiviDInscriptionAccepteLAncienFormat() throws Exception {
        Long entrepriseId = entrepriseRepository.findByNif("NIF_TEST").orElseThrow().getId();
        var depose = rattachementService.deposer(CreateDemandeRattachementRequest.builder()
                .username("suivi-" + System.nanoTime())
                .password("MotDePasse!2026")
                .nomComplet("Personne suivie")
                .email("suivi@example.mr")
                .roleDemande(Role.ENTREPRISE)
                .typeEntite(TypeEntiteRattachement.ENTREPRISE)
                .entiteId(entrepriseId)
                .build(), Map.of("MANDAT_REPRESENTATION", pdf()));
        DemandeRattachement d = rattachementRepository.findById(depose.getId()).orElseThrow();
        d.setReference("RT-0777-10-2026");
        rattachementRepository.saveAndFlush(d);

        assertThat(rattachementService.suivre("RT-777-10/2026", "suivi@example.mr")).isNotNull();
    }

    @Test
    @Transactional
    void laFicheDUnCreditAccepteLAncienFormat() {
        CertificatCredit c = certificatRepository.findByNumero(DEMO_CERTIFICAT).orElseThrow();
        c.setReference("CR-0777-10-2026");
        certificatRepository.saveAndFlush(c);
        Utilisateur admin = utilisateurRepository.findByUsername("admin").orElseThrow();

        assertThat(certificatService.getFicheByReference("CR-777-10/2026",
                new AuthenticatedUser(admin.getId(), admin.getUsername(), admin.getRole()))).isNotNull();
    }

    // ── fixtures ───────────────────────────────────────────────────────────────────────────────

    private UtilisationCredit utilisation(String numeroCertificat) {
        CertificatCredit cert = certificatRepository.findByNumero(DEMO_CERTIFICAT).orElseThrow();
        UtilisationDouaniere u = new UtilisationDouaniere();
        u.setType(TypeUtilisation.DOUANIER);
        u.setStatut(StatutUtilisation.ENVOYEE_AU_TRESOR);
        u.setDateDemande(Instant.now());
        u.setMontant(BigDecimal.valueOf(100));
        u.setCertificatCredit(cert);
        u.setEntreprise(cert.getEntreprise());
        u.setNumeroDeclaration("DECL-REF-" + System.nanoTime());
        u.setNumeroBulletin("BUL-REF-" + System.nanoTime());
        u.setNumeroCertificatUtilisation(numeroCertificat);
        u.setDateCertificatUtilisation(Instant.now());
        return utilisationRepository.saveAndFlush(u);
    }

    private static org.springframework.mock.web.MockMultipartFile pdf() {
        return new org.springframework.mock.web.MockMultipartFile("file", "mandat.pdf", "application/pdf",
                "%PDF-1.4 mandat".getBytes());
    }
}
