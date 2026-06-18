package mr.gov.finances.sgci;

import mr.gov.finances.sgci.domain.entity.CertificatCredit;
import mr.gov.finances.sgci.domain.entity.Entreprise;
import mr.gov.finances.sgci.domain.entity.LigneBulletinLiquidation;
import mr.gov.finances.sgci.domain.entity.UtilisationDouaniere;
import mr.gov.finances.sgci.domain.enums.AffectationTaxe;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.domain.enums.StatutUtilisation;
import mr.gov.finances.sgci.domain.enums.TypeLigneTaxe;
import mr.gov.finances.sgci.domain.enums.TypeUtilisation;
import mr.gov.finances.sgci.repository.CertificatCreditRepository;
import mr.gov.finances.sgci.repository.DocumentUtilisationCreditRepository;
import mr.gov.finances.sgci.repository.LigneBulletinLiquidationRepository;
import mr.gov.finances.sgci.repository.UtilisationCreditRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.MinioService;
import mr.gov.finances.sgci.service.UtilisationCreditService;
import mr.gov.finances.sgci.web.dto.SaisirChequeRequest;
import mr.gov.finances.sgci.web.exception.ApiErrorCode;
import mr.gov.finances.sgci.web.exception.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Vérifie la règle fail-fast : échec MinIO → pas de changement de statut ni de document actif.
 */
@SpringBootTest
@ActiveProfiles("test")
class UtilisationCreditMinioFailFastIT {

    private static final String DEMO_CERTIFICAT = "CI-DEMO-SCEN-E";

    @MockBean
    private MinioService minioService;

    @Autowired
    private UtilisationCreditService utilisationCreditService;

    @Autowired
    private UtilisationCreditRepository utilisationCreditRepository;

    @Autowired
    private LigneBulletinLiquidationRepository ligneBulletinRepository;

    @Autowired
    private DocumentUtilisationCreditRepository documentUtilisationCreditRepository;

    @Autowired
    private CertificatCreditRepository certificatCreditRepository;

    private AuthenticatedUser dgdUser;
    private AuthenticatedUser entrepriseUser;

    @BeforeEach
    void setUpUsers() {
        dgdUser = new AuthenticatedUser(1L, "dgd", Role.DGD);
        entrepriseUser = new AuthenticatedUser(2L, "entreprise", Role.ENTREPRISE);
        when(minioService.uploadFile(any(MultipartFile.class)))
                .thenThrow(ApiException.serviceUnavailable(ApiErrorCode.OBJECT_STORAGE_UNAVAILABLE, "Stockage mock indisponible"));
    }

    @Test
    @Transactional
    void visaDgd_minioUnavailable_statutInchange() throws Exception {
        UtilisationDouaniere util = prepareUtilisationDouaniere(StatutUtilisation.DEMANDEE);
        Long ligneId = util.getLignes().get(0).getId();
        String decisionsJson = "[{\"ligneId\":" + ligneId + ",\"affectation\":\"AU_CI\",\"valeurTaxe\":100}]";
        MockMultipartFile file = new MockMultipartFile("file", "bulletin.pdf", "application/pdf", "pdf".getBytes());

        assertThatThrownBy(() -> utilisationCreditService.visaDgd(util.getId(), decisionsJson, file, dgdUser))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(503);

        UtilisationDouaniere reloaded = (UtilisationDouaniere) utilisationCreditRepository.findById(util.getId()).orElseThrow();
        assertThat(reloaded.getStatut()).isEqualTo(StatutUtilisation.DEMANDEE);
        assertThat(documentUtilisationCreditRepository
                .findByUtilisationCreditIdAndCodeDocumentAndActifTrue(util.getId(), "BULLETIN_ANNOTE"))
                .isEmpty();
    }

    @Test
    @Transactional
    void saisirCheque_minioUnavailable_statutEtDocumentInchanges() throws Exception {
        UtilisationDouaniere util = prepareUtilisationDouaniere(StatutUtilisation.EN_CONTROLE_DGD);
        SaisirChequeRequest request = SaisirChequeRequest.builder()
                .banqueNom("Banque Test")
                .numeroCheque("CHQ-001")
                .montantCheque(BigDecimal.valueOf(100))
                .dateCheque(Instant.now())
                .build();
        MockMultipartFile file = new MockMultipartFile("file", "cheque.pdf", "application/pdf", "pdf".getBytes());

        assertThatThrownBy(() -> utilisationCreditService.saisirCheque(util.getId(), request, file, entrepriseUser))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(503);

        UtilisationDouaniere reloaded = (UtilisationDouaniere) utilisationCreditRepository.findById(util.getId()).orElseThrow();
        assertThat(reloaded.getStatut()).isEqualTo(StatutUtilisation.EN_CONTROLE_DGD);
        assertThat(documentUtilisationCreditRepository
                .findByUtilisationCreditIdAndCodeDocumentAndActifTrue(util.getId(), "CHEQUE_CERTIFIE"))
                .isEmpty();
    }

    private UtilisationDouaniere prepareUtilisationDouaniere(StatutUtilisation statut) {
        CertificatCredit certificat = certificatCreditRepository.findByNumero(DEMO_CERTIFICAT)
                .orElseThrow(() -> new IllegalStateException("Certificat seed absent: " + DEMO_CERTIFICAT));
        Entreprise entreprise = certificat.getEntreprise();

        UtilisationDouaniere util = new UtilisationDouaniere();
        util.setType(TypeUtilisation.DOUANIER);
        util.setStatut(statut);
        util.setDateDemande(Instant.now());
        util.setMontant(BigDecimal.valueOf(100));
        util.setCertificatCredit(certificat);
        util.setEntreprise(entreprise);
        util.setNumeroDeclaration("DECL-TEST");
        util.setNumeroBulletin("BUL-TEST");
        util = (UtilisationDouaniere) utilisationCreditRepository.save(util);

        LigneBulletinLiquidation ligne = LigneBulletinLiquidation.builder()
                .utilisationDouaniere(util)
                .codeTaxe("DD")
                .denominationTaxe("Droit de Douane")
                .typeLigne(TypeLigneTaxe.ARTICLE)
                .valeurTaxe(BigDecimal.valueOf(100))
                .affectationEntreprise(AffectationTaxe.AU_CI)
                .build();
        ligne = ligneBulletinRepository.save(ligne);
        util.getLignes().add(ligne);
        return util;
    }
}
