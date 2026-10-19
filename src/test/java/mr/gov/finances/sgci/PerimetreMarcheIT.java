package mr.gov.finances.sgci;

import mr.gov.finances.sgci.domain.entity.AutoriteContractante;
import mr.gov.finances.sgci.domain.entity.Bailleur;
import mr.gov.finances.sgci.domain.entity.Convention;
import mr.gov.finances.sgci.domain.entity.Marche;
import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.domain.enums.StatutConvention;
import mr.gov.finances.sgci.domain.enums.StatutMarche;
import mr.gov.finances.sgci.repository.AutoriteContractanteRepository;
import mr.gov.finances.sgci.repository.BailleurRepository;
import mr.gov.finances.sgci.repository.ConventionRepository;
import mr.gov.finances.sgci.repository.MarcheRepository;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.MarcheService;
import mr.gov.finances.sgci.web.dto.UpdateMarcheRequest;
import mr.gov.finances.sgci.web.exception.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Une autorité contractante ne modifie que ses propres marchés.
 *
 * <p>La lecture d'un marché applique un périmètre depuis toujours — {@code findById} filtre par
 * {@code canAccessMarche}. L'écriture n'en appliquait aucun : {@code MarcheService.update} ne
 * recevait pas d'utilisateur, et {@code marche.manage} est détenu par les trois rôles d'autorité
 * contractante. N'importe laquelle pouvait donc réécrire, par identifiant, le numéro, l'intitulé,
 * la date de signature, le montant du contrat et le statut du marché de n'importe quelle autre.
 *
 * <p>C'est la faille déjà corrigée sur les conventions, dont le commentaire porte la leçon :
 * « sans ce contrôle, elle fermerait par identifiant la convention de n'importe quelle autre
 * autorité ». Elle n'avait pas été reportée au marché.
 *
 * <p>Niveau service : Tomcat ne démarre pas sur ce poste.
 */
@SpringBootTest
@ActiveProfiles("test")
class PerimetreMarcheIT {

    @Autowired
    private MarcheService marcheService;

    @Autowired
    private MarcheRepository marcheRepository;

    @Autowired
    private ConventionRepository conventionRepository;

    @Autowired
    private AutoriteContractanteRepository autoriteContractanteRepository;

    @Autowired
    private BailleurRepository bailleurRepository;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    /** Le compte d'autorité contractante amorcé par le seed. */
    private AuthenticatedUser autoriteDuSeed;

    /** Un marché qui appartient à une *autre* autorité, créée pour ce test. */
    private Marche marcheEtranger;

    /** Un marché de l'autorité du seed : le témoin. */
    private Marche monMarche;

    @BeforeEach
    void setUp() {
        Utilisateur compte = utilisateurRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.AUTORITE_CONTRACTANTE
                        && u.getAutoriteContractante() != null)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Aucun compte d'autorité contractante amorcé"));
        autoriteDuSeed = new AuthenticatedUser(compte.getId(), compte.getUsername(),
                Role.AUTORITE_CONTRACTANTE);

        Long sonAutoriteId = compte.getAutoriteContractante().getId();

        monMarche = marcheRepository.findAll().stream()
                .filter(m -> m.getConvention() != null
                        && m.getConvention().getAutoriteContractante() != null
                        && sonAutoriteId.equals(m.getConvention().getAutoriteContractante().getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Aucun marché amorcé pour l'autorité du seed"));

        marcheEtranger = creerMarchePourUneAutreAutorite();
    }

    // ── la fuite ───────────────────────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void uneAutoriteContractanteNeModifiePasLeMarcheDUneAutre() {
        assertThatThrownBy(() -> marcheService.update(
                marcheEtranger.getId(), demandeDeModification("USURPE-001"), autoriteDuSeed))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("hors périmètre");
    }

    @Test
    @Transactional
    void leMarcheEtrangerResteIntactApresUneTentative() {
        // Le refus ne suffit pas : il faut qu'aucune écriture n'ait eu lieu avant lui.
        String numeroAvant = marcheEtranger.getNumeroMarche();

        assertThatThrownBy(() -> marcheService.update(
                marcheEtranger.getId(), demandeDeModification("USURPE-002"), autoriteDuSeed))
                .isInstanceOf(ApiException.class);

        assertThat(marcheRepository.findById(marcheEtranger.getId()).orElseThrow().getNumeroMarche())
                .as("le marché d'une autre autorité ne doit pas avoir bougé")
                .isEqualTo(numeroAvant);
    }

