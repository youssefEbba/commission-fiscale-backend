package mr.gov.finances.sgci.service.archive;

import mr.gov.finances.sgci.domain.entity.AutoriteContractante;
import mr.gov.finances.sgci.domain.entity.CertificatCredit;
import mr.gov.finances.sgci.domain.entity.Convention;
import mr.gov.finances.sgci.domain.entity.Entreprise;
import mr.gov.finances.sgci.domain.entity.Marche;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.domain.enums.StatutMarche;
import mr.gov.finances.sgci.repository.AutoriteContractanteRepository;
import mr.gov.finances.sgci.repository.CertificatCreditRepository;
import mr.gov.finances.sgci.repository.ConventionRepository;
import mr.gov.finances.sgci.repository.EntrepriseRepository;
import mr.gov.finances.sgci.repository.MarcheRepository;
import mr.gov.finances.sgci.repository.UtilisationCreditRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.web.dto.archive.LotArchiveDtos.AffectationRequest;
import mr.gov.finances.sgci.web.dto.archive.LotArchiveDtos.CreditDto;
import mr.gov.finances.sgci.web.dto.archive.LotArchiveDtos.ImportResultatDto;
import mr.gov.finances.sgci.web.dto.archive.LotArchiveDtos.LotDto;
import mr.gov.finances.sgci.web.dto.archive.VerificationNifDto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/**
 * Reprise d'archive par fichier de période : analyse et propositions, compléments de l'opérateur,
 * import crédit par crédit (un échec n'annule pas les autres), crédit déjà repris complété.
 *
 * <p>Pas de {@code @Transactional} : l'import ouvre une transaction par crédit, que la transaction
 * d'un test masquerait. Chaque test crée ses propres données (numéros uniques).
 */
@SpringBootTest
@ActiveProfiles("test")
class ImportArchiveLotIT {

    private static final AuthenticatedUser ADMIN = new AuthenticatedUser(1L, "admin", Role.ADMIN_SI);

    @Autowired private ImportArchiveLotService service;
    @Autowired private EntrepriseRepository entrepriseRepository;
    @Autowired private AutoriteContractanteRepository autoriteRepository;
    @Autowired private ConventionRepository conventionRepository;
    @Autowired private CertificatCreditRepository certificatRepository;
    @Autowired private UtilisationCreditRepository utilisationRepository;
    @Autowired private MarcheRepository marcheRepository;

    @MockitoSpyBean private ImportArchiveCreditService importService;
    @MockitoBean private DgiNifClient dgi;

    private String n;
    private Entreprise titulaire;
    private AutoriteContractante autorite;
    private Convention convention;

    @BeforeEach
    void setUp() {
        n = String.valueOf(System.nanoTime() % 1_000_000_000L);
        titulaire = entrepriseRepository.save(Entreprise.builder()
                .raisonSociale("Titulaire lot " + n).nif("00" + n).build());
        autorite = autoriteRepository.save(AutoriteContractante.builder()
                .nom("Autorité lot " + n).code("AC" + n).build());
        convention = conventionRepository.save(Convention.builder()
                .reference("CONV-LOT-" + n).intitule("Convention lot " + n).autoriteContractante(autorite).build());
    }

    @Test
    void lAnalyseProposeEntrepriseAutoriteEtConventionEtSignaleCeQuiManque() {
        LotDto lot = service.analyser(fichierTrois("a"), ADMIN);

        assertThat(lot.getNbCredits()).isEqualTo(3);
        CreditDto connu = lot.getCredits().get(0);
        // Les éléments du calcul refont le solde affiché.
        assertThat(connu.getCreditDouanier().subtract(connu.getTotalUtilisationsDouane()).subtract(connu.getSortieTransfert()))
                .isEqualByComparingTo(connu.getSoldeDouanierCalcule());
        assertThat(connu.getCreditInterieur().add(connu.getTransfertCredit()).subtract(connu.getTotalUtilisationsInterieur()))
                .isEqualByComparingTo(connu.getSoldeInterieurCalcule());
        assertThat(connu.getEntrepriseId()).as("NIF sans les zéros de tête").isEqualTo(titulaire.getId());
        assertThat(connu.getSourceEntreprise()).isEqualTo("NIF");
        assertThat(connu.getAutoriteContractanteId()).isEqualTo(autorite.getId());
        assertThat(connu.getSourceAutorite()).isEqualTo("CODE_AC");
        assertThat(connu.getConventionId()).isEqualTo(convention.getId());
        assertThat(connu.getSourceConvention()).isEqualTo("CONVENTION_UNIQUE");
        assertThat(connu.isCreerMarche()).isTrue();
        assertThat(connu.getStatut()).isEqualTo("PRET");

        CreditDto inconnu = lot.getCredits().get(1);
        assertThat(inconnu.getManquants()).containsExactly("ENTREPRISE", "AUTORITE", "CONVENTION");
        assertThat(inconnu.getStatut()).isEqualTo("A_COMPLETER");
        assertThat(lot.getCredits().get(2).getManquants()).contains("ANOMALIES");
        assertThat(lot.getNifsSansEntreprise()).as("deux crédits, un seul NIF").isEqualTo(1);

        LotDto encore = service.analyser(fichierTrois("a"), ADMIN);
        assertThat(encore.isDejaAnalyse()).isTrue();
        assertThat(encore.getId()).isEqualTo(lot.getId());
    }

