package mr.gov.finances.sgci;

import mr.gov.finances.sgci.domain.entity.CertificatCredit;
import mr.gov.finances.sgci.domain.entity.DocumentUtilisationCredit;
import mr.gov.finances.sgci.domain.entity.Entreprise;
import mr.gov.finances.sgci.domain.entity.UtilisationCredit;
import mr.gov.finances.sgci.domain.entity.UtilisationDouaniere;
import mr.gov.finances.sgci.domain.entity.UtilisationTVAInterieure;
import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.domain.enums.StatutUtilisation;
import mr.gov.finances.sgci.domain.enums.TypeUtilisation;
import mr.gov.finances.sgci.repository.CertificatCreditRepository;
import mr.gov.finances.sgci.repository.DocumentUtilisationCreditRepository;
import mr.gov.finances.sgci.repository.EntrepriseRepository;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.repository.UtilisationCreditRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.DocumentUtilisationCreditService;
import mr.gov.finances.sgci.service.UtilisationCreditService;
import mr.gov.finances.sgci.web.dto.CertificatUtilisationEmissionDto;
import mr.gov.finances.sgci.web.dto.DocumentUtilisationCreditDto;
import mr.gov.finances.sgci.web.dto.UtilisationCreditDto;
import mr.gov.finances.sgci.web.exception.ApiErrorCode;
import mr.gov.finances.sgci.web.exception.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * À partir de quand l'entreprise voit son certificat d'utilisation.
 *
 * <p>La règle de l'équipe de test : pas avant que la DGTCP envoie le dossier au Trésor pour la
 * quittance. En TVA intérieure, où il n'y a pas de Trésor, pas avant que la DGI ait enregistré sa
 * quittance. Les directions le voient toujours.
 *
 * <p>Le serveur et le front se trompaient en sens inverses : le serveur ne l'ouvrait qu'à la
 * liquidation, d'où « le certificat ne s'affiche pas » ; le front montrait son numéro dès l'émission.
 * Le numéro suit donc désormais la pièce, et le DTO dit au front s'il peut l'afficher.
 *
 * <p>Niveau service : Tomcat ne démarre pas sur ce poste.
 */
@SpringBootTest
@ActiveProfiles("test")
class CertificatUtilisationVisibiliteIT {

    private static final String DEMO_CERTIFICAT = "CI-DEMO-SCEN-E";
    private static final String NUMERO = "CU-777/2026";

    @Autowired private UtilisationCreditService service;
    @Autowired private DocumentUtilisationCreditService documentService;
    @Autowired private UtilisationCreditRepository utilisationRepository;
    @Autowired private DocumentUtilisationCreditRepository documentRepository;
    @Autowired private CertificatCreditRepository certificatRepository;
    @Autowired private EntrepriseRepository entrepriseRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;

    private AuthenticatedUser entreprise;
    private AuthenticatedUser dgtcp;

    @BeforeEach
    void setUp() {
        // Les comptes amorcés : le contrôle de périmètre résout l'entreprise depuis l'utilisateur en base.
        entreprise = compte("entreprise");
        dgtcp = compte("dgtcp");
    }

    // ── en douane ──────────────────────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void douane_masqueALEntrepriseTantQueLeDossierNEstPasEnvoyeAuTresor() {
        UtilisationCredit util = douaneAu(StatutUtilisation.CERTIFICAT_EMIS);

        UtilisationCreditDto vu = service.findById(util.getId(), entreprise);

