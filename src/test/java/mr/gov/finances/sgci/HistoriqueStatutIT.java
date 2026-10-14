package mr.gov.finances.sgci;

import mr.gov.finances.sgci.domain.entity.AutoriteContractante;
import mr.gov.finances.sgci.domain.entity.Convention;
import mr.gov.finances.sgci.domain.entity.HistoriqueStatut;
import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.domain.enums.StatutConvention;
import mr.gov.finances.sgci.repository.AutoriteContractanteRepository;
import mr.gov.finances.sgci.repository.ConventionRepository;
import mr.gov.finances.sgci.repository.HistoriqueStatutRepository;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.security.ActeurCourant;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Les transitions de statut laissent une trace, quel que soit le service qui les provoque.
 *
 * <p>{@link #uneTransitionHorsValidateurEstHistorisee()} est le test qui justifie le choix d'un
 * écouteur Hibernate : la convention ne passe par aucun validateur de workflow, donc une
 * historisation posée sur les 26 appels à {@code validateTransition} l'aurait manquée — comme elle
 * aurait manqué les marchés, les sous-traitances et les référentiels projet.
 *
 * <p><b>Ces tests ne sont pas {@code @Transactional}, et ils ne peuvent pas l'être.</b> L'écriture
 * n'a lieu qu'<em>après</em> le commit, précisément pour qu'une transaction annulée ne laisse rien ;
 * un test transactionnel ne verrait donc jamais une seule ligne. Ils committent, et nettoient ce
 * qu'ils ont créé — d'où le {@code @AfterEach}. Les comptages se font par identifiant d'entité,
 * jamais globalement : le jeu de démonstration et les autres classes de test écrivent aussi.
 *
 * <p>Niveau service : Tomcat ne démarre pas sur ce poste.
 */
@SpringBootTest
@ActiveProfiles("test")
class HistoriqueStatutIT {

    private static final String TYPE = "Convention";

    @Autowired
    private ConventionRepository conventionRepository;

    @Autowired
    private AutoriteContractanteRepository autoriteRepository;

    @Autowired
    private HistoriqueStatutRepository historiqueRepository;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private Convention convention;

    @BeforeEach
    void creerUneConvention() {
        AutoriteContractante autorite = autoriteRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Aucune autorité contractante amorcée"));
        convention = conventionRepository.save(Convention.builder()
                .reference("CONV-HISTO-" + System.nanoTime())
                .intitule("Convention d'historisation")
                .statut(StatutConvention.EN_ATTENTE)
                .autoriteContractante(autorite)
                .actif(true)
                .dateCreation(Instant.now())
                .build());
    }

    @AfterEach
    void nettoyer() {
        SecurityContextHolder.clearContext();
        if (convention != null && convention.getId() != null) {
            historiqueRepository.deleteAll(lignes());
            conventionRepository.deleteById(convention.getId());
        }
    }

    // ── ce qui justifie l'écouteur ─────────────────────────────────────────────────────────────

    @Test
    void uneTransitionHorsValidateurEstHistorisee() {
        // La convention ne passe par aucun validateur de workflow : avec une historisation posée
        // appel par appel, cette transition n'aurait laissé aucune trace.
        basculerVers(StatutConvention.VALIDE);

        List<HistoriqueStatut> apres = lignes();

        assertThat(apres)
                .filteredOn(l -> "UPDATE".equals(l.getOrigine()))
                .singleElement()
                .satisfies(l -> {
                    assertThat(l.getStatutAvant()).isEqualTo(StatutConvention.EN_ATTENTE.name());
                    assertThat(l.getStatutApres()).isEqualTo(StatutConvention.VALIDE.name());
                    assertThat(l.getEntityType()).isEqualTo(TYPE);
                    assertThat(l.getEntityId()).isEqualTo(convention.getId());
                    assertThat(l.getDateTransition()).isNotNull();
                });
    }

    @Test
    void laCreationEstHistorisee() {
        // Sans la ligne d'insertion, « depuis combien de temps dans ce statut » est faux pour tout
        // dossier qui n'a jamais été modifié depuis sa création.
        assertThat(lignes())
                .filteredOn(l -> "INSERT".equals(l.getOrigine()))
                .singleElement()
                .satisfies(l -> {
                    assertThat(l.getStatutAvant()).isNull();
                    assertThat(l.getStatutApres()).isEqualTo(StatutConvention.EN_ATTENTE.name());
                });
    }

    // ── ce qui ne doit rien écrire ─────────────────────────────────────────────────────────────

    @Test
    void unStatutInchangeNecreeAucuneLigne() {
        // Toute mise à jour déclenche l'écouteur, statut ou non : s'il cessait de comparer, la table
        // enflerait d'une ligne par enregistrement et les durées deviendraient absurdes.
        int avant = lignes().size();

        Convention chargee = conventionRepository.findById(convention.getId()).orElseThrow();
        chargee.setIntitule("Intitulé corrigé, statut intact");
        conventionRepository.saveAndFlush(chargee);

        assertThat(lignes()).hasSize(avant);
    }

    @Test
    void unRollbackNeLaissePasDeLigne() {
        // C'est tout l'intérêt d'écrire après le commit, et non pendant.
        int avant = lignes().size();

        transactionTemplate.execute(status -> {
            Convention chargee = conventionRepository.findById(convention.getId()).orElseThrow();
            chargee.setStatut(StatutConvention.REJETE);
            conventionRepository.saveAndFlush(chargee);
            status.setRollbackOnly();
            return null;
        });

        assertThat(lignes()).hasSize(avant);
        assertThat(conventionRepository.findById(convention.getId()).orElseThrow().getStatut())
                .isEqualTo(StatutConvention.EN_ATTENTE);
    }

    // ── l'acteur ───────────────────────────────────────────────────────────────────────────────

    @Test
    void lauteurEstLutilisateurAuthentifie() {
        Utilisateur president = utilisateurRepository.findByUsername("president").orElseThrow();
        AuthenticatedUser principal = new AuthenticatedUser(
                president.getId(), president.getUsername(), Role.PRESIDENT);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null,
                        List.of(new SimpleGrantedAuthority("ROLE_PRESIDENT"))));

        basculerVers(StatutConvention.VALIDE);

        assertThat(lignes())
                .filteredOn(l -> "UPDATE".equals(l.getOrigine()))
                .singleElement()
                .satisfies(l -> {
                    assertThat(l.getUsername()).isEqualTo("president");
                    assertThat(l.getUtilisateurId()).isEqualTo(president.getId());
                    assertThat(l.getRoleActeur()).isEqualTo(Role.PRESIDENT.name());
                });
    }

    @Test
    void horsContexteDeSecuriteLauteurEstLeSysteme() {
        // Les migrations de démarrage et les traitements différés écrivent sans authentification.
        // « system » est l'information juste, pas un défaut à masquer.
        SecurityContextHolder.clearContext();

        basculerVers(StatutConvention.ANNULEE);

        assertThat(lignes())
                .filteredOn(l -> "UPDATE".equals(l.getOrigine()))
                .singleElement()
                .satisfies(l -> {
                    assertThat(l.getUsername()).isEqualTo(ActeurCourant.SYSTEME);
                    assertThat(l.getUtilisateurId()).isNull();
                    assertThat(l.getRoleActeur()).isNull();
                });
    }

    // ── fixtures ───────────────────────────────────────────────────────────────────────────────

    private void basculerVers(StatutConvention cible) {
        Convention chargee = conventionRepository.findById(convention.getId()).orElseThrow();
        chargee.setStatut(cible);
        conventionRepository.saveAndFlush(chargee);
    }

    /** Les lignes de ce dossier seulement : les autres classes de test en écrivent aussi. */
    private List<HistoriqueStatut> lignes() {
        return historiqueRepository.findByEntityTypeAndEntityIdOrderByDateTransitionAsc(
                TYPE, convention.getId());
    }
}