    @Test
    void lesComplementsParNifPuisLImportCreditParCredit() {
        LotDto lot = service.analyser(fichierTrois("b"), ADMIN);
        CreditDto transfert = lot.getCredits().get(0);
        CreditDto douanier = lot.getCredits().get(1);
        CreditDto ecart = lot.getCredits().get(2);

        Entreprise autre = entrepriseRepository.save(Entreprise.builder()
                .raisonSociale("Créée pendant la reprise " + n).nif("77" + n).build());
        service.affecter(lot.getId(), AffectationRequest.builder().nif("77" + n).entrepriseId(autre.getId()).build());
        service.affecter(lot.getId(), AffectationRequest.builder()
                .creditIds(List.of(douanier.getId(), ecart.getId()))
                .entrepriseId(autre.getId())
                .autoriteContractanteId(autorite.getId())
                .build());
        lot = service.affecter(lot.getId(), AffectationRequest.builder()
                .creditIds(List.of(ecart.getId())).confirmerMalgreAnomalies(true).build());
        assertThat(lot.getCredits()).extracting(CreditDto::getConventionId)
                .as("la seule convention de l'autorité est proposée").containsOnly(convention.getId());
        assertThat(lot.getParStatut().get("PRET")).isEqualTo(3L);

        // Le crédit douanier échoue : les deux autres sont importés quand même.
        doThrow(new IllegalStateException("panne simulée")).when(importService).importerReleve(
                argThat(r -> r != null && ("D" + n + "/2026").equals(r.getNumeroCredit())),
                any(), any(), any(), any(), anyBoolean(), any());
        ImportResultatDto resultat = service.importer(lot.getId(), null, ADMIN);

        assertThat(resultat.getImportes()).isEqualTo(2);
        assertThat(resultat.getErreurs()).isEqualTo(1);
        CreditDto enErreur = credit(service.detail(lot.getId()), douanier.getId());
        assertThat(enErreur.getStatut()).isEqualTo("ERREUR");
        assertThat(enErreur.getMessageErreur()).contains("panne simulée");

        // Soldes recalculés : le reliquat douanier sort au transfert, la TVA abonde l'intérieur.
        CreditDto importe = credit(service.detail(lot.getId()), transfert.getId());
        assertThat(importe.getStatut()).isEqualTo("IMPORTE");
        CertificatCredit certificat = certificatRepository.findById(importe.getCertificatId()).orElseThrow();
        assertThat(certificat.getSoldeCordon()).isEqualByComparingTo("0");
        assertThat(certificat.getSoldeTVA()).isEqualByComparingTo("2430379.73");

        // Marché créé à partir du relevé.
        Marche marche = marcheRepository.findById(importe.getMarcheId()).orElseThrow();
        assertThat(marche.getNumeroMarche()).isEqualTo("0349/T/038/CPMP/AC" + n + "/2022");
        assertThat(marche.getDateSignature()).isEqualTo(LocalDate.of(2022, 9, 3));
        assertThat(marche.getDureeExecution()).isEqualTo("75 JOURS");
        assertThat(marche.getStatut()).isEqualTo(StatutMarche.EN_COURS);
        assertThat(marche.getDemandeCorrection()).isNotNull();

        // L'écart est importé avec le solde calculé, pas celui affiché.
        CertificatCredit certificatEcart = certificatRepository.findById(
                credit(service.detail(lot.getId()), ecart.getId()).getCertificatId()).orElseThrow();
        assertThat(certificatEcart.getSoldeTVA()).isEqualByComparingTo("400000");

        // La panne passée, le crédit en erreur se reprend.
        Mockito.reset(importService);
        ImportResultatDto reprise = service.importer(lot.getId(), List.of(douanier.getId()), ADMIN);
        assertThat(reprise.getImportes()).isEqualTo(1);
        CertificatCredit certificatDouanier = certificatRepository.findById(
                reprise.getCredits().get(0).getCertificatId()).orElseThrow();
        assertThat(certificatDouanier.getSoldeCordon()).isEqualByComparingTo("1999332.09");
    }

