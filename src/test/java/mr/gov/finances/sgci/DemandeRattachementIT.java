package mr.gov.finances.sgci;

import mr.gov.finances.sgci.domain.entity.AutoriteContractante;
import mr.gov.finances.sgci.domain.entity.Entreprise;
import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.domain.enums.StatutDemandeRattachement;
import mr.gov.finances.sgci.domain.enums.TypeEntiteRattachement;
import mr.gov.finances.sgci.repository.AutoriteContractanteRepository;
import mr.gov.finances.sgci.repository.EntrepriseRepository;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.DemandeRattachementService;
import mr.gov.finances.sgci.web.dto.SuiviRattachementDto;
import mr.gov.finances.sgci.web.dto.CreateDemandeRattachementRequest;
import mr.gov.finances.sgci.web.dto.DemandeRattachementDto;
import mr.gov.finances.sgci.web.exception.ApiErrorCode;
import mr.gov.finances.sgci.web.exception.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Une personne demande à être rattachée à une entité ; le Président tranche.
 *
 * <p>Le test le plus important est {@link #leDepotNeCreeNiCompteNiEntite()} : tout l'objet du lot est
 * que le dépôt n'engage rien. L'ancienne inscription créait le compte <em>et</em> l'entité d'un même
 * geste, sans que personne n'ait rien validé.
 *
 * <p>Vient ensuite {@link #uneQualiteNonDemandableEstRefusee()} : l'ancienne route acceptait
 * l'énumération des rôles au complet, {@code PRESIDENT} compris.
 *
 * <p>Niveau service : Tomcat ne démarre pas sur ce poste.
 */
@SpringBootTest
@ActiveProfiles("test")
class DemandeRattachementIT {

    @Autowired
    private DemandeRattachementService service;

    @Autowired
    private EntrepriseRepository entrepriseRepository;

    @Autowired
    private AutoriteContractanteRepository autoriteRepository;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    private AuthenticatedUser president;

    @BeforeEach
    void setUp() {
        Utilisateur p = utilisateurRepository.findByUsername("president").orElseThrow();
        president = new AuthenticatedUser(p.getId(), p.getUsername(), p.getRole());
    }

    // ── le dépôt n'engage rien ─────────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void leDepotNeCreeNiCompteNiEntite() {
        Entreprise cible = uneEntreprise();
        long entreprisesAvant = entrepriseRepository.count();

        DemandeRattachementDto dto = service.deposer(surEntiteExistante(cible.getId()), mandat());

        assertThat(dto.getStatut()).isEqualTo(StatutDemandeRattachement.EN_ATTENTE);
        assertThat(dto.getReference()).isNotBlank();
        assertThat(dto.getUtilisateurCreeId()).isNull();
        assertThat(utilisateurRepository.existsByUsername(dto.getUsername())).isFalse();
        assertThat(entrepriseRepository.count()).isEqualTo(entreprisesAvant);
    }

    @Test
    @Transactional
    void leHachageDuMotDePasseNestJamaisExpose() {
        Entreprise cible = uneEntreprise();

        DemandeRattachementDto dto = service.deposer(surEntiteExistante(cible.getId()), mandat());

        // Le DTO ne porte aucun champ de mot de passe : la vérification est structurelle, et c'est
        // elle qui empêchera quelqu'un d'en ajouter un par commodité.
        assertThat(dto.toString()).doesNotContain("passwordHash");
    }

    // ── ce que le dépôt refuse ─────────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void uneQualiteNonDemandableEstRefusee() {
        for (Role interdit : java.util.List.of(Role.PRESIDENT, Role.ADMIN_SI, Role.DGD, Role.DGTCP)) {
            CreateDemandeRattachementRequest req = surEntiteExistante(uneEntreprise().getId());
            req.setRoleDemande(interdit);
            req.setUsername("usurpateur-" + interdit.name().toLowerCase() + "-" + System.nanoTime());

            assertThatThrownBy(() -> service.deposer(req, mandat()))
                    .as("rôle %s", interdit)
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).getCode())
                            .isEqualTo(ApiErrorCode.VALIDATION_FAILED));
        }
    }

    @Test
    @Transactional
    void uneQualiteIncoherenteAvecLentiteEstRefusee() {
        CreateDemandeRattachementRequest req = surEntiteExistante(uneEntreprise().getId());
        req.setRoleDemande(Role.AUTORITE_CONTRACTANTE);

        assertThatThrownBy(() -> service.deposer(req, mandat()))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getMessage())
                        .contains("ne correspond pas au type d'entité"));
    }

    @Test
    @Transactional
    void leJustificatifObligatoireManquantEstRefuse() {
        CreateDemandeRattachementRequest req = surEntiteExistante(uneEntreprise().getId());

        assertThatThrownBy(() -> service.deposer(req, Map.of()))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getMessage())
                        .contains("MANDAT_REPRESENTATION"));
    }

    @Test
    @Transactional
    void niEntiteChoisieNiEntiteDecrite_refuse() {
        CreateDemandeRattachementRequest req = surEntiteExistante(null);
        req.setEntiteNom(null);

        assertThatThrownBy(() -> service.deposer(req, mandat()))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getMessage())
                        .contains("Choisissez une entité existante"));
    }

    @Test
    @Transactional
    void unSecondDepotPourLeMemeIdentifiantEstRefuse() {
        Entreprise cible = uneEntreprise();
        CreateDemandeRattachementRequest premier = surEntiteExistante(cible.getId());
        service.deposer(premier, mandat());

        CreateDemandeRattachementRequest second = surEntiteExistante(cible.getId());
        second.setUsername(premier.getUsername());

        assertThatThrownBy(() -> service.deposer(second, mandat()))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(ApiErrorCode.CONFLICT));
    }

    // ── l'approbation ──────────────────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void lapprobationCreeLeCompteEtLeRattache() {
        Entreprise cible = uneEntreprise();
        DemandeRattachementDto depose = service.deposer(surEntiteExistante(cible.getId()), mandat());

        DemandeRattachementDto approuve = service.approuver(depose.getId(), president);

        assertThat(approuve.getStatut()).isEqualTo(StatutDemandeRattachement.APPROUVEE);
        assertThat(approuve.getUtilisateurCreeId()).isNotNull();

        Utilisateur compte = utilisateurRepository.findById(approuve.getUtilisateurCreeId()).orElseThrow();
        assertThat(compte.getRole()).isEqualTo(Role.ENTREPRISE);
        assertThat(compte.getEntreprise().getId()).isEqualTo(cible.getId());
        assertThat(compte.getActif()).isTrue();
    }

    @Test
    @Transactional
    void lapprobationDuneEntiteNouvelleLaCree() {
        long avant = entrepriseRepository.count();
        CreateDemandeRattachementRequest req = surEntiteExistante(null);
        req.setEntiteNom("Société nouvelle " + System.nanoTime());
        req.setEntiteNif("NIF-" + System.nanoTime());
        DemandeRattachementDto depose = service.deposer(req, mandat());
        assertThat(entrepriseRepository.count()).as("rien créé au dépôt").isEqualTo(avant);

        DemandeRattachementDto approuve = service.approuver(depose.getId(), president);

        assertThat(entrepriseRepository.count()).isEqualTo(avant + 1);
        Utilisateur compte = utilisateurRepository.findById(approuve.getUtilisateurCreeId()).orElseThrow();
        assertThat(compte.getEntreprise()).isNotNull();
        assertThat(compte.getEntreprise().getRaisonSociale()).isEqualTo(req.getEntiteNom());
    }

    @Test
    @Transactional
    void lapprobationDuneAutoriteNouvelleLuiDonneUnCodeUnique() {
        AutoriteContractante existante = autoriteRepository.findAll().stream().findFirst().orElseThrow();
        CreateDemandeRattachementRequest req = surAutoriteNouvelle(existante.getCode());

        DemandeRattachementDto approuve = service.approuver(
                service.deposer(req, mandat()).getId(), president);

        Utilisateur compte = utilisateurRepository.findById(approuve.getUtilisateurCreeId()).orElseThrow();
        assertThat(compte.getAutoriteContractante()).isNotNull();
        // Le sigle demandé était déjà pris : le code retombe sur une forme dérivée, sans collision.
        assertThat(compte.getAutoriteContractante().getCode()).isNotEqualTo(existante.getCode());
    }

    // ── le refus ───────────────────────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void leRefusExigeUnMotif() {
        DemandeRattachementDto depose = service.deposer(surEntiteExistante(uneEntreprise().getId()), mandat());

        assertThatThrownBy(() -> service.refuser(depose.getId(), "  ", president))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getMessage()).contains("motif"));
    }

    @Test
    @Transactional
    void leRefusNeCreeAucunCompte() {
        DemandeRattachementDto depose = service.deposer(surEntiteExistante(uneEntreprise().getId()), mandat());

        DemandeRattachementDto refuse = service.refuser(depose.getId(), "Mandat illisible", president);

        assertThat(refuse.getStatut()).isEqualTo(StatutDemandeRattachement.REFUSEE);
        assertThat(refuse.getMotifRefus()).isEqualTo("Mandat illisible");
        assertThat(utilisateurRepository.existsByUsername(depose.getUsername())).isFalse();
    }

    @Test
    @Transactional
    void uneDemandeDejaTraiteeNeSeRejugePas() {
        DemandeRattachementDto depose = service.deposer(surEntiteExistante(uneEntreprise().getId()), mandat());
        service.approuver(depose.getId(), president);

        assertThatThrownBy(() -> service.refuser(depose.getId(), "trop tard", president))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode())
                        .isEqualTo(ApiErrorCode.STATUT_INCOMPATIBLE));
    }

    // ── la recherche publique ne livre que le nécessaire ───────────────────────────────────────

    @Test
    void laRechercheDentitesNeRendQueLidentifiantEtLeNom() {
        var resultats = service.rechercherEntites(TypeEntiteRattachement.ENTREPRISE, null);

        assertThat(resultats).isNotEmpty();
        assertThat(resultats.get(0).nom()).isNotBlank();
        // Le record n'a que deux composants : aucun NIF, aucune adresse, aucune situation fiscale
        // ne peut fuiter par cette route ouverte à tous.
        assertThat(resultats.get(0).getClass().getRecordComponents()).hasSize(2);
    }

    // ── fixtures ───────────────────────────────────────────────────────────────────────────────

    private Entreprise uneEntreprise() {
        return entrepriseRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Aucune entreprise amorcée"));
    }

    private Map<String, MultipartFile> mandat() {
        return Map.of("MANDAT_REPRESENTATION",
                new MockMultipartFile("MANDAT_REPRESENTATION", "mandat.pdf",
                        "application/pdf", "%PDF-1.4 mandat".getBytes()));
    }

    private CreateDemandeRattachementRequest surEntiteExistante(Long entrepriseId) {
        return CreateDemandeRattachementRequest.builder()
                .username("rattache-" + System.nanoTime())
                .password("MotDePasse!2026")
                .nomComplet("Personne à rattacher")
                .email("rattache@example.mr")
                .roleDemande(Role.ENTREPRISE)
                .typeEntite(TypeEntiteRattachement.ENTREPRISE)
                .entiteId(entrepriseId)
                .entiteNom(entrepriseId == null ? "Société nouvelle " + System.nanoTime() : null)
                .build();
    }

    private CreateDemandeRattachementRequest surAutoriteNouvelle(String sigleDejaPris) {
        return CreateDemandeRattachementRequest.builder()
                .username("agent-ac-" + System.nanoTime())
                .password("MotDePasse!2026")
                .nomComplet("Agent d'autorité")
                .email("agent@example.mr")
                .roleDemande(Role.AUTORITE_CONTRACTANTE)
                .typeEntite(TypeEntiteRattachement.AUTORITE_CONTRACTANTE)
                .entiteNom("Autorité nouvelle " + System.nanoTime())
                .entiteSigle(sigleDejaPris)
                .entiteMinistereTutelle("Ministère de tutelle")
                .build();
    }

    // ── les deux formulaires d'inscription ─────────────────────────────────────────────────────

    @Test
    void uneEntrepriseNePeutPasDeclarerUnMinistereDeTutelle() {
        // Les deux inscriptions ne demandent pas les mêmes informations, et l'écran présente donc
        // deux formulaires. Sans cette garde, la distinction ne vivrait que dans le navigateur.
        CreateDemandeRattachementRequest req = surEntiteExistante(null);
        req.setEntiteMinistereTutelle("Ministère glissé par erreur");

        assertThatThrownBy(() -> service.deposer(req, mandat()))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getMessage())
                        .contains("ne concerne pas une entreprise"));
    }

    @Test
    void uneAutoriteNePeutPasDeclarerUnNifNiUneActivite() {
        // Le cas vraiment nuisible : « creerEntite » ignore le NIF pour une autorité contractante.
        // Accepté en silence, le demandeur aurait saisi une donnée qui disparaît à l'approbation,
        // sans que personne ne le lui dise.
        CreateDemandeRattachementRequest avecNif = surAutoriteNouvelle(null);
        avecNif.setEntiteNif("NIF-GLISSE");

        assertThatThrownBy(() -> service.deposer(avecNif, mandat()))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getMessage())
                        .contains("ne concernent pas une autorité contractante"));

        CreateDemandeRattachementRequest avecActivite = surAutoriteNouvelle(null);
        avecActivite.setEntiteActivite("Travaux publics");

        assertThatThrownBy(() -> service.deposer(avecActivite, mandat()))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void chaqueFormulaireResteAccepteAvecSesPropresChamps() {
        // L'inverse de la garde : elle ne doit pas refuser les dépôts légitimes.
        assertThatCode(() -> service.deposer(surAutoriteNouvelle(null), mandat()))
                .doesNotThrowAnyException();
        assertThatCode(() -> service.deposer(surEntiteExistante(null), mandat()))
                .doesNotThrowAnyException();
    }

    @Test
    void leDescriptifNestPasLuQuandUneEntiteExistanteEstChoisie() {
        // La garde ne s'applique qu'à une entité à créer : un champ résiduel envoyé par l'écran
        // après un changement de type ne doit pas faire échouer un rattachement à l'existant.
        Entreprise cible = uneEntreprise();
        CreateDemandeRattachementRequest req = surEntiteExistante(cible.getId());
        req.setEntiteMinistereTutelle("Résidu du formulaire précédent");

        assertThatCode(() -> service.deposer(req, mandat())).doesNotThrowAnyException();
    }

    // ── le suivi public ────────────────────────────────────────────────────────────────────────

    @Test
    void leDemandeurSuitSaDemandeAvecSaReferenceEtSonCourriel() {
        CreateDemandeRattachementRequest req = surEntiteExistante(uneEntreprise().getId());
        DemandeRattachementDto depose = service.deposer(req, mandat());

        SuiviRattachementDto suivi = service.suivre(depose.getReference(), req.getEmail());

        assertThat(suivi.getReference()).isEqualTo(depose.getReference());
        assertThat(suivi.getStatut()).isEqualTo(StatutDemandeRattachement.EN_ATTENTE);
        assertThat(suivi.getRoleDemande()).isEqualTo(Role.ENTREPRISE);
        assertThat(suivi.getEntiteNom()).isNotBlank();
        assertThat(suivi.getDateDepot()).isNotNull();
        assertThat(suivi.getDateTraitement()).isNull();
    }

    @Test
    void leSuiviNeRendAucuneDonneePersonnelle() {
        // La référence est séquentielle, donc devinable. Ce que rend cette route doit rester ce que
        // le demandeur sait déjà : ni identifiant de connexion, ni téléphone, ni courriel.
        CreateDemandeRattachementRequest req = surEntiteExistante(uneEntreprise().getId());
        DemandeRattachementDto depose = service.deposer(req, mandat());

        SuiviRattachementDto suivi = service.suivre(depose.getReference(), req.getEmail());

        assertThat(suivi).hasNoNullFieldsOrPropertiesExcept(
                "dateTraitement", "motifRefus");
        assertThat(suivi.toString())
                .doesNotContain(req.getUsername())
                .doesNotContain(req.getEmail());
    }

    @Test
    void unCourrielQuiNeCorrespondPasEchoueCommeUneReferenceInconnue() {
        // Les deux erreurs doivent être indiscernables : les distinguer confirmerait l'existence
        // d'une référence à qui la cherche, et le courriel ne servirait plus à rien.
        CreateDemandeRattachementRequest req = surEntiteExistante(uneEntreprise().getId());
        DemandeRattachementDto depose = service.deposer(req, mandat());

        String messageMauvaisCourriel = messageDeRefus(depose.getReference(), "autre@example.mr");
        String messageReferenceInconnue = messageDeRefus("RT-999-12/1999", req.getEmail());

        assertThat(messageMauvaisCourriel).isEqualTo(messageReferenceInconnue);
    }

    @Test
    void leSuiviExigeLesDeuxElements() {
        assertThatThrownBy(() -> service.suivre(null, "a@b.mr")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.suivre("RT-001-10/2026", " ")).isInstanceOf(ApiException.class);
    }

    private String messageDeRefus(String reference, String email) {
        try {
            service.suivre(reference, email);
            throw new AssertionError("Le suivi aurait dû échouer");
        } catch (ApiException e) {
            return e.getMessage();
        }
    }
}
