package mr.gov.finances.sgci.service.archive;

import mr.gov.finances.sgci.domain.entity.AutoriteContractante;
import mr.gov.finances.sgci.domain.entity.Convention;
import mr.gov.finances.sgci.domain.entity.Entreprise;
import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.domain.enums.BlocReporting;
import mr.gov.finances.sgci.domain.enums.Granularite;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.repository.AutoriteContractanteRepository;
import mr.gov.finances.sgci.repository.ConventionRepository;
import mr.gov.finances.sgci.repository.EntrepriseRepository;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.ReportingService;
import mr.gov.finances.sgci.service.reporting.AFermerService;
import mr.gov.finances.sgci.service.reporting.BeneficiairesService;
import mr.gov.finances.sgci.service.reporting.BlocagesService;
import mr.gov.finances.sgci.service.reporting.EngagementEtatService;
import mr.gov.finances.sgci.service.reporting.IndicateursImpactService;
import mr.gov.finances.sgci.service.reporting.ProductionPeriodeService;
import mr.gov.finances.sgci.service.reporting.ReportingDossierService;
import mr.gov.finances.sgci.service.reporting.export.CsvReportWriter;
import mr.gov.finances.sgci.service.reporting.export.PdfReportWriter;
import mr.gov.finances.sgci.service.reporting.export.XlsxReportWriter;
import mr.gov.finances.sgci.service.reporting.model.ReportingDossier;
import mr.gov.finances.sgci.web.dto.archive.LotArchiveDtos.AffectationRequest;
import mr.gov.finances.sgci.web.dto.archive.LotArchiveDtos.CreditDto;
import mr.gov.finances.sgci.web.dto.archive.LotArchiveDtos.LotDto;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le reporting et ses exports après la reprise des vrais exports de période (non versionnés :
 * {@code -Dreleve.lot.dir=<dossier>}) — reproduit en local l'écran « Erreur de chargement » et
 * l'export en échec signalés en production le 04/11/2026.
 */
@SpringBootTest
@ActiveProfiles("test")
class ReportingApresRepriseIT {

    @Autowired private ImportArchiveLotService lots;
    @Autowired private EntrepriseRepository entrepriseRepository;
    @Autowired private AutoriteContractanteRepository autoriteRepository;
    @Autowired private ConventionRepository conventionRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private ReportingService reportingService;
    @Autowired private EngagementEtatService engagement;
    @Autowired private BlocagesService blocages;
    @Autowired private AFermerService aFermer;
    @Autowired private ProductionPeriodeService production;
    @Autowired private IndicateursImpactService impact;
    @Autowired private BeneficiairesService beneficiaires;
    @Autowired private ReportingDossierService dossiers;
    @Autowired private XlsxReportWriter xlsx;
    @Autowired private PdfReportWriter pdf;
    @Autowired private CsvReportWriter csv;

