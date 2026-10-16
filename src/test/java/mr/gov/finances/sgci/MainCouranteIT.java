package mr.gov.finances.sgci;

import mr.gov.finances.sgci.domain.entity.DemandeCorrection;
import mr.gov.finances.sgci.domain.entity.Entreprise;
import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.domain.enums.StatutDemande;
import mr.gov.finances.sgci.repository.DemandeCorrectionRepository;
import mr.gov.finances.sgci.repository.EntrepriseRepository;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.maincourante.MainCouranteService;
import mr.gov.finances.sgci.web.dto.maincourante.LigneMainCouranteDto;
import mr.gov.finances.sgci.web.dto.maincourante.MainCouranteDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * La main courante : ce que l'utilisateur connecté doit faire, et rien d'autre.
 *
 * <p>Les deux tests qui comptent sont {@link #unVisaDejaPoseNeResteDansAucuneMainCourante()} et
 * {@link #uneEntrepriseNeVoitPasLeTravailDesDirections()}. Le premier protège contre la faute qui
 * rendrait l'écran inutilisable : une liste de travail qui garde les dossiers déjà traités cesse
 * d'être consultée au bout d'une semaine. Le second protège le périmètre, qui est ici le même que
 * celui du reporting, délibérément réutilisé.
 *
 * <p>{@link #lesProcessusNonCouvertsSontNommes()} tient à une décision : les utilisations de crédit
 * ne sont pas couvertes, et leur absence est <b>déclarée</b> plutôt que silencieuse. Une liste de
 * travail qui omet un processus sans le dire fait croire qu'il n'y a rien à y faire, ce qui est
 * pire qu'une liste vide — elle inspire confiance.
 *
 * <p>Niveau service : Tomcat ne démarre pas sur ce poste.
 */
@SpringBootTest
@ActiveProfiles("test")
class MainCouranteIT {

    @Autowired
    private MainCouranteService service;

    @Autowired
    private DemandeCorrectionRepository demandeRepository;

    @Autowired
    private EntrepriseRepository entrepriseRepository;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    /** Les rôles dont le périmètre suppose un rattachement à une entité. */
    private static final java.util.Set<Role> ROLES_EXIGEANT_UN_RATTACHEMENT = java.util.Set.of(
            Role.AUTORITE_CONTRACTANTE, Role.AUTORITE_UPM, Role.AUTORITE_UEP,
            Role.ENTREPRISE, Role.SOUS_TRAITANT);

    private Entreprise titulaire;

    @BeforeEach
    void setUp() {
        titulaire = entrepriseRepository.save(Entreprise.builder()
                .raisonSociale("Titulaire main courante " + System.nanoTime())
                .nif("NIF-MC-" + System.nanoTime())
                .build());
    }

    // ── ce qui doit apparaître ─────────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void uneDemandeEnInstructionAttendLeVisaDeLaDirection() {
        DemandeCorrection demande = demandeNeuve(StatutDemande.EN_EVALUATION,
                Instant.now().minus(12, ChronoUnit.DAYS));

        MainCouranteDto vue = pour("dgd", Role.DGD);

        assertThat(vue.getLignes())
                .filteredOn(l -> demande.getId().equals(l.getId())
                        && "CORRECTION".equals(l.getProcessus()))
                .singleElement()
                .satisfies(l -> {
                    // L'action dit le geste attendu, pas seulement le statut : c'est la différence
                    // entre une liste de dossiers et une liste de travail.
                    assertThat(l.getAction()).isEqualTo("Poser le visa DGD");
                    assertThat(l.getJoursDAttente()).isBetween(11L, 13L);
                    assertThat(l.getEntrepriseRaisonSociale()).isEqualTo(titulaire.getRaisonSociale());
                });
        assertThat(vue.getParProcessus()).containsKey("CORRECTION");
        assertThat(vue.getTotal()).isPositive();
    }

    @Test
    @Transactional
    void lesLignesSontTrieesParAncienneteDecroissante() {
        demandeNeuve(StatutDemande.EN_EVALUATION, Instant.now().minus(3, ChronoUnit.DAYS));
        demandeNeuve(StatutDemande.EN_EVALUATION, Instant.now().minus(90, ChronoUnit.DAYS));

        MainCouranteDto vue = pour("dgd", Role.DGD);

        assertThat(vue.getLignes()).isSortedAccordingTo(
                (a, b) -> Long.compare(b.getJoursDAttente(), a.getJoursDAttente()));
    }

    // ── ce qui ne doit pas apparaître ──────────────────────────────────────────────────────────

    @Test
    @Transactional
    void unVisaDejaPoseNeResteDansAucuneMainCourante() {
        // La faute qui rendrait l'écran inutilisable : une liste de travail qui garde les dossiers
        // déjà traités cesse d'être consultée au bout d'une semaine.
        DemandeCorrection demande = demandeNeuve(StatutDemande.EN_EVALUATION,
                Instant.now().minus(5, ChronoUnit.DAYS));
        demande.setValidationDgd(true);
        demandeRepository.saveAndFlush(demande);

        assertThat(pour("dgd", Role.DGD).getLignes())
                .extracting(LigneMainCouranteDto::getId)
                .doesNotContain(demande.getId());
        // Mais elle attend toujours les autres directions.
        assertThat(pour("dgi", Role.DGI).getLignes())
                .extracting(LigneMainCouranteDto::getId)
                .contains(demande.getId());
    }

    @Test
    @Transactional
    void unDossierTermineNapparaitPas() {
        DemandeCorrection adoptee = demandeNeuve(StatutDemande.ADOPTEE,
                Instant.now().minus(200, ChronoUnit.DAYS));

        assertThat(pour("dgd", Role.DGD).getLignes())
                .extracting(LigneMainCouranteDto::getId)
                .doesNotContain(adoptee.getId());
    }

    @Test
    @Transactional
    void uneEntrepriseNeVoitPasLeTravailDesDirections() {
        DemandeCorrection demande = demandeNeuve(StatutDemande.EN_EVALUATION,
                Instant.now().minus(7, ChronoUnit.DAYS));

        MainCouranteDto vueEntreprise = pour("entreprise", Role.ENTREPRISE);

        assertThat(vueEntreprise.getLignes())
                .as("un visa de direction n'est pas une tâche de déposant")
                .noneSatisfy(l -> assertThat(l.getProcessus()).isEqualTo("CORRECTION"));
        assertThat(vueEntreprise.getLignes())
                .extracting(LigneMainCouranteDto::getId)
                .doesNotContain(demande.getId());
    }

    // ── l'honnêteté de la réponse ──────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void laCouvertureDesProcessusDependDuRole() {
        // Les utilisations sont désormais couvertes : la table des étapes est dérivée du circuit
        // et de l'arbitre des rôles, et arrimée à eux par EtapesUtilisationTest.
        MainCouranteDto dgd = pour("dgd", Role.DGD);
        assertThat(dgd.getProcessusCouverts())
                .contains("CORRECTION", "MISE_EN_PLACE", "TRANSFERT", "UTILISATION");
        assertThat(dgd.getProcessusNonCouverts())
                .as("plus rien n'est laissé de côté pour une direction du circuit")
                .isEmpty();

        // L'administration n'est une tâche que pour ceux qui la traitent.
        assertThat(pour("admin", Role.ADMIN_SI).getProcessusCouverts()).contains("ADMINISTRATION");
        assertThat(dgd.getProcessusCouverts()).doesNotContain("ADMINISTRATION");

        // Le déposant voit ses brouillons et ses demandes de pièce, pas le travail des directions.
        assertThat(pour("entreprise", Role.ENTREPRISE).getProcessusCouverts())
                .contains("REJET_TEMPORAIRE", "BROUILLON", "UTILISATION")
                .doesNotContain("CORRECTION");
    }

    @Test
    @Transactional
    void lAutoriteContractanteNEstPlusLesMainsVides() {
        // Elle n'avait aucune ligne possible : ni visa, ni file de clôture, ni utilisation. Elle
        // dépose pourtant des demandes de correction, et reçoit des demandes de pièce.
        MainCouranteDto vue = pour("ac", Role.AUTORITE_CONTRACTANTE);

        assertThat(vue.getProcessusCouverts()).contains("REJET_TEMPORAIRE", "BROUILLON");
    }

    @Test
    @Transactional
    void laDefinitionDeLAttenteEstPubliee() {
        // Même exigence que pour le reporting : une durée mal nommée se transforme en reproche
        // adressé à la mauvaise direction.
        assertThat(pour("dgd", Role.DGD).getDefinitionAttente())
                .contains("dernière étape connue")
                .contains("Ce n'est pas l'ancienneté dans le statut actuel");
    }

    @Test
    @Transactional
    void lesProcessusCouvertsDependentDuRole() {
        // Un transfert se vise par DGD, DGI, DGTCP puis le Président ; la DGB n'est pas du circuit.
        assertThat(pour("dgd", Role.DGD).getProcessusCouverts()).contains("TRANSFERT");
        assertThat(pour("dgb", Role.DGB).getProcessusCouverts()).doesNotContain("TRANSFERT");
        assertThat(pour("entreprise", Role.ENTREPRISE).getProcessusCouverts())
                .contains("REJET_TEMPORAIRE");
    }

    // ── fixtures ───────────────────────────────────────────────────────────────────────────────

    private MainCouranteDto pour(String username, Role role) {
        Utilisateur u = utilisateurRepository.findByUsername(username).orElseThrow();
        return service.pourMoi(new AuthenticatedUser(u.getId(), u.getUsername(), role));
    }

    /** Une demande neuve rattachée à l'entreprise du test, pour isoler ce qu'on mesure. */
    private DemandeCorrection demandeNeuve(StatutDemande statut, Instant dateDepot) {
        DemandeCorrection modele = demandeRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Aucune demande amorcée"));
        return demandeRepository.saveAndFlush(DemandeCorrection.builder()
                .numero("DC-MC-" + System.nanoTime())
                .statut(statut)
                .dateDepot(dateDepot)
                .autoriteContractante(modele.getAutoriteContractante())
                .entreprise(titulaire)
                .convention(modele.getConvention())
                .build());
    }

    @Test
    @Transactional
    void laMainCouranteNEchouePourAucunRole() {
        // Elle remplace le tableau de bord : elle est la première page que chacun ouvre. Une
        // exception sur un seul rôle rend l'application inutilisable pour lui, et le message
        // « Impossible de charger » ne dit pas lequel. Ce test les parcourt tous.
        for (Role role : Role.values()) {
            Utilisateur u = utilisateurRepository.findAll().stream()
                    .filter(x -> x.getRole() == role)
                    .findFirst()
                    .orElse(null);
            if (u == null) {
                continue;
            }
            AuthenticatedUser acteur = new AuthenticatedUser(u.getId(), u.getUsername(), role);
            assertThatCode(() -> service.pourMoi(acteur))
                    .as("main courante du rôle %s (compte %s)", role, u.getUsername())
                    .doesNotThrowAnyException();
        }
    }

    @Test
    @Transactional
    void laMainCouranteNEchouePourAucunRoleMemeSansRattachement() {
        // Un compte dont le rôle attend un rattachement mais n'en a pas : le cadrage lève, et
        // c'est précisément le cas qu'un écran d'accueil ne doit pas transformer en page blanche.
        for (Role role : Role.values()) {
            Utilisateur orphelin = utilisateurRepository.save(Utilisateur.builder()
                    .username("orphelin-" + role.name().toLowerCase() + "-" + System.nanoTime())
                    .passwordHash("x")
                    .role(role)
                    .nomComplet("Compte sans rattachement")
                    .email("orphelin-" + System.nanoTime() + "@sharklasers.com")
                    .actif(true)
                    .build());
            AuthenticatedUser acteur =
                    new AuthenticatedUser(orphelin.getId(), orphelin.getUsername(), role);
            assertThatCode(() -> service.pourMoi(acteur))
                    .as("main courante d'un %s sans rattachement", role)
                    .doesNotThrowAnyException();

            MainCouranteDto vue = service.pourMoi(acteur);
            if (ROLES_EXIGEANT_UN_RATTACHEMENT.contains(role)) {
                // Liste vide, forcément — mais la cause doit être nommée. Sans elle, l'écran dirait
                // « rien n'attend votre intervention » à quelqu'un dont le compte ne peut rien
                // recevoir, et personne ne chercherait plus loin.
                assertThat(vue.getLignes()).isEmpty();
                assertThat(vue.getProcessusNonCouverts())
                        .as("la cause doit être nommée pour %s", role)
                        .anySatisfy(m -> assertThat(m).contains("n'est rattaché à aucune entité"));
            }
        }
    }
}
