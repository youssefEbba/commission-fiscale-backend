package mr.gov.finances.sgci.service.archive;

import mr.gov.finances.sgci.domain.entity.AutoriteContractante;
import mr.gov.finances.sgci.domain.entity.CertificatCredit;
import mr.gov.finances.sgci.domain.entity.Convention;
import mr.gov.finances.sgci.domain.entity.DemandeCorrection;
import mr.gov.finances.sgci.domain.entity.Entreprise;
import mr.gov.finances.sgci.domain.entity.Marche;
import mr.gov.finances.sgci.domain.entity.UtilisationTVAInterieure;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.domain.enums.StatutCertificat;
import mr.gov.finances.sgci.domain.enums.StatutUtilisation;
import mr.gov.finances.sgci.domain.enums.TypeUtilisation;
import mr.gov.finances.sgci.repository.AutoriteContractanteRepository;
import mr.gov.finances.sgci.repository.CertificatCreditRepository;
import mr.gov.finances.sgci.repository.ConventionRepository;
import mr.gov.finances.sgci.repository.DemandeCorrectionRepository;
import mr.gov.finances.sgci.repository.EntrepriseRepository;
import mr.gov.finances.sgci.repository.LotImportArchiveCreditRepository;
import mr.gov.finances.sgci.repository.MarcheRepository;
import mr.gov.finances.sgci.repository.TvaDeductibleStockRepository;
import mr.gov.finances.sgci.repository.UtilisationCreditRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.web.dto.archive.ArchiveCreditAdminDtos.ArchiveCreditDto;
import mr.gov.finances.sgci.web.dto.archive.ArchiveCreditAdminDtos.RattachementRequest;
import mr.gov.finances.sgci.web.dto.archive.LotArchiveDtos.CreditDto;
import mr.gov.finances.sgci.web.dto.archive.LotArchiveDtos.ImportResultatDto;
import mr.gov.finances.sgci.web.dto.archive.LotArchiveDtos.LotDto;
import mr.gov.finances.sgci.web.exception.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Crédit repris de l'archive : l'administrateur corrige son rattachement, ou le supprime avec tout ce
 * qui s'y rattache (même ce qui a été fait dans l'application depuis l'import), puis le réimporte.
 *
 * <p>Pas de {@code @Transactional} : l'import ouvre une transaction par crédit.
 */
@SpringBootTest
@ActiveProfiles("test")
class ArchiveCreditAdminIT {

    private static final AuthenticatedUser ADMIN = new AuthenticatedUser(1L, "admin", Role.ADMIN_SI);

    @Autowired private ImportArchiveLotService lotService;
    @Autowired private ArchiveCreditAdminService service;
    @Autowired private EntrepriseRepository entrepriseRepository;
    @Autowired private AutoriteContractanteRepository autoriteRepository;
    @Autowired private ConventionRepository conventionRepository;
    @Autowired private CertificatCreditRepository certificatRepository;
    @Autowired private DemandeCorrectionRepository demandeRepository;
    @Autowired private UtilisationCreditRepository utilisationRepository;
    @Autowired private TvaDeductibleStockRepository stockRepository;
    @Autowired private MarcheRepository marcheRepository;
    @Autowired private LotImportArchiveCreditRepository lotCreditRepository;
    @Autowired private mr.gov.finances.sgci.repository.TransfertCreditRepository transfertRepository;
    @Autowired private TransactionTemplate tx;

    @MockitoBean private DgiNifClient dgi;

    private String n;
    private Entreprise titulaire;
    private AutoriteContractante autorite;

    @BeforeEach
    void setUp() {
        n = String.valueOf(System.nanoTime() % 1_000_000_000L);
        titulaire = entrepriseRepository.save(Entreprise.builder().raisonSociale("Titulaire " + n).nif("00" + n).build());
        autorite = autoriteRepository.save(AutoriteContractante.builder().nom("Autorité " + n).code("AC" + n).build());
        conventionRepository.save(Convention.builder()
                .reference("CONV-ADM-" + n).intitule("Convention " + n).autoriteContractante(autorite).build());
    }

