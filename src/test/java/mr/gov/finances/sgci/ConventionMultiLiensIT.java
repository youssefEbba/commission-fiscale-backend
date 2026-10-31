package mr.gov.finances.sgci;

import mr.gov.finances.sgci.config.ConventionMultiLiensMigration;
import mr.gov.finances.sgci.domain.entity.AutoriteContractante;
import mr.gov.finances.sgci.domain.entity.Bailleur;
import mr.gov.finances.sgci.domain.entity.Convention;
import mr.gov.finances.sgci.domain.entity.DemandeCorrection;
import mr.gov.finances.sgci.domain.entity.Marche;
import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.domain.enums.StatutConvention;
import mr.gov.finances.sgci.domain.enums.StatutDemande;
import mr.gov.finances.sgci.domain.enums.StatutMarche;
import mr.gov.finances.sgci.repository.AutoriteContractanteRepository;
import mr.gov.finances.sgci.repository.BailleurRepository;
import mr.gov.finances.sgci.repository.ConventionRepository;
import mr.gov.finances.sgci.repository.DemandeCorrectionRepository;
import mr.gov.finances.sgci.repository.EntrepriseRepository;
import mr.gov.finances.sgci.repository.MarcheRepository;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.ConventionService;
import mr.gov.finances.sgci.service.MarcheService;
import mr.gov.finances.sgci.web.dto.ConventionDto;
import mr.gov.finances.sgci.web.dto.CreateMarcheRequest;
import mr.gov.finances.sgci.web.dto.EntiteLieeDto;
import mr.gov.finances.sgci.web.dto.MarcheDto;
import mr.gov.finances.sgci.web.dto.UpdateConventionRequest;
import mr.gov.finances.sgci.web.dto.UpdateMarcheRequest;
import mr.gov.finances.sgci.web.exception.ApiErrorCode;
import mr.gov.finances.sgci.web.exception.ApiException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Une convention relie plusieurs autorités et plusieurs bailleurs ; un marché, plusieurs conventions.
 *
 * <p>Décision de l'équipe projet, octobre 2026. Le Président et l'administrateur gèrent ces liens et
 * modifient conventions et marchés ; une autorité ne peut être déliée d'une convention que si elle
 * n'y a encore ni marché ni demande de correction.
 *
 * <p>Niveau service : Tomcat ne démarre pas sur ce poste.
 */
@SpringBootTest
@ActiveProfiles("test")
class ConventionMultiLiensIT {

    @Autowired private ConventionService conventionService;
    @Autowired private MarcheService marcheService;
    @Autowired private ConventionMultiLiensMigration migration;
    @Autowired private ConventionRepository conventionRepository;
    @Autowired private MarcheRepository marcheRepository;
    @Autowired private DemandeCorrectionRepository demandeRepository;
    @Autowired private AutoriteContractanteRepository autoriteRepository;
    @Autowired private BailleurRepository bailleurRepository;
    @Autowired private EntrepriseRepository entrepriseRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private EntityManager entityManager;

    private AuthenticatedUser president;
    private AuthenticatedUser admin;
    private AuthenticatedUser acTitulaire;
    private AutoriteContractante principale;
    private AutoriteContractante seconde;
    private AuthenticatedUser acSeconde;

    @BeforeEach
    void setUp() {
        president = compte("president");
        admin = compte("admin");
        acTitulaire = compte("ac");
        principale = autoriteRepository.findByCode("AC_DEFAULT").orElseThrow();
    }

    // ── relier et délier une autorité ──────────────────────────────────────────────────────────

    @Test
    @Transactional
    void lePresidentRelieUneSecondeAutoriteQuiVoitAlorsLaConvention() {
        preparerSecondeAutorite();
        Convention c = convention(StatutConvention.VALIDE);
        assertThat(conventionService.canAccessConvention(c.getId(), acSeconde)).as("avant").isFalse();

        ConventionDto dto = conventionService.lierAutorite(c.getId(), seconde.getId(), president);

        assertThat(dto.getAutorites()).extracting(EntiteLieeDto::getId)
                .containsExactlyInAnyOrder(principale.getId(), seconde.getId());
        assertThat(conventionService.canAccessConvention(c.getId(), acSeconde)).as("après").isTrue();
        assertThat(conventionService.findAll(acSeconde, null)).extracting(ConventionDto::getId).contains(c.getId());
    }