        assertThat(vu.getCertificatUtilisationCommunicable()).isFalse();
        assertThat(vu.getNumeroCertificatUtilisation()).as("le numéro suit la pièce").isNull();
        assertThat(vu.getDateCertificatUtilisation()).isNull();
        assertThat(codesVusPar(util, entreprise)).doesNotContain("CERTIFICAT_UTILISATION");
    }

    @Test
    @Transactional
    void douane_visibleALEntrepriseDesLEnvoiAuTresor() {
        UtilisationCredit util = douaneAu(StatutUtilisation.ENVOYEE_AU_TRESOR);

        UtilisationCreditDto vu = service.findById(util.getId(), entreprise);

        assertThat(vu.getCertificatUtilisationCommunicable()).isTrue();
        assertThat(vu.getNumeroCertificatUtilisation()).isEqualTo(NUMERO);
        assertThat(codesVusPar(util, entreprise)).contains("CERTIFICAT_UTILISATION");
    }

    @Test
    @Transactional
    void douane_laListeMasqueAussiLeNumero() {
        UtilisationCredit util = douaneAu(StatutUtilisation.CERTIFICAT_EMIS);

        List<UtilisationCreditDto> liste = service.findAllVisible(entreprise, null, null);

        assertThat(liste).filteredOn(u -> util.getId().equals(u.getId()))
                .singleElement()
                .satisfies(u -> assertThat(u.getNumeroCertificatUtilisation()).isNull());
    }

    // ── en TVA intérieure ──────────────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void tva_masqueALEntrepriseTantQueLaDgiNAPasEnregistreSaQuittance() {
        UtilisationCredit util = tvaAu(StatutUtilisation.CERTIFICAT_EMIS);

        UtilisationCreditDto vu = service.findById(util.getId(), entreprise);

        assertThat(vu.getCertificatUtilisationCommunicable()).isFalse();
        assertThat(vu.getNumeroCertificatUtilisation()).isNull();
        assertThat(codesVusPar(util, entreprise)).doesNotContain("CERTIFICAT_UTILISATION");
    }

    @Test
    @Transactional
    void tva_visibleALEntrepriseDesLaQuittanceDgi() {
        UtilisationCredit util = tvaAu(StatutUtilisation.QUITTANCE_DGI_ENREGISTREE);

        UtilisationCreditDto vu = service.findById(util.getId(), entreprise);

        assertThat(vu.getCertificatUtilisationCommunicable()).isTrue();
        assertThat(vu.getNumeroCertificatUtilisation()).isEqualTo(NUMERO);
        assertThat(codesVusPar(util, entreprise)).contains("CERTIFICAT_UTILISATION");
    }

    // ── les directions ─────────────────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void lesDirectionsLeVoientToujours() {
        UtilisationCredit util = douaneAu(StatutUtilisation.CERTIFICAT_EMIS);

        UtilisationCreditDto vu = service.findById(util.getId(), dgtcp);

        assertThat(vu.getCertificatUtilisationCommunicable()).isTrue();
        assertThat(vu.getNumeroCertificatUtilisation()).isEqualTo(NUMERO);
        assertThat(codesVusPar(util, dgtcp)).contains("CERTIFICAT_UTILISATION");
    }

    // ── l'état d'émission ──────────────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void lEtatDEmissionMasqueAussiLeNumero() {
        UtilisationCredit util = douaneAu(StatutUtilisation.CERTIFICAT_EMIS);

        CertificatUtilisationEmissionDto vuEntreprise = service.etatEmissionCertificat(util.getId(), entreprise);
        CertificatUtilisationEmissionDto vuDgtcp = service.etatEmissionCertificat(util.getId(), dgtcp);

        assertThat(vuEntreprise.getNumeroCertificatUtilisation()).isNull();
        assertThat(vuDgtcp.getNumeroCertificatUtilisation()).isEqualTo(NUMERO);
    }

    @Test
    @Transactional
    void lEtatDEmissionEstRefuseHorsPerimetre() {
        UtilisationCredit util = douaneAu(StatutUtilisation.ENVOYEE_AU_TRESOR);
        AuthenticatedUser autre = compteDUneAutreEntreprise();

        assertThatThrownBy(() -> service.etatEmissionCertificat(util.getId(), autre))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(ApiErrorCode.ACCESS_DENIED));
    }

    // ── fixtures ───────────────────────────────────────────────────────────────────────────────

    private List<String> codesVusPar(UtilisationCredit util, AuthenticatedUser user) {
        return documentService.findByUtilisationCreditId(util.getId(), user).stream()
                .map(DocumentUtilisationCreditDto::getCodeDocument)
                .toList();
    }

    private AuthenticatedUser compte(String username) {
        Utilisateur u = utilisateurRepository.findByUsername(username).orElseThrow();
        return new AuthenticatedUser(u.getId(), u.getUsername(), u.getRole());
    }

    private AuthenticatedUser compteDUneAutreEntreprise() {
        Entreprise autre = entrepriseRepository.save(Entreprise.builder()
                .raisonSociale("Autre entreprise " + System.nanoTime())
                .nif("NIF-AUTRE-" + System.nanoTime())
                .build());
        Utilisateur u = utilisateurRepository.save(Utilisateur.builder()
                .username("autre-" + System.nanoTime())
                .passwordHash("x")
                .role(Role.ENTREPRISE)
                .entreprise(autre)
                .actif(true)
                .build());
        return new AuthenticatedUser(u.getId(), u.getUsername(), u.getRole());
    }

    private CertificatCredit certificat() {
        return certificatRepository.findByNumero(DEMO_CERTIFICAT)
                .orElseThrow(() -> new IllegalStateException("Certificat seed absent: " + DEMO_CERTIFICAT));
    }

    /** Une utilisation douanière au statut donné, au certificat déjà émis et déposé. */
    private UtilisationCredit douaneAu(StatutUtilisation statut) {
        CertificatCredit cert = certificat();
        UtilisationDouaniere util = new UtilisationDouaniere();
        util.setType(TypeUtilisation.DOUANIER);
        util.setStatut(statut);
        util.setDateDemande(Instant.now());
        util.setMontant(BigDecimal.valueOf(100));
        util.setCertificatCredit(cert);
        util.setEntreprise(cert.getEntreprise());
        util.setNumeroDeclaration("DECL-CUV-" + System.nanoTime());
        util.setNumeroBulletin("BUL-CUV-" + System.nanoTime());
        return avecCertificat(util);
    }

    /** Une utilisation de TVA intérieure au statut donné, au certificat déjà émis et déposé. */
    private UtilisationCredit tvaAu(StatutUtilisation statut) {
        CertificatCredit cert = certificat();
        UtilisationTVAInterieure util = new UtilisationTVAInterieure();
        util.setType(TypeUtilisation.TVA_INTERIEURE);
        util.setStatut(statut);
        util.setDateDemande(Instant.now());
        util.setMontant(BigDecimal.valueOf(50));
        util.setMontantTVA(BigDecimal.valueOf(50));
        util.setNumeroFacture("FAC-CUV-" + System.nanoTime());
        util.setDateFacture(Instant.now());
        util.setCertificatCredit(cert);
        util.setEntreprise(cert.getEntreprise());
        return avecCertificat(util);
    }

    private UtilisationCredit avecCertificat(UtilisationCredit util) {
        util.setNumeroCertificatUtilisation(NUMERO);
        util.setDateCertificatUtilisation(Instant.now());
        UtilisationCredit saved = utilisationRepository.save(util);
        documentRepository.save(DocumentUtilisationCredit.builder()
                .codeDocument("CERTIFICAT_UTILISATION")
                .nomFichier("certificat-utilisation.pdf")
                .chemin("/api/local-files/cu-" + System.nanoTime() + ".pdf")
                .dateUpload(Instant.now())
                .taille(2048L)
                .version(1)
                .actif(true)
                .utilisationCredit(saved)
                .build());
        return saved;
    }
}
