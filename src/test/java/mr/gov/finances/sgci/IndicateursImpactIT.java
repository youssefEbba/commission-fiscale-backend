package mr.gov.finances.sgci;

import mr.gov.finances.sgci.domain.entity.DemandeCorrection;
import mr.gov.finances.sgci.domain.entity.Entreprise;
import mr.gov.finances.sgci.domain.entity.HistoriqueStatut;
import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.domain.enums.BlocReporting;
import mr.gov.finances.sgci.domain.enums.IndicateurReference;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.domain.enums.StatutDemande;
import mr.gov.finances.sgci.repository.DemandeCorrectionRepository;
import mr.gov.finances.sgci.repository.EntrepriseRepository;
import mr.gov.finances.sgci.repository.HistoriqueStatutRepository;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.reporting.IndicateursImpactService;
import mr.gov.finances.sgci.service.reporting.ReportingDossierService;
import mr.gov.finances.sgci.service.reporting.ValeurReferenceService;
import mr.gov.finances.sgci.service.reporting.model.ReportingDossier;
import mr.gov.finances.sgci.web.dto.reporting.IndicateursImpactDto;
import mr.gov.finances.sgci.web.dto.reporting.ValeurReferenceDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Les indicateurs d'impact (cahier des charges, point 2) : chaque mesure est vérifiée sur un
 * dossier dont on connaît la réponse, isolé par le filtre entreprise.
 */
@SpringBootTest
@ActiveProfiles("test")
class IndicateursImpactIT {

    private static final Instant T0 = Instant.now().minus(20, ChronoUnit.DAYS);

    @Autowired private IndicateursImpactService service;
    @Autowired private ValeurReferenceService referenceService;
    @Autowired private ReportingDossierService dossierService;
    @Autowired private DemandeCorrectionRepository demandeRepository;
    @Autowired private EntrepriseRepository entrepriseRepository;
    @Autowired private HistoriqueStatutRepository historiqueRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;

    private Entreprise titulaire;
    private AuthenticatedUser president;

    @BeforeEach
    void setUp() {
        titulaire = entrepriseRepository.save(Entreprise.builder()
                .raisonSociale("Titulaire impact " + System.nanoTime())
                .nif("NIF-IMP-" + System.nanoTime())
                .build());
        Utilisateur u = utilisateurRepository.findByUsername("president").orElseThrow();
        president = new AuthenticatedUser(u.getId(), u.getUsername(), Role.PRESIDENT);
    }

    @Test
    @Transactional
    void lesDelaisParPhaseSeLisentSurLHistoriqueDesStatuts() {
        DemandeCorrection adoptee = demande(StatutDemande.ADOPTEE, "1000", "500");
        transition(adoptee, "RECEVABLE", 2);
        transition(adoptee, "EN_EVALUATION", 3);
        transition(adoptee, "EN_VALIDATION", 10);
        transition(adoptee, "ADOPTEE", 12);
        demande(StatutDemande.REJETEE, "200", "0");

        IndicateursImpactDto dto = indicateurs();

        IndicateursImpactDto.Processus correction = processus(dto, "CORRECTION");
        assertThat(correction.getDeposes()).isEqualTo(2);
        assertThat(correction.getTraites()).isEqualTo(2);
        assertThat(correction.getTauxValidationPct()).isEqualTo(50.0);
        assertThat(correction.getTauxRejetPct()).isEqualTo(50.0);

        assertThat(phase(dto, "CORRECTION", "Total : dépôt → décision").getMoyenneJours()).isEqualTo(12.0);
        assertThat(phase(dto, "CORRECTION", "Instruction par les directions (visas)").getMoyenneJours()).isEqualTo(7.0);
        assertThat(phase(dto, "CORRECTION", "Décision du Président").getNbDossiers()).isEqualTo(1);
    }