    @Test
    void unCreditDejaReprisEstCompleteSansDoublonDUtilisations() {
        String numero = "T" + n + "/2023";
        LotDto premier = service.analyser(new ExportPeriodeFixture()
                .bloc(ExportPeriodeFixture.avecTransfert(n, numero, "0349/T/038/CPMP/AC" + n + "/2022 DU 03/09/2022"))
                .fichier("premier.xls"), ADMIN);
        service.importer(premier.getId(), null, ADMIN);
        Long certificatId = premier.getCredits().get(0).getId() != null
                ? credit(service.detail(premier.getId()), premier.getCredits().get(0).getId()).getCertificatId() : null;
        int utilisationsAvant = utilisationRepository.findByCertificatCreditId(certificatId).size();

        // Export suivant : le même crédit revient, avec un autre crédit à côté.
        LotDto second = service.analyser(new ExportPeriodeFixture()
                .bloc(ExportPeriodeFixture.avecTransfert(n, numero, "0349/T/038/CPMP/AC" + n + "/2022 DU 03/09/2022"))
                .bloc(ExportPeriodeFixture.douanier("55" + n, "S" + n + "/2026", "2030/T/" + n + "/CPMP/XX/2025 DU 06/10/2025"))
                .fichier("second.xls"), ADMIN);
        CreditDto repris = second.getCredits().get(0);
        assertThat(repris.isDejaRepris()).isTrue();
        assertThat(repris.getSourceEntreprise()).isEqualTo("CREDIT_EXISTANT");
        assertThat(repris.getStatut()).isEqualTo("PRET");

        ImportResultatDto resultat = service.importer(second.getId(), List.of(repris.getId()), ADMIN);

        assertThat(resultat.getImportes()).isEqualTo(1);
        assertThat(resultat.getCredits().get(0).getCertificatId()).isEqualTo(certificatId);
        assertThat(utilisationRepository.findByCertificatCreditId(certificatId)).hasSize(utilisationsAvant);
    }

    @Test
    void laDgiProposeLeNomEtLeNumeroManquantSeSaisit() {
        when(dgi.verifier(eq("66" + n))).thenReturn(VerificationNifDto.builder()
                .nif("66" + n).trouve(true).raisonSociale("SOCIETE DGI " + n).nomCommercial("SDGI").etat("2").build());
        ExportPeriodeFixture.Bloc modele = ExportPeriodeFixture.interieurEcart("66" + n, null, "0446/F/013/CPMP/AC" + n + "/2025 DU 23/02/26");
        ExportPeriodeFixture.Bloc sansNumero = new ExportPeriodeFixture.Bloc(modele.nif(), modele.referenceMarche(),
                modele.duree(), "00/00/0000", null, modele.montantMarche(), modele.montantCredit(), modele.transfert(),
                modele.creditDouanier(), modele.creditInterieur(), modele.soldeDouanier(), "400.000,00",
                modele.ligneTransfert(), modele.utilisations());

        LotDto lot = service.interrogerDgi(service.analyser(new ExportPeriodeFixture().bloc(sansNumero)
                .fichier("dgi.xls"), ADMIN).getId());

        CreditDto c = lot.getCredits().get(0);
        assertThat(c.getRaisonSocialeDgi()).isEqualTo("SOCIETE DGI " + n);
        assertThat(c.getNomCommercialDgi()).isEqualTo("SDGI");
        assertThat(lot.getNifsAInterrogerDgi()).isZero();
        assertThat(c.getManquants()).contains("NUMERO", "DATE", "ENTREPRISE");

        lot = service.affecter(lot.getId(), AffectationRequest.builder().creditIds(List.of(c.getId()))
                .numeroCredit("M" + n + "/2023").dateCredit(LocalDate.of(2023, 5, 2)).entrepriseId(titulaire.getId()).build());
        assertThat(lot.getCredits().get(0).getNumeroCredit()).isEqualTo("M" + n + "/2023");
        assertThat(lot.getCredits().get(0).getStatut()).isEqualTo("PRET");
    }