    @Test
    @Transactional
    void uneAutoriteNeGerePasLesLiens() {
        preparerSecondeAutorite();
        Convention c = convention(StatutConvention.EN_ATTENTE);

        assertThatThrownBy(() -> conventionService.lierAutorite(c.getId(), seconde.getId(), acTitulaire))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(ApiErrorCode.ROLE_FORBIDDEN));
    }

    @Test
    @Transactional
    void delierEstPossibleTantQueLAutoriteNAAucunDossier() {
        preparerSecondeAutorite();
        Convention c = convention(StatutConvention.VALIDE);
        conventionService.lierAutorite(c.getId(), seconde.getId(), admin);

        ConventionDto dto = conventionService.delierAutorite(c.getId(), seconde.getId(), admin);

        assertThat(dto.getAutorites()).extracting(EntiteLieeDto::getId).containsExactly(principale.getId());
    }

    @Test
    @Transactional
    void delierEstRefuseQuandLAutoriteADejaUnMarche() {
        preparerSecondeAutorite();
        Convention c = convention(StatutConvention.VALIDE);
        conventionService.lierAutorite(c.getId(), seconde.getId(), president);
        marcheRepository.saveAndFlush(Marche.builder()
                .numeroMarche("MP-LIEN-" + System.nanoTime())
                .montantContratHt(BigDecimal.TEN)
                .statut(StatutMarche.EN_COURS)
                .convention(c)
                .autoriteContractante(seconde)
                .build());

        assertThatThrownBy(() -> conventionService.delierAutorite(c.getId(), seconde.getId(), president))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("marché");
    }

    @Test
    @Transactional
    void delierEstRefuseQuandLAutoriteADejaUneDemande() {
        preparerSecondeAutorite();
        Convention c = convention(StatutConvention.VALIDE);
        conventionService.lierAutorite(c.getId(), seconde.getId(), president);
        demandeRepository.saveAndFlush(DemandeCorrection.builder()
                .numero("DC-LIEN-" + System.nanoTime())
                .dateDepot(Instant.now())
                .statut(StatutDemande.RECUE)
                .autoriteContractante(seconde)
                .entreprise(entrepriseRepository.findByNif("NIF_DEFAULT").orElseThrow())
                .convention(c)
                .build());

        assertThatThrownBy(() -> conventionService.delierAutorite(c.getId(), seconde.getId(), president))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("demande");
    }

    @Test
    @Transactional
    void laDerniereAutoriteNeSeDeliePas() {
        Convention c = convention(StatutConvention.VALIDE);

        assertThatThrownBy(() -> conventionService.delierAutorite(c.getId(), principale.getId(), president))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("au moins une autorité");
    }

    // ── modifier une convention ────────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void lePresidentModifieUneConventionValideeEtSesBailleurs() {
        Convention c = convention(StatutConvention.VALIDE);
        Bailleur b1 = bailleur();
        Bailleur b2 = bailleur();

        ConventionDto dto = conventionService.update(c.getId(), UpdateConventionRequest.builder()
                .reference(c.getReference())
                .intitule("Intitulé corrigé par le Président")
                .bailleurIds(List.of(b1.getId(), b2.getId()))
                .build(), president);

        assertThat(dto.getIntitule()).isEqualTo("Intitulé corrigé par le Président");
        assertThat(dto.getBailleurs()).extracting(EntiteLieeDto::getId).containsExactly(b1.getId(), b2.getId());
        assertThat(dto.getBailleurId()).as("le premier devient le principal").isEqualTo(b1.getId());
    }

    @Test
    @Transactional
    void lAutoriteNeModifiePlusUneConventionValidee() {
        Convention c = convention(StatutConvention.VALIDE);

        assertThatThrownBy(() -> conventionService.update(c.getId(), UpdateConventionRequest.builder()
                .reference(c.getReference()).intitule("Tentative").build(), acTitulaire))
                .isInstanceOf(ApiException.class);
    }

    // ── un marché, plusieurs conventions ───────────────────────────────────────────────────────

    @Test
    @Transactional
    void unMarchePeutRelierPlusieursConventions() {
        Convention c1 = convention(StatutConvention.VALIDE);
        Convention c2 = convention(StatutConvention.VALIDE);

        MarcheDto m = marcheService.create(CreateMarcheRequest.builder()
                .conventionId(c1.getId())
                .conventionIds(List.of(c1.getId(), c2.getId()))
                .numeroMarche("MP-MULTI-" + System.nanoTime())
                .intitule("Marché à deux conventions")
                .montantContratHt(BigDecimal.valueOf(1000))
                .statut(StatutMarche.EN_COURS)
                .build(), acTitulaire);

        assertThat(m.getConventionIds()).containsExactly(c1.getId(), c2.getId());
        assertThat(m.getAutoriteContractanteId()).isEqualTo(principale.getId());
    }

    @Test
    @Transactional
    void uneAutoriteNePlacePasSonMarcheSurUneConventionQuiNeLaConcernePas() {
        preparerSecondeAutorite();
        Convention c1 = convention(StatutConvention.VALIDE);
        Convention etrangere = conventionDe(seconde);

        assertThatThrownBy(() -> marcheService.create(CreateMarcheRequest.builder()
                .conventionId(c1.getId())
                .conventionIds(List.of(c1.getId(), etrangere.getId()))
                .numeroMarche("MP-INTRUS-" + System.nanoTime())
                .intitule("Marché")
                .montantContratHt(BigDecimal.valueOf(1000))
                .statut(StatutMarche.EN_COURS)
                .build(), acTitulaire))
                .isInstanceOf(ApiException.class);
    }

    @Test
    @Transactional
    void lePresidentModifieLesConventionsDUnMarche() {
        Convention c1 = convention(StatutConvention.VALIDE);
        Convention c2 = convention(StatutConvention.VALIDE);
        Marche m = marcheRepository.saveAndFlush(Marche.builder()
                .numeroMarche("MP-MAJ-" + System.nanoTime())
                .intitule("Marché")
                .montantContratHt(BigDecimal.TEN)
                .statut(StatutMarche.EN_COURS)
                .convention(c1)
                .build());

        MarcheDto dto = marcheService.update(m.getId(), UpdateMarcheRequest.builder()
                .numeroMarche(m.getNumeroMarche())
                .intitule(m.getIntitule())
                .montantContratHt(m.getMontantContratHt())
                .statut(m.getStatut())
                .conventionIds(List.of(c2.getId(), c1.getId()))
                .build(), president);

        assertThat(dto.getConventionIds()).containsExactly(c2.getId(), c1.getId());
        assertThat(dto.getConventionId()).as("la première devient la principale").isEqualTo(c2.getId());
    }

    // ── la reprise de l'existant ───────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void laMigrationRelieLExistantASesColonnesPrincipales() {
        Convention c = convention(StatutConvention.VALIDE);
        Marche m = marcheRepository.saveAndFlush(Marche.builder()
                .numeroMarche("MP-MIG-" + System.nanoTime())
                .montantContratHt(BigDecimal.TEN)
                .statut(StatutMarche.EN_COURS)
                .convention(c)
                .build());
        // Simule une base antérieure au lot : aucune ligne de liaison, aucune autorité de marché.
        jdbcTemplate.update("DELETE FROM convention_autorite WHERE convention_id = ?", c.getId());
        jdbcTemplate.update("DELETE FROM convention_bailleur WHERE convention_id = ?", c.getId());
        jdbcTemplate.update("DELETE FROM marche_convention WHERE marche_id = ?", m.getId());
        jdbcTemplate.update("UPDATE marche SET autorite_contractante_id = NULL WHERE id = ?", m.getId());

        migration.run(null);
        entityManager.clear();

        Convention apres = conventionRepository.findById(c.getId()).orElseThrow();
        assertThat(apres.getAutorites()).extracting(AutoriteContractante::getId).containsExactly(principale.getId());
        assertThat(apres.getBailleurs()).extracting(Bailleur::getId).containsExactly(c.getBailleur().getId());
        Marche marcheApres = marcheRepository.findById(m.getId()).orElseThrow();
        assertThat(marcheApres.getConventions()).extracting(Convention::getId).containsExactly(c.getId());
        assertThat(marcheApres.getAutoriteContractante().getId()).isEqualTo(principale.getId());
    }

    // ── fixtures ───────────────────────────────────────────────────────────────────────────────

    private AuthenticatedUser compte(String username) {
        Utilisateur u = utilisateurRepository.findByUsername(username).orElseThrow();
        return new AuthenticatedUser(u.getId(), u.getUsername(), u.getRole());
    }

    private void preparerSecondeAutorite() {
        seconde = autoriteRepository.save(AutoriteContractante.builder()
                .nom("Seconde autorité " + System.nanoTime())
                .code("AC2-" + System.nanoTime())
                .build());
        Utilisateur u = utilisateurRepository.save(Utilisateur.builder()
                .username("ac2-" + System.nanoTime())
                .passwordHash("x")
                .role(Role.AUTORITE_CONTRACTANTE)
                .autoriteContractante(seconde)
                .actif(true)
                .build());
        acSeconde = new AuthenticatedUser(u.getId(), u.getUsername(), u.getRole());
    }

    private Convention convention(StatutConvention statut) {
        return conventionRepository.saveAndFlush(Convention.builder()
                .reference("CONV-LIEN-" + System.nanoTime())
                .intitule("Convention multi-liens")
                .statut(statut)
                .autoriteContractante(principale)
                .bailleur(bailleur())
                .dateSignature(LocalDate.now())
                .build());
    }

    private Convention conventionDe(AutoriteContractante ac) {
        return conventionRepository.saveAndFlush(Convention.builder()
                .reference("CONV-AUTRE-" + System.nanoTime())
                .intitule("Convention d'une autre autorité")
                .statut(StatutConvention.VALIDE)
                .autoriteContractante(ac)
                .build());
    }

    private Bailleur bailleur() {
        return bailleurRepository.saveAndFlush(Bailleur.builder().nom("Bailleur " + System.nanoTime()).build());
    }
}
