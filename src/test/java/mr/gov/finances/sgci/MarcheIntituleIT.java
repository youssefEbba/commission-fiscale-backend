package mr.gov.finances.sgci;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import mr.gov.finances.sgci.domain.entity.Marche;
import mr.gov.finances.sgci.repository.MarcheRepository;
import mr.gov.finances.sgci.service.MarcheService;
import mr.gov.finances.sgci.web.dto.CreateMarcheRequest;
import mr.gov.finances.sgci.web.dto.MarcheDto;
import mr.gov.finances.sgci.web.dto.UpdateMarcheRequest;
import mr.gov.finances.sgci.web.exception.ApiErrorCode;
import mr.gov.finances.sgci.web.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * L'intitulé d'une attribution est obligatoire à la création, et seulement là.
 *
 * <p>23 des 27 marchés en base n'en ont pas : l'exiger aussi à la modification les rendrait non
 * modifiables tant que personne ne leur en invente un, ce qui transformerait une règle de saisie en
 * blocage rétroactif. La règle de création est donc portée par {@code @NotBlank} sur le DTO — vérifiée
 * ici par le validateur, puisqu'elle s'applique au niveau du contrôleur — et la modification se
 * contente de refuser l'effacement d'un intitulé existant.
 */
@SpringBootTest
@ActiveProfiles("test")
class MarcheIntituleIT {

    @Autowired
    private Validator validator;

    @Autowired
    private MarcheService service;

    @Autowired
    private MarcheRepository marcheRepository;

    // ── création : obligatoire ─────────────────────────────────────────────────────────────────

    @Test
    void creationSansIntitule_refusee() {
        Set<ConstraintViolation<CreateMarcheRequest>> violations = validator.validate(creation(null));

        assertThat(violations).extracting(v -> v.getPropertyPath().toString()).contains("intitule");
    }

    @Test
    void creationAvecIntituleVide_refusee() {
        // Une chaîne d'espaces n'est pas un intitulé : c'est pour cela que la contrainte est
        // @NotBlank et non @NotNull.
        Set<ConstraintViolation<CreateMarcheRequest>> violations = validator.validate(creation("   "));

        assertThat(violations).extracting(v -> v.getPropertyPath().toString()).contains("intitule");
    }

    @Test
    void creationAvecIntitule_acceptee() {
        Set<ConstraintViolation<CreateMarcheRequest>> violations =
                validator.validate(creation("Construction d'un pont"));

        assertThat(violations).extracting(v -> v.getPropertyPath().toString())
                .doesNotContain("intitule");
    }

    // ── modification : ni exigence, ni effacement ──────────────────────────────────────────────

    @Test
    @Transactional
    void effacerUnIntituleExistant_refuse() {
        Marche marche = unMarche();
        marche.setIntitule("Intitulé à conserver");
        marcheRepository.save(marche);

        assertThatThrownBy(() -> service.update(marche.getId(), modification(marche, "")))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException api = (ApiException) e;
                    assertThat(api.getCode()).isEqualTo(ApiErrorCode.VALIDATION_FAILED);
                    assertThat(api.getMessage()).contains("ne peut pas être effacé");
                });
    }

    @Test
    @Transactional
    void marcheHistoriqueSansIntitule_resteModifiable() {
        // Le cas des 23 marchés existants : on doit pouvoir corriger leur montant ou leur statut
        // sans être forcé d'inventer un intitulé.
        Marche marche = unMarche();
        marche.setIntitule(null);
        marcheRepository.save(marche);

        assertThatCode(() -> service.update(marche.getId(), modification(marche, null)))
                .doesNotThrowAnyException();
    }

    @Test
    @Transactional
    void renseignerLintituleDunMarcheHistorique_accepte() {
        Marche marche = unMarche();
        marche.setIntitule(null);
        marcheRepository.save(marche);

        MarcheDto dto = service.update(marche.getId(), modification(marche, "Intitulé rattrapé"));

        assertThat(dto.getIntitule()).isEqualTo("Intitulé rattrapé");
    }

    // ── fixtures ───────────────────────────────────────────────────────────────────────────────

    private Marche unMarche() {
        return marcheRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Aucun marché amorcé"));
    }

    private CreateMarcheRequest creation(String intitule) {
        return CreateMarcheRequest.builder()
                .conventionId(1L)
                .numeroMarche("M-" + System.nanoTime())
                .intitule(intitule)
                .montantContratHt(BigDecimal.valueOf(1_000_000))
                .statut(mr.gov.finances.sgci.domain.enums.StatutMarche.EN_COURS)
                .build();
    }

    private UpdateMarcheRequest modification(Marche marche, String intitule) {
        return UpdateMarcheRequest.builder()
                .numeroMarche(marche.getNumeroMarche())
                .intitule(intitule)
                .dateSignature(marche.getDateSignature())
                .montantContratHt(marche.getMontantContratHt())
                .statut(marche.getStatut())
                .build();
    }
}