    @Test
    void lAdministrateurCorrigeLeRattachementDUnCreditDArchive() {
        Import imp = importerUnCredit();
        Entreprise autre = entrepriseRepository.save(Entreprise.builder().raisonSociale("Bonne entreprise " + n).nif("55" + n).build());
        AutoriteContractante autreAc = autoriteRepository.save(AutoriteContractante.builder().nom("Bonne AC " + n).code("BAC" + n).build());
        Convention autreConv = conventionRepository.save(Convention.builder()
                .reference("CONV-BON-" + n).intitule("Bonne convention").autoriteContractante(autreAc).build());
        Long ancienMarche = service.detail(imp.certificatId).getMarcheId();

        ArchiveCreditDto dto = service.corrigerRattachement(imp.certificatId, RattachementRequest.builder()
                .entrepriseId(autre.getId()).autoriteContractanteId(autreAc.getId()).conventionId(autreConv.getId())
                .marcheId(null).motif("Mauvaise entreprise à la reprise").build(), ADMIN);

        assertThat(dto.getEntrepriseId()).isEqualTo(autre.getId());
        assertThat(dto.getAutoriteContractanteId()).isEqualTo(autreAc.getId());
        assertThat(dto.getConventionId()).isEqualTo(autreConv.getId());
        assertThat(dto.getMarcheId()).isNull();
        tx.executeWithoutResult(s -> {
            CertificatCredit c = certificatRepository.findById(imp.certificatId).orElseThrow();
            assertThat(c.getEntreprise().getId()).isEqualTo(autre.getId());
            assertThat(utilisationRepository.findByCertificatCreditId(imp.certificatId))
                    .isNotEmpty().allSatisfy(u -> assertThat(u.getEntreprise().getId()).isEqualTo(autre.getId()));
            assertThat(marcheRepository.findById(ancienMarche).orElseThrow().getDemandeCorrection())
                    .as("l'ancien marché est détaché").isNull();
            assertThat(lotCreditRepository.findByCertificatId(imp.certificatId))
                    .allSatisfy(lc -> assertThat(lc.getEntreprise().getId()).isEqualTo(autre.getId()));
        });

        // Une convention qui n'est pas celle de l'autorité est refusée.
        assertThatThrownBy(() -> service.corrigerRattachement(imp.certificatId, RattachementRequest.builder()
                .entrepriseId(autre.getId()).autoriteContractanteId(autorite.getId()).conventionId(autreConv.getId())
                .motif("essai").build(), ADMIN))
                .isInstanceOf(ApiException.class).hasMessageContaining("n'est pas rattachée");
    }