    @Test
    @Transactional
    void lesMontantsDemandesEtRetenusSuiventLesDemandes() {
        DemandeCorrection d = demande(StatutDemande.ADOPTEE, "1000", "500");
        d.setCreditInterieurDeclare(new BigDecimal("1200"));
        d.setCreditExterieurDeclare(new BigDecimal("600"));
        demandeRepository.saveAndFlush(d);

        IndicateursImpactDto.Montants m = indicateurs().getMontants();

        assertThat(m.getDemandes()).isEqualByComparingTo("1800");
        assertThat(m.getRetenus()).isEqualByComparingTo("1500");
    }

    @Test
    @Transactional
    void laValeurDeReferenceAlimenteLaColonneAvant() {
        referenceService.enregistrer(ValeurReferenceDto.builder()
                .code(IndicateurReference.DELAI_MOYEN_CORRECTION_JOURS)
                .valeur(new BigDecimal("45"))
                .periodeSource("2021-2023, registre des dépôts")
                .build(), president);
        DemandeCorrection adoptee = demande(StatutDemande.ADOPTEE, "1000", "0");
        transition(adoptee, "ADOPTEE", 9);

        IndicateursImpactDto.Comparaison delai = indicateurs().getAvantApres().stream()
                .filter(c -> c.getCode().equals("DELAI_MOYEN_CORRECTION_JOURS"))
                .findFirst().orElseThrow();

        assertThat(delai.getAvant()).isEqualByComparingTo("45");
        assertThat(delai.getApres()).isEqualByComparingTo("9");
        assertThat(delai.getVariationPct()).isEqualTo(-80.0);
        assertThat(delai.getSourceAvant()).isEqualTo("valeur de référence saisie");
        assertThat(referenceService.lister()).hasSize(IndicateurReference.values().length);
    }

    @Test
    @Transactional
    void lExportPorteLesQuatreTableauxDImpact() {
        demande(StatutDemande.ADOPTEE, "1000", "0");

        ReportingDossier dossier = dossierService.assembler(president, T0.minus(1, ChronoUnit.DAYS),
                Instant.now().plus(1, ChronoUnit.DAYS), null, titulaire.getId(), Set.of(BlocReporting.IMPACT));

        assertThat(dossier.sections()).extracting(ReportingDossier.Section::titre)
                .containsExactly("Impact : demandes traitées et taux de décision",
                        "Impact : délais moyens de traitement par phase",
                        "Impact : montants et utilisation effective des crédits",
                        "Impact : avant et après la digitalisation");
    }

    private IndicateursImpactDto indicateurs() {
        return service.indicateurs(president, T0.minus(1, ChronoUnit.DAYS), Instant.now().plus(1, ChronoUnit.DAYS),
                null, titulaire.getId());
    }

    private DemandeCorrection demande(StatutDemande statut, String interieur, String exterieur) {
        DemandeCorrection modele = demandeRepository.findAll().stream().findFirst().orElseThrow();
        return demandeRepository.saveAndFlush(DemandeCorrection.builder()
                .numero("DC-IMP-" + System.nanoTime())
                .statut(statut)
                .dateDepot(T0)
                .creditInterieur(new BigDecimal(interieur))
                .creditExterieur(new BigDecimal(exterieur))
                .autoriteContractante(modele.getAutoriteContractante())
                .convention(modele.getConvention())
                .entreprise(titulaire)
                .build());
    }

    private void transition(DemandeCorrection d, String statut, int joursApresDepot) {
        historiqueRepository.saveAndFlush(HistoriqueStatut.builder()
                .entityType("DemandeCorrection")
                .entityId(d.getId())
                .statutApres(statut)
                .dateTransition(T0.plus(joursApresDepot, ChronoUnit.DAYS))
                .username("test")
                .origine("UPDATE")
                .build());
    }

    private static IndicateursImpactDto.Processus processus(IndicateursImpactDto dto, String code) {
        return dto.getProcessus().stream().filter(p -> p.getCode().equals(code)).findFirst().orElseThrow();
    }

    private static IndicateursImpactDto.Phase phase(IndicateursImpactDto dto, String processus, String libelle) {
        return dto.getPhases().stream()
                .filter(p -> p.getProcessus().equals(processus) && p.getPhase().equals(libelle))
                .findFirst().orElseThrow();
    }
}
