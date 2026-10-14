package mr.gov.finances.sgci;

import mr.gov.finances.sgci.domain.entity.CertificatCredit;
import mr.gov.finances.sgci.domain.entity.Entreprise;
import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.domain.enums.BlocReporting;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.domain.enums.StatutCertificat;
import mr.gov.finances.sgci.repository.CertificatCreditRepository;
import mr.gov.finances.sgci.repository.EntrepriseRepository;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.reporting.EngagementEtatService;
import mr.gov.finances.sgci.service.reporting.ReportingDossierService;
import mr.gov.finances.sgci.service.reporting.export.CsvReportWriter;
import mr.gov.finances.sgci.service.reporting.export.PdfReportWriter;
import mr.gov.finances.sgci.service.reporting.export.XlsxReportWriter;
import mr.gov.finances.sgci.service.reporting.model.ReportingDossier;
import mr.gov.finances.sgci.web.dto.reporting.EngagementEtatDto;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Les trois exports disent la même chose que les routes JSON, et respectent le périmètre.
 *
 * <p>{@link #leClasseurDonneLeMemeChiffreQueLeService()} attrape la régression qui compte vraiment :
 * un export qui a fini par avoir sa propre requête, juste au départ, puis divergente à mesure que
 * l'écran évolue — et plus personne ne sait lequel des deux chiffres croire. Le modèle neutre unique
 * est précisément là pour l'empêcher, et ce test le vérifie.
 *
 * <p>{@link #leClasseurDUneEntrepriseNeNommePasUneAutre()} attrape l'autre : le périmètre appliqué à
 * la réponse JSON mais oublié dans le fichier. C'est la fuite la plus facile à ne pas voir, puisque
 * personne n'ouvre le classeur d'un autre utilisateur.
 *
 * <p>C'est la signature {@code (dossier, flux)} des rédacteurs qui rend tout cela vérifiable ici :
 * un flux mémoire en test, le flux de la réponse en production, sans jamais démarrer Tomcat.
 */
@SpringBootTest
@ActiveProfiles("test")
class ExportReportingIT {

    private static final Instant DEBUT = Instant.now().minus(30, ChronoUnit.DAYS);
    private static final Instant FIN = Instant.now().plus(1, ChronoUnit.DAYS);

    @Autowired
    private ReportingDossierService dossierService;

    @Autowired
    private EngagementEtatService engagementEtatService;

    @Autowired
    private XlsxReportWriter xlsx;

    @Autowired
    private CsvReportWriter csv;

    @Autowired
    private PdfReportWriter pdf;

    @Autowired
    private CertificatCreditRepository certificatRepository;

    @Autowired
    private EntrepriseRepository entrepriseRepository;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    private AuthenticatedUser president;
    private Entreprise titulaire;

    @BeforeEach
    void setUp() {
        Utilisateur u = utilisateurRepository.findByUsername("president").orElseThrow();
        president = new AuthenticatedUser(u.getId(), u.getUsername(), Role.PRESIDENT);
        titulaire = entrepriseRepository.save(Entreprise.builder()
                .raisonSociale("Titulaire export " + System.nanoTime())
                .nif("NIF-EXP-" + System.nanoTime())
                .build());
    }

    // ── l'égalité avec le service ──────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void leClasseurDonneLeMemeChiffreQueLeService() throws Exception {
        certificat(BigDecimal.valueOf(150), BigDecimal.valueOf(200));

        EngagementEtatDto attendu = engagementEtatService.parAxe(
                president, DEBUT, FIN, null, titulaire.getId(), null);
        byte[] fichier = classeur(Set.of(BlocReporting.ENGAGEMENT));

        double totalAttendu = attendu.getTotal().getAccordeTotal().doubleValue();
        assertThat(totalAttendu).isEqualTo(350.0);
        assertThat(valeursNumeriques(fichier))
                .as("le total d'engagement du service doit figurer dans le classeur")
                .contains(totalAttendu);
    }

    @Test
    @Transactional
    void lesMontantsSontDesNombresEtNonDuTexte() throws Exception {
        // Un montant laissé en texte dans un classeur est un montant qu'on ne peut pas additionner,
        // et c'est la première chose que fera le lecteur.
        certificat(BigDecimal.valueOf(150), BigDecimal.valueOf(200));

        assertThat(valeursNumeriques(classeur(Set.of(BlocReporting.ENGAGEMENT))))
                .as("au moins une cellule numérique attendue")
                .isNotEmpty();
    }

    // ── le périmètre ───────────────────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void leClasseurDUneEntrepriseNeNommePasUneAutre() throws Exception {
        Entreprise autre = entrepriseRepository.save(Entreprise.builder()
                .raisonSociale("Concurrent à ne pas divulguer " + System.nanoTime())
                .nif("NIF-AUTRE-" + System.nanoTime())
                .build());
        certificatPour(autre, BigDecimal.valueOf(999));
        certificat(BigDecimal.valueOf(150), BigDecimal.valueOf(200));

        Utilisateur compteEntreprise = utilisateurRepository.findByUsername("entreprise").orElseThrow();
        AuthenticatedUser vueEntreprise = new AuthenticatedUser(
                compteEntreprise.getId(), compteEntreprise.getUsername(), Role.ENTREPRISE);

        ReportingDossier dossier = dossierService.assembler(
                vueEntreprise, DEBUT, FIN, null, null, Set.of(BlocReporting.ENGAGEMENT));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        xlsx.ecrire(dossier, out);

        String contenu = toutLeTexte(out.toByteArray());
        assertThat(contenu)
                .as("le périmètre doit s'appliquer au fichier autant qu'à la réponse JSON")
                .doesNotContain(autre.getRaisonSociale())
                .doesNotContain(titulaire.getRaisonSociale());
    }

    // ── les deux autres formats ────────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void leCsvEstLisibleEtPorteSonEnTete() throws Exception {
        certificat(BigDecimal.valueOf(150), BigDecimal.valueOf(200));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        csv.ecrire(dossier(Set.of(BlocReporting.ENGAGEMENT)), out);

        String contenu = out.toString(StandardCharsets.UTF_8);

        // La marque d'ordre d'octets : sans elle, Excel sous Windows abîme les accents.
        assertThat(contenu).startsWith("﻿");
        assertThat(contenu).contains("section;ligne;colonne;valeur");
        assertThat(contenu).contains("Engagement de l'État");
    }

    @Test
    @Transactional
    void lePdfEstUnPdfLisible() throws Exception {
        certificat(BigDecimal.valueOf(150), BigDecimal.valueOf(200));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        pdf.ecrire(dossier(null), out);

        try (PDDocument document = Loader.loadPDF(out.toByteArray())) {
            assertThat(document.getNumberOfPages()).isPositive();
        }
    }

    @Test
    @Transactional
    void unCaractereHorsJeuNeFaitPasEchouerLePdf() throws Exception {
        // Les polices intégrées lèvent sur tout caractère hors WinAnsi. Un état qui échoue à cause
        // d'une espace fine invisible dans un libellé est le pire mode de panne possible.
        ReportingDossier avecArabe = new ReportingDossier(
                new ReportingDossier.EnTete("État", "périmètre fine", "fenêtre",
                        "auteur", Instant.now(), "mention العربية"),
                java.util.List.of(new ReportingDossier.Section("Section ’test’",
                        java.util.List.of("Colonne"),
                        java.util.List.of(java.util.List.of("valeur العربية")),
                        null, "note … suite")));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        pdf.ecrire(avecArabe, out);

        try (PDDocument document = Loader.loadPDF(out.toByteArray())) {
            assertThat(document.getNumberOfPages()).isPositive();
        }
    }

    // ── fixtures ───────────────────────────────────────────────────────────────────────────────

    private ReportingDossier dossier(Set<BlocReporting> blocs) {
        return dossierService.assembler(president, DEBUT, FIN, null, titulaire.getId(), blocs);
    }

    private byte[] classeur(Set<BlocReporting> blocs) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        xlsx.ecrire(dossier(blocs), out);
        return out.toByteArray();
    }

    private java.util.List<Double> valeursNumeriques(byte[] fichier) throws Exception {
        java.util.List<Double> out = new java.util.ArrayList<>();
        try (Workbook classeur = WorkbookFactory.create(new ByteArrayInputStream(fichier))) {
            for (Sheet feuille : classeur) {
                for (Row ligne : feuille) {
                    for (Cell cellule : ligne) {
                        if (cellule.getCellType() == CellType.NUMERIC) {
                            out.add(cellule.getNumericCellValue());
                        }
                    }
                }
            }
        }
        return out;
    }

    private String toutLeTexte(byte[] fichier) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (Workbook classeur = WorkbookFactory.create(new ByteArrayInputStream(fichier))) {
            for (Sheet feuille : classeur) {
                sb.append(feuille.getSheetName()).append('\n');
                for (Row ligne : feuille) {
                    for (Cell cellule : ligne) {
                        if (cellule.getCellType() == CellType.STRING) {
                            sb.append(cellule.getStringCellValue()).append('\n');
                        }
                    }
                }
            }
        }
        return sb.toString();
    }

    private CertificatCredit certificat(BigDecimal cordon, BigDecimal tvaInterieure) {
        return certificatPour(titulaire, cordon, tvaInterieure);
    }

    private CertificatCredit certificatPour(Entreprise e, BigDecimal cordon) {
        return certificatPour(e, cordon, BigDecimal.ZERO);
    }

    private CertificatCredit certificatPour(Entreprise e, BigDecimal cordon,
                                            BigDecimal tvaInterieure) {
        return certificatRepository.saveAndFlush(CertificatCredit.builder()
                .numero("CI-EXP-" + System.nanoTime())
                .dateEmission(Instant.now())
                .statut(StatutCertificat.OUVERT)
                .entreprise(e)
                .montantCordon(cordon)
                .droitsEtTaxesDouaneHorsTva(cordon)
                .taxesConsommation(BigDecimal.ZERO)
                .tvaImportationDouaneAccordee(BigDecimal.ZERO)
                .montantTVAInterieure(tvaInterieure)
                .soldeCordon(cordon)
                .tvaImportationDouane(BigDecimal.ZERO)
                .soldeTVA(tvaInterieure)
                .build());
    }
}