    @Test
    void laSuppressionEffaceToutEtLeCreditSeReimporte() {
        Import imp = importerUnCredit();
        // Une utilisation créée dans l'application après l'import : elle part aussi.
        CertificatCredit cert = certificatRepository.findById(imp.certificatId).orElseThrow();
        UtilisationTVAInterieure app = new UtilisationTVAInterieure();
        app.setType(TypeUtilisation.TVA_INTERIEURE);
        app.setStatut(StatutUtilisation.DEMANDEE);
        app.setDateDemande(Instant.now());
        app.setMontant(BigDecimal.TEN);
        app.setMontantTVA(BigDecimal.TEN);
        app.setNumeroFacture("FAC-APP-" + n);
        app.setCertificatCredit(cert);
        app.setEntreprise(cert.getEntreprise());
        utilisationRepository.save(app);

        ArchiveCreditDto avant = service.detail(imp.certificatId);
        assertThat(avant.getSuppression().getUtilisationsArchive()).isPositive();
        assertThat(avant.getSuppression().getUtilisationsApplication()).isEqualTo(1);
        assertThat(avant.getSuppression().getTransferts()).isEqualTo(1);
        assertThat(avant.isMarcheCreeParImport()).isTrue();
        assertThat(transfertRepository.findByCertificatCreditId(imp.certificatId))
                .extracting(t -> t.getReference()).containsExactly("ARCHIVE-TR-T" + n + "/2023");
        Long marcheId = avant.getMarcheId();
        Long demandeId = avant.getDemandeCorrectionId();

        service.supprimer(imp.certificatId, "Reprise à refaire", ADMIN);

        assertThat(certificatRepository.findById(imp.certificatId)).isEmpty();
        assertThat(utilisationRepository.findByCertificatCreditId(imp.certificatId)).isEmpty();
        assertThat(stockRepository.findByCertificatCreditIdOrderByDateCreationAsc(imp.certificatId)).isEmpty();
        assertThat(demandeRepository.findById(demandeId)).isEmpty();
        assertThat(marcheRepository.findById(marcheId)).as("marché créé par l'import").isEmpty();
        tx.executeWithoutResult(s -> {
            var lc = lotCreditRepository.findById(imp.creditId).orElseThrow();
            assertThat(lc.getCertificat()).isNull();
            assertThat(lc.getDejaRepris()).isFalse();
            assertThat(lc.getStatut().name()).isIn("PRET", "A_COMPLETER");
        });

        // Le crédit se réimporte, sous la même référence.
        ImportResultatDto encore = lotService.importer(imp.lotId, List.of(imp.creditId), ADMIN);
        assertThat(encore.getImportes()).isEqualTo(1);
        CertificatCredit nouveau = certificatRepository.findById(encore.getCredits().get(0).getCertificatId()).orElseThrow();
        assertThat(nouveau.getReference()).isEqualTo(imp.reference);
    }

    @Test
    void unCreditCreeDansLApplicationNiUnNonAdministrateurNePeuvent() {
        CertificatCredit cree = new CertificatCredit();
        cree.setNumero("CERT-APP-" + n);
        cree.setReference("CR-APP-" + n);
        cree.setEntreprise(titulaire);
        cree.setStatut(StatutCertificat.OUVERT);
        Long id = certificatRepository.save(cree).getId();
        assertThatThrownBy(() -> service.supprimer(id, "motif", ADMIN))
                .isInstanceOf(ApiException.class).hasMessageContaining("repris de l'archive");

        Import imp = importerUnCredit();
        AuthenticatedUser dgtcp = new AuthenticatedUser(2L, "dgtcp", Role.DGTCP);
        assertThatThrownBy(() -> service.supprimer(imp.certificatId, "motif", dgtcp)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.supprimer(imp.certificatId, " ", ADMIN))
                .isInstanceOf(ApiException.class).hasMessageContaining("motif");
        assertThat(certificatRepository.findById(imp.certificatId)).isPresent();
    }

    private record Import(Long lotId, Long creditId, Long certificatId, String reference) {
    }

    private Import importerUnCredit() {
        String numero = "T" + n + "/2023";
        LotDto lot = lotService.analyser(new ExportPeriodeFixture()
                .bloc(ExportPeriodeFixture.avecTransfert(n, numero, "0349/T/038/CPMP/AC" + n + "/2022 DU 03/09/2022"))
                .fichier("admin_" + n + ".xls"), ADMIN);
        CreditDto credit = lot.getCredits().get(0);
        assertThat(credit.getStatut()).isEqualTo("PRET");
        ImportResultatDto res = lotService.importer(lot.getId(), List.of(credit.getId()), ADMIN);
        assertThat(res.getImportes()).isEqualTo(1);
        Long certificatId = res.getCredits().get(0).getCertificatId();
        DemandeCorrection d = certificatRepository.findById(certificatId).map(CertificatCredit::getDemandeCorrection).orElseThrow();
        assertThat(d).isNotNull();
        Marche m = marcheRepository.findByDemandeCorrectionId(d.getId()).orElse(null);
        assertThat(m).as("marché créé par l'import").isNotNull();
        return new Import(lot.getId(), credit.getId(), certificatId, "ARCHIVE-" + numero);
    }
}