    @Test
    @Transactional
    void lesPiecesDUnMarcheEtrangerNeSontPasLisibles() {
        assertThatThrownBy(() -> marcheService.findDocuments(marcheEtranger.getId(), autoriteDuSeed))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("hors périmètre");
    }

    // ── le pendant : sans lui, tout fermer passerait pour une réussite ──────────────────────────

    @Test
    @Transactional
    void chaqueAutoriteModifieSesPropresMarches() {
        assertThatCode(() -> marcheService.update(
                monMarche.getId(), demandeDeModification(monMarche.getNumeroMarche()), autoriteDuSeed))
                .doesNotThrowAnyException();
    }

    @Test
    @Transactional
    void uneAutoriteLitLesPiecesDeSesPropresMarches() {
        assertThatCode(() -> marcheService.findDocuments(monMarche.getId(), autoriteDuSeed))
                .doesNotThrowAnyException();
    }

    @Test
    @Transactional
    void lesDirectionsGardentLaMainSurTousLesMarches() {
        // Le périmètre ne vaut que pour les autorités contractantes et leurs délégués. Une
        // direction qui perdrait l'accès ne pourrait plus instruire.
        Utilisateur dgtcp = utilisateurRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.DGTCP).findFirst().orElse(null);
        if (dgtcp == null) {
            return;
        }
        AuthenticatedUser acteur =
                new AuthenticatedUser(dgtcp.getId(), dgtcp.getUsername(), Role.DGTCP);

        assertThatCode(() -> marcheService.update(
                marcheEtranger.getId(),
                demandeDeModification(marcheEtranger.getNumeroMarche()), acteur))
                .doesNotThrowAnyException();
    }

    // ── un marché clos ne se réécrit plus ──────────────────────────────────────────────────────

    @Test
    @Transactional
    void unMarcheClotureNeSeModifiePlus() {
        // Les deux contrôles d'origine ne gardaient que le changement de statut : le numéro, la
        // date de signature et le montant se réécrivaient encore sur un marché clôturé, alors que
        // ses documents étaient déjà verrouillés.
        monMarche.setStatut(StatutMarche.CLOTURE);
        marcheRepository.saveAndFlush(monMarche);

        UpdateMarcheRequest req = demandeDeModification("RENUMEROTE-001");
        req.setStatut(StatutMarche.CLOTURE);

        assertThatThrownBy(() -> marcheService.update(monMarche.getId(), req, autoriteDuSeed))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("clôturé");
    }

    @Test
    @Transactional
    void unMarcheEnCoursResteModifiable() {
        monMarche.setStatut(StatutMarche.EN_COURS);
        marcheRepository.saveAndFlush(monMarche);

        assertThatCode(() -> marcheService.update(monMarche.getId(),
                demandeDeModification(monMarche.getNumeroMarche()), autoriteDuSeed))
                .doesNotThrowAnyException();
    }

    // ── fixtures ───────────────────────────────────────────────────────────────────────────────

    private UpdateMarcheRequest demandeDeModification(String numero) {
        return UpdateMarcheRequest.builder()
                .numeroMarche(numero)
                .intitule("Intitulé de test")
                .dateSignature(LocalDate.now())
                .montantContratHt(BigDecimal.valueOf(500_000))
                .statut(StatutMarche.EN_COURS)
                .build();
    }

    /** Une autorité distincte de celle du seed, avec sa convention et son marché. */
    private Marche creerMarchePourUneAutreAutorite() {
        AutoriteContractante autre = autoriteContractanteRepository.save(
                AutoriteContractante.builder()
                        .nom("Autorité témoin du test de périmètre")
                        .code("AC-PERIMETRE-" + System.nanoTime())
                        .contact("temoin@test.local")
                        .build());

        Bailleur bailleur = bailleurRepository.findAll().stream().findFirst()
                .orElseGet(() -> bailleurRepository.save(
                        Bailleur.builder().nom("Bailleur témoin").build()));

        Convention convention = conventionRepository.save(Convention.builder()
                .reference("CONV-PERIMETRE-" + System.nanoTime())
                .intitule("Convention témoin")
                .bailleur(bailleur)
                .autoriteContractante(autre)
                .statut(StatutConvention.EN_ATTENTE)
                .build());

        return marcheRepository.saveAndFlush(Marche.builder()
                .numeroMarche("MP-PERIMETRE-" + System.nanoTime())
                .dateSignature(LocalDate.now())
                .montantContratHt(BigDecimal.valueOf(750_000))
                .statut(StatutMarche.EN_COURS)
                .convention(convention)
                .build());
    }
}
