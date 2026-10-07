package mr.gov.finances.sgci;

import mr.gov.finances.sgci.domain.document.DocumentDepotPolicy;
import mr.gov.finances.sgci.domain.entity.DocumentRequirement;
import mr.gov.finances.sgci.domain.enums.ProcessusDocument;
import mr.gov.finances.sgci.repository.DocumentRequirementRepository;
import mr.gov.finances.sgci.service.DocumentRequirementService;
import mr.gov.finances.sgci.web.dto.DocumentRequirementDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le formulaire de dépôt ne réclame pas au demandeur ce que la Commission produira elle-même.
 *
 * <p>Le test le plus important est {@link #toutCodeParametreEstClasse()} : une politique par liste
 * souffre de l'oubli, et c'est lui qui transforme « code oublié ⇒ réclamé à tort » en « code oublié ⇒
 * test rouge ». Vient ensuite {@link #leParametrageVoitTousLesCodes()} : filtrer l'écran
 * d'administration ferait croire à un type disparu, et quelqu'un le recréerait en double.
 */
@SpringBootTest
@ActiveProfiles("test")
class DocumentCorrectionDepotIT {

    private static final ProcessusDocument CORRECTION = ProcessusDocument.CORRECTION_OFFRE_FISCALE;

    @Autowired
    private DocumentRequirementService service;

    @Autowired
    private DocumentRequirementRepository repository;

    // ── le garde-fou contre l'oubli ────────────────────────────────────────────────────────────

    @Test
    void toutCodeParametreEstClasse() {
        List<String> codes = repository.findByProcessusOrderByOrdreAffichageAsc(CORRECTION).stream()
                .map(DocumentRequirement::getCodeDocument)
                .distinct()
                .collect(Collectors.toList());

        assertThat(codes).isNotEmpty();
        for (String code : codes) {
            boolean classe = DocumentDepotPolicy.CODES_DEPOSES_PAR_LE_DEMANDEUR.contains(code)
                    || DocumentDepotPolicy.CODES_PRODUITS_PAR_LA_COMMISSION.contains(code);
            assertThat(classe)
                    .as("Le code « %s » est paramétré sur la correction mais n'est classé ni déposé "
                            + "ni produit : il serait réclamé au demandeur par défaut", code)
                    .isTrue();
        }
    }

    @Test
    void lesDeuxFamillesSontDisjointes() {
        Set<String> intersection = DocumentDepotPolicy.CODES_DEPOSES_PAR_LE_DEMANDEUR.stream()
                .filter(DocumentDepotPolicy.CODES_PRODUITS_PAR_LA_COMMISSION::contains)
                .collect(Collectors.toSet());

        assertThat(intersection).isEmpty();
    }

    // ── la règle ───────────────────────────────────────────────────────────────────────────────

    @Test
    void lesPiecesDeLaCommissionNeSontPasDeposables() {
        for (String code : List.of("OFFRE_CORRIGEE", "LETTRE_ADOPTION", "CREDIT_EXTERIEUR",
                "CREDIT_INTERIEUR", "FEUILLE_EVALUATION_SIGNEE", "OFFRE_FISCALE_CORRIGEE", "RECU_DEPOT")) {
            assertThat(DocumentDepotPolicy.deposableParLeDemandeur(code)).as(code).isFalse();
        }
    }

    @Test
    void lesPiecesDuDemandeurLeRestent() {
        for (String code : List.of("LETTRE_SAISINE", "OFFRE_FISCALE", "DAO_DQE", "ATTESTATION_FISCALE")) {
            assertThat(DocumentDepotPolicy.deposableParLeDemandeur(code)).as(code).isTrue();
        }
    }

    @Test
    void unCodeInconnuResteDeposable() {
        // Le paramétrage reste ouvert à l'ADMIN_SI : un type ajouté en exploitation ne doit pas
        // disparaître du formulaire sans que personne ne l'ait décidé.
        assertThat(DocumentDepotPolicy.deposableParLeDemandeur("TYPE_AJOUTE_EN_EXPLOITATION")).isTrue();
    }

    // ── les deux lectures ──────────────────────────────────────────────────────────────────────

    @Test
    void leFormulaireDeDepotNeProposeQueLesPiecesDuDemandeur() {
        List<String> codes = service.findByProcessus(CORRECTION, true).stream()
                .map(DocumentRequirementDto::getCodeDocument)
                .collect(Collectors.toList());

        assertThat(codes).contains("LETTRE_SAISINE", "OFFRE_FISCALE");
        assertThat(codes).doesNotContain("OFFRE_CORRIGEE", "LETTRE_ADOPTION", "CREDIT_EXTERIEUR",
                "CREDIT_INTERIEUR", "RECU_DEPOT");
    }

    @Test
    void leParametrageVoitTousLesCodes() {
        List<String> codes = service.findByProcessus(CORRECTION).stream()
                .map(DocumentRequirementDto::getCodeDocument)
                .collect(Collectors.toList());

        assertThat(codes).contains("LETTRE_SAISINE", "OFFRE_CORRIGEE", "LETTRE_ADOPTION");
    }

    @Test
    void laFamilleEstExposeeAuParametrage() {
        List<DocumentRequirementDto> tous = service.findByProcessus(CORRECTION);

        assertThat(tous).filteredOn(d -> "OFFRE_CORRIGEE".equals(d.getCodeDocument()))
                .allMatch(d -> Boolean.FALSE.equals(d.getDeposableParLeDemandeur()));
        assertThat(tous).filteredOn(d -> "LETTRE_SAISINE".equals(d.getCodeDocument()))
                .allMatch(d -> Boolean.TRUE.equals(d.getDeposableParLeDemandeur()));
    }

    @Test
    void leRecuDeDepotEstParametreEtFacultatif() {
        assertThat(repository.findByProcessusOrderByOrdreAffichageAsc(CORRECTION))
                .filteredOn(r -> "RECU_DEPOT".equals(r.getCodeDocument()))
                .isNotEmpty()
                .allMatch(r -> Boolean.FALSE.equals(r.getObligatoire()));
    }
}