    /**
     * Les vrais exports de période, non versionnés ({@code -Dreleve.lot.dir=<dossier>}), importés de
     * bout en bout comme le ferait l'écran : entreprises créées pour les NIF inconnus, autorité et
     * convention affectées à tout le lot, anomalies confirmées, import par paquets de 10.
     */
    @Test
    void vraisExportsImportesDeBoutEnBout() throws java.io.IOException {
        String dossier = System.getProperty("releve.lot.dir");
        org.junit.jupiter.api.Assumptions.assumeTrue(dossier != null && !dossier.isBlank(), "releve.lot.dir non fourni");
        List<java.nio.file.Path> fichiers;
        try (java.util.stream.Stream<java.nio.file.Path> s = java.nio.file.Files.list(java.nio.file.Path.of(dossier))) {
            fichiers = s.filter(p -> p.getFileName().toString().endsWith(".xls")).sorted().toList();
        }
        for (java.nio.file.Path p : fichiers) {
            LotDto lot = service.analyser(new MockMultipartFile("fichier", p.getFileName().toString(),
                    "application/vnd.ms-excel", java.nio.file.Files.readAllBytes(p)), ADMIN);

            for (String nif : lot.getCredits().stream().filter(c -> c.getEntrepriseId() == null)
                    .map(CreditDto::getNif).distinct().toList()) {
                Entreprise e = entrepriseRepository.save(Entreprise.builder()
                        .raisonSociale("Reprise NIF " + nif).nif(nif).build());
                service.affecter(lot.getId(), AffectationRequest.builder().nif(nif).entrepriseId(e.getId()).build());
            }
            for (CreditDto c : lot.getCredits().stream().filter(c -> c.getNumeroCredit() == null).toList()) {
                service.affecter(lot.getId(), AffectationRequest.builder().creditIds(List.of(c.getId()))
                        .numeroCredit("SANS-NUMERO-L" + c.getLigneDebut()).dateCredit(LocalDate.of(2023, 1, 1)).build());
            }
            List<Long> ids = lot.getCredits().stream().map(CreditDto::getId).toList();
            lot = service.affecter(lot.getId(), AffectationRequest.builder().creditIds(ids)
                    .autoriteContractanteId(autorite.getId()).conventionId(convention.getId())
                    .confirmerMalgreAnomalies(true).build());
            assertThat(lot.getParStatut().get("PRET")).as(p.getFileName().toString()).isEqualTo((long) ids.size());

            int importes = 0;
            int erreurs = 0;
            for (int i = 0; i < ids.size(); i += 10) {
                ImportResultatDto r = service.importer(lot.getId(), ids.subList(i, Math.min(i + 10, ids.size())), ADMIN);
                importes += r.getImportes();
                erreurs += r.getErreurs();
                r.getCredits().stream().filter(c -> c.getMessageErreur() != null)
                        .forEach(c -> System.out.printf("   ERREUR %s : %s%n", c.getNumeroCredit(), c.getMessageErreur()));
            }
            System.out.printf("%s : %d crédit(s) importé(s), %d en erreur%n", p.getFileName(), importes, erreurs);
            assertThat(erreurs).as(p.getFileName().toString()).isZero();
            assertThat(importes).isEqualTo(ids.size());

            // Un crédit répété dans l'export n'a qu'un certificat : c'est le dernier bloc importé qui fixe ses soldes.
            java.util.Map<Long, CreditDto> dernierParCertificat = new java.util.LinkedHashMap<>();
            service.detail(lot.getId()).getCredits().forEach(c -> dernierParCertificat.put(c.getCertificatId(), c));
            long repetes = lot.getCredits().size() - dernierParCertificat.size();
            System.out.printf("%s : %d bloc(s) répétant un N° de crédit déjà lu%n", p.getFileName(), repetes);
            for (CreditDto c : dernierParCertificat.values()) {
                CertificatCredit cert = certificatRepository.findById(c.getCertificatId()).orElseThrow();
                assertThat(cert.getSoldeCordon()).as(c.getNumeroCredit())
                        .isEqualByComparingTo(c.getSoldeDouanierCalcule().max(java.math.BigDecimal.ZERO));
                assertThat(cert.getSoldeTVA()).as(c.getNumeroCredit())
                        .isEqualByComparingTo(c.getSoldeInterieurCalcule().max(java.math.BigDecimal.ZERO));
            }
        }
    }

    private MockMultipartFile fichierTrois(String variante) {
        return new ExportPeriodeFixture()
                .bloc(ExportPeriodeFixture.avecTransfert(n, "T" + n + variante + "/2023",
                        "0349/T/038/CPMP/AC" + n + "/2022 DU 03/09/2022"))
                .bloc(ExportPeriodeFixture.douanier("77" + n, "D" + n + "/2026", "2030/T/" + n + "/CPMP/XX/2025 DU 06/10/2025"))
                .bloc(ExportPeriodeFixture.interieurEcart("77" + n, "E" + n + variante + "/2026", "0446/F/" + n + variante + "/CPMP/YY/2025 DU 23/02/26"))
                .fichier("export_" + n + variante + ".xls");
    }

    private static CreditDto credit(LotDto lot, Long id) {
        return lot.getCredits().stream().filter(c -> c.getId().equals(id)).findFirst().orElseThrow();
    }
}