    @Test
    void reportingEtExportsApresLaRepriseDesVraisFichiers() throws Exception {
        String dossier = System.getProperty("releve.lot.dir");
        Assumptions.assumeTrue(dossier != null && !dossier.isBlank(), "releve.lot.dir non fourni");
        Utilisateur admin = utilisateurRepository.findByUsername("admin").orElseThrow();
        AuthenticatedUser adminAuth = new AuthenticatedUser(admin.getId(), "admin", Role.ADMIN_SI);
        importer(Path.of(dossier), adminAuth);

        Utilisateur p = utilisateurRepository.findByUsername("president").orElseThrow();
        AuthenticatedUser president = new AuthenticatedUser(p.getId(), "president", Role.PRESIDENT);
        Instant to = Instant.now();
        Instant from = to.minus(365, ChronoUnit.DAYS);

        Map<String, Callable<Object>> appels = new LinkedHashMap<>();
        appels.put("summary", () -> reportingService.getSummary(president, from, to, null, null));
        appels.put("timeseries", () -> reportingService.getDemandeTimeseries(president, from, to, null, null));
        appels.put("engagement", () -> engagement.parAxe(president, from, to, null, null, null));
        appels.put("blocages", () -> blocages.blocages(president, from, to, null, null, null));
        appels.put("a-fermer", () -> aFermer.aFermer(president, from, to, null, null, null, null));
        appels.put("production", () -> production.production(president, from, to, null, null, Granularite.MOIS));
        appels.put("impact", () -> impact.indicateurs(president, from, to, null, null));
        appels.put("beneficiaires", () -> beneficiaires.beneficiaires(president, from, to, null, null));
        appels.put("summary (sans dates)", () -> reportingService.getSummary(president, null, null, null, null));
        appels.put("engagement (sans dates)", () -> engagement.parAxe(president, null, null, null, null, null));
        appels.put("export", () -> {
            ReportingDossier d = dossiers.assembler(president, from, to, null, null, EnumSet.allOf(BlocReporting.class));
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            xlsx.ecrire(d, o);
            pdf.ecrire(d, new ByteArrayOutputStream());
            csv.ecrire(d, new ByteArrayOutputStream());
            return o.size();
        });

        List<String> echecs = new ArrayList<>();
        for (Map.Entry<String, Callable<Object>> e : appels.entrySet()) {
            long t0 = System.currentTimeMillis();
            try {
                e.getValue().call();
                System.out.printf("OK %-24s %6d ms%n", e.getKey(), System.currentTimeMillis() - t0);
            } catch (Exception ex) {
                Throwable racine = ex;
                while (racine.getCause() != null && racine.getCause() != racine) {
                    racine = racine.getCause();
                }
                System.out.printf("ÉCHEC %-21s %s : %s%n", e.getKey(), racine.getClass().getName(), racine.getMessage());
                StackTraceElement[] pile = racine.getStackTrace();
                for (int i = 0; i < Math.min(8, pile.length); i++) {
                    System.out.println("      at " + pile[i]);
                }
                echecs.add(e.getKey());
            }
        }
        assertThat(echecs).isEmpty();
    }

    private void importer(Path dossier, AuthenticatedUser admin) throws Exception {
        String n = String.valueOf(System.nanoTime());
        AutoriteContractante ac = autoriteRepository.save(AutoriteContractante.builder().nom("AC reprise " + n).code("R" + n).build());
        Convention conv = conventionRepository.save(Convention.builder()
                .reference("CONV-REP-" + n).intitule("Convention reprise").autoriteContractante(ac).build());
        List<Path> fichiers;
        try (Stream<Path> s = Files.list(dossier)) {
            fichiers = s.filter(f -> f.getFileName().toString().endsWith(".xls")).sorted().toList();
        }
        for (Path f : fichiers) {
            LotDto lot = lots.analyser(new MockMultipartFile("fichier", f.getFileName().toString(),
                    "application/vnd.ms-excel", Files.readAllBytes(f)), admin);
            for (String nif : lot.getCredits().stream().filter(c -> c.getEntrepriseId() == null)
                    .map(CreditDto::getNif).distinct().toList()) {
                Entreprise e = entrepriseRepository.save(Entreprise.builder().raisonSociale("Reprise " + nif).nif(nif).build());
                lots.affecter(lot.getId(), AffectationRequest.builder().nif(nif).entrepriseId(e.getId()).build());
            }
            for (CreditDto c : lot.getCredits().stream().filter(c -> c.getNumeroCredit() == null).toList()) {
                lots.affecter(lot.getId(), AffectationRequest.builder().creditIds(List.of(c.getId()))
                        .numeroCredit("SANS-NUM-L" + c.getLigneDebut()).dateCredit(java.time.LocalDate.of(2023, 1, 1)).build());
            }
            List<Long> ids = lot.getCredits().stream().map(CreditDto::getId).toList();
            lots.affecter(lot.getId(), AffectationRequest.builder().creditIds(ids)
                    .autoriteContractanteId(ac.getId()).conventionId(conv.getId()).confirmerMalgreAnomalies(true).build());
            for (int i = 0; i < ids.size(); i += 20) {
                lots.importer(lot.getId(), ids.subList(i, Math.min(i + 20, ids.size())), admin);
            }
        }
    }
}
