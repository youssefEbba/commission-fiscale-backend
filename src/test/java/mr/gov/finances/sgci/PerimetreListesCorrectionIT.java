package mr.gov.finances.sgci;

import mr.gov.finances.sgci.domain.entity.AutoriteContractante;
import mr.gov.finances.sgci.domain.entity.Bailleur;
import mr.gov.finances.sgci.domain.entity.Convention;
import mr.gov.finances.sgci.domain.entity.DemandeCorrection;
import mr.gov.finances.sgci.domain.entity.Entreprise;
import mr.gov.finances.sgci.domain.entity.Marche;
import mr.gov.finances.sgci.domain.entity.MarcheDelegue;
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
import mr.gov.finances.sgci.service.DemandeCorrectionService;
import mr.gov.finances.sgci.web.dto.DemandeCorrectionDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Les listes de demandes de correction filtrées par un critère de la requête — statut, entreprise,
 * autorité, délégué — appliquent le périmètre de l'appelant.
 *
 * <p>{@code findAll} et {@code findById} le font depuis toujours. Ces quatre listes n'en
 * appliquaient aucun hors brouillon, alors que leurs routes acceptent
 * {@code correction.entreprise.queue.view}, que détient le rôle ENTREPRISE, et
 * {@code correction.visa.history.view} et {@code correction.offer.view}, que détiennent l'autorité
 * contractante et ses délégués. Une entreprise listait donc, par statut, les demandes de toutes les
 * autorités, et par identifiant celles de n'importe quelle autre entreprise ; une autorité, celles
 * d'une autre autorité ou des délégués d'une autre autorité. Le masquage des pièces de sortie n'y
 * changeait rien : il cachait des pièces, pas l'existence ni le contenu des demandes d'autrui.
 *
 * <p>Niveau service : Tomcat ne démarre pas sur ce poste.
 */
@SpringBootTest
@ActiveProfiles("test")
class PerimetreListesCorrectionIT {

    private static final StatutDemande STATUT = StatutDemande.RECUE;

    @Autowired private DemandeCorrectionService service;
    @Autowired private DemandeCorrectionRepository demandeRepository;
    @Autowired private AutoriteContractanteRepository autoriteRepository;
    @Autowired private EntrepriseRepository entrepriseRepository;
    @Autowired private ConventionRepository conventionRepository;
    @Autowired private BailleurRepository bailleurRepository;
    @Autowired private MarcheRepository marcheRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;

    private AuthenticatedUser entreprise;
    private AuthenticatedUser ac;
    private AuthenticatedUser dgtcp;
    /** Le compte relais, agissant au nom de l'entreprise du seed. */
    private AuthenticatedUser relaisPourEntreprise;
    /** Délégué de l'autre autorité, sur le marché de la demande étrangère. */
    private AuthenticatedUser delegueEtranger;
    /** Délégué de l'autorité du seed, sur le marché de la demande propre. */
    private AuthenticatedUser delegueDuSeed;

    private Long entrepriseId;
    private Long autreEntrepriseId;
    private Long autoriteId;
    private Long autreAutoriteId;

    /** Ni l'autorité ni l'entreprise du seed : personne d'autre qu'une direction ne doit la voir. */
    private DemandeCorrection demandeEtrangere;
    /** L'entreprise du seed, mais déposée par une autre autorité : son dossier, pas celui de l'AC du seed. */
    private DemandeCorrection demandeCroisee;
    /** L'autorité et l'entreprise du seed : le témoin que chacun voit. */
    private DemandeCorrection demandePropre;

    @BeforeEach
    void setUp() {
        Utilisateur compteEntreprise = utilisateurRepository.findByUsername("entreprise").orElseThrow();
        entreprise = compte(compteEntreprise);
        Entreprise entrepriseDuSeed = compteEntreprise.getEntreprise();
        entrepriseId = entrepriseDuSeed.getId();

        Utilisateur compteAc = utilisateurRepository.findByUsername("ac").orElseThrow();
        ac = compte(compteAc);
        AutoriteContractante autoriteDuSeed = compteAc.getAutoriteContractante();
        autoriteId = autoriteDuSeed.getId();

        dgtcp = compte(utilisateurRepository.findByUsername("dgtcp").orElseThrow());

        Utilisateur compteRelais = utilisateurRepository.findByUsername("commission_relais").orElseThrow();
        relaisPourEntreprise = new AuthenticatedUser(compteRelais.getId(), compteRelais.getUsername(),
                Role.ENTREPRISE, true, entrepriseId, null);

        AutoriteContractante autreAutorite = autoriteRepository.save(AutoriteContractante.builder()
                .nom("Autorité témoin du périmètre des listes")
                .code("AC-LISTES-" + System.nanoTime())
                .contact("temoin@test.local")
                .build());
        Entreprise autreEntreprise = entrepriseRepository.save(Entreprise.builder()
                .raisonSociale("Entreprise témoin du périmètre des listes")
                .nif("NIF-LISTES-" + System.nanoTime())
                .build());
        autreEntrepriseId = autreEntreprise.getId();
        autreAutoriteId = autreAutorite.getId();

        demandeEtrangere = demande(autreAutorite, autreEntreprise);
        demandeCroisee = demande(autreAutorite, entrepriseDuSeed);
        demandePropre = demande(autoriteDuSeed, entrepriseDuSeed);

        delegueEtranger = delegueSurLeMarcheDe(demandeEtrangere, autreAutorite);
        delegueDuSeed = delegueSurLeMarcheDe(demandePropre, autoriteDuSeed);
    }

    // ── par statut ─────────────────────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void parStatut_uneEntrepriseNeVoitPasLesDemandesDAutrui() {
        assertThat(ids(service.findByStatut(STATUT, entreprise)))
                .doesNotContain(demandeEtrangere.getId())
                .contains(demandeCroisee.getId(), demandePropre.getId());
    }

    @Test
    @Transactional
    void parStatut_uneAutoriteNeVoitQueSaStructure() {
        assertThat(ids(service.findByStatut(STATUT, ac)))
                .doesNotContain(demandeEtrangere.getId(), demandeCroisee.getId())
                .contains(demandePropre.getId());
    }

    @Test
    @Transactional
    void parStatut_leRelaisVoitLePerimetreDeLEntrepriseRepresentee() {
        assertThat(ids(service.findByStatut(STATUT, relaisPourEntreprise)))
                .doesNotContain(demandeEtrangere.getId())
                .contains(demandeCroisee.getId(), demandePropre.getId());
    }

    @Test
    @Transactional
    void parStatut_uneDirectionVoitTout() {
        assertThat(ids(service.findByStatut(STATUT, dgtcp)))
                .contains(demandeEtrangere.getId(), demandeCroisee.getId(), demandePropre.getId());
    }

    @Test
    @Transactional
    void parStatut_neRenvoieQueLeStatutDemande() {
        assertThat(service.findByStatut(STATUT, entreprise))
                .extracting(DemandeCorrectionDto::getStatut)
                .containsOnly(STATUT);
    }

    // ── par entreprise ─────────────────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void parEntreprise_uneEntrepriseNeListePasUneAutreEntreprise() {
        assertThat(service.findByEntreprise(autreEntrepriseId, entreprise)).isEmpty();
    }

    @Test
    @Transactional
    void parEntreprise_uneEntrepriseListeTousSesDossiers() {
        assertThat(ids(service.findByEntreprise(entrepriseId, entreprise)))
                .contains(demandeCroisee.getId(), demandePropre.getId());
    }

    @Test
    @Transactional
    void parEntreprise_uneAutoriteNeVoitQueLesDemandesDeSaStructure() {
        // L'assistant de création appelle cette route avec l'entreprise choisie, pour griser les
        // marchés déjà pris : l'AC y retrouve ses propres demandes, pas celles des autres autorités.
        assertThat(ids(service.findByEntreprise(entrepriseId, ac)))
                .doesNotContain(demandeCroisee.getId())
                .contains(demandePropre.getId());
        assertThat(service.findByEntreprise(autreEntrepriseId, ac)).isEmpty();
    }

    @Test
    @Transactional
    void parEntreprise_leRelaisNeSortPasDeLEntrepriseRepresentee() {
        assertThat(ids(service.findByEntreprise(entrepriseId, relaisPourEntreprise)))
                .contains(demandeCroisee.getId(), demandePropre.getId());
        assertThat(service.findByEntreprise(autreEntrepriseId, relaisPourEntreprise)).isEmpty();
    }

    @Test
    @Transactional
    void parEntreprise_uneDirectionVoitToutesLesDemandesDeLEntreprise() {
        assertThat(ids(service.findByEntreprise(entrepriseId, dgtcp)))
                .contains(demandeCroisee.getId(), demandePropre.getId());
        assertThat(ids(service.findByEntreprise(autreEntrepriseId, dgtcp)))
                .containsExactly(demandeEtrangere.getId());
    }

    // ── par autorité ───────────────────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void parAutorite_uneAutoriteNeListePasUneAutreAutorite() {
        assertThat(service.findByAutoriteContractante(autreAutoriteId, ac)).isEmpty();
    }

    @Test
    @Transactional
    void parAutorite_uneAutoriteListeSaStructure() {
        // Demandes.tsx et la mise en place appellent cette route avec l'autorité du compte.
        assertThat(ids(service.findByAutoriteContractante(autoriteId, ac)))
                .contains(demandePropre.getId());
    }

    @Test
    @Transactional
    void parAutorite_unDelegueNeVoitQueLesDemandesDeSesMarches() {
        assertThat(service.findByAutoriteContractante(autoriteId, delegueEtranger)).isEmpty();
        assertThat(ids(service.findByAutoriteContractante(autreAutoriteId, delegueEtranger)))
                .containsExactly(demandeEtrangere.getId());
    }

    @Test
    @Transactional
    void parAutorite_uneEntrepriseNeVoitQueSesDossiers() {
        assertThat(ids(service.findByAutoriteContractante(autreAutoriteId, entreprise)))
                .containsExactly(demandeCroisee.getId());
    }

    @Test
    @Transactional
    void parAutorite_uneDirectionVoitToutesLesDemandesDeLAutorite() {
        assertThat(ids(service.findByAutoriteContractante(autreAutoriteId, dgtcp)))
                .containsExactlyInAnyOrder(demandeEtrangere.getId(), demandeCroisee.getId());
    }

    // ── par délégué ────────────────────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void parDelegue_uneAutoriteNeListePasLeDelegueDUneAutre() {
        // Le contrôle d'origine ne gardait que les délégués eux-mêmes ; l'autorité n'en avait aucun.
        assertThat(service.findByDelegue(delegueEtranger.getUserId(), ac)).isEmpty();
    }

    @Test
    @Transactional
    void parDelegue_uneAutoriteListeSesDelegues() {
        assertThat(ids(service.findByDelegue(delegueDuSeed.getUserId(), ac)))
                .containsExactly(demandePropre.getId());
    }

    @Test
    @Transactional
    void parDelegue_unDelegueListeSesDemandes() {
        assertThat(ids(service.findByDelegue(delegueEtranger.getUserId(), delegueEtranger)))
                .containsExactly(demandeEtrangere.getId());
    }

    @Test
    @Transactional
    void parDelegue_uneEntrepriseNeListePasLesDemandesDAutrui() {
        assertThat(service.findByDelegue(delegueEtranger.getUserId(), entreprise)).isEmpty();
    }

    @Test
    @Transactional
    void parDelegue_uneDirectionVoitToutesLesDemandesDuDelegue() {
        assertThat(ids(service.findByDelegue(delegueEtranger.getUserId(), dgtcp)))
                .containsExactly(demandeEtrangere.getId());
    }

    // ── fixtures ───────────────────────────────────────────────────────────────────────────────

    private static List<Long> ids(List<DemandeCorrectionDto> dtos) {
        return dtos.stream().map(DemandeCorrectionDto::getId).toList();
    }

    private static AuthenticatedUser compte(Utilisateur u) {
        return new AuthenticatedUser(u.getId(), u.getUsername(), u.getRole());
    }

    private DemandeCorrection demande(AutoriteContractante autorite, Entreprise titulaire) {
        Bailleur bailleur = bailleurRepository.findAll().stream().findFirst()
                .orElseGet(() -> bailleurRepository.save(Bailleur.builder().nom("Bailleur témoin").build()));
        Convention convention = conventionRepository.save(Convention.builder()
                .reference("CONV-LISTES-" + System.nanoTime())
                .intitule("Convention témoin")
                .bailleur(bailleur)
                .autoriteContractante(autorite)
                .statut(StatutConvention.EN_ATTENTE)
                .build());
        return demandeRepository.saveAndFlush(DemandeCorrection.builder()
                .numero("DC-LISTES-" + System.nanoTime())
                .dateDepot(Instant.now())
                .statut(STATUT)
                .autoriteContractante(autorite)
                .entreprise(titulaire)
                .convention(convention)
                .build());
    }

    /** Un délégué UPM de cette autorité, sur un marché rattaché à la demande. */
    private AuthenticatedUser delegueSurLeMarcheDe(DemandeCorrection demande, AutoriteContractante autorite) {
        Utilisateur delegue = utilisateurRepository.save(Utilisateur.builder()
                .username("delegue-listes-" + System.nanoTime())
                .passwordHash("non-utilise")
                .role(Role.AUTORITE_UPM)
                .autoriteContractante(autorite)
                .nomComplet("Délégué témoin du périmètre des listes")
                .build());
        Marche marche = Marche.builder()
                .numeroMarche("MP-LISTES-" + System.nanoTime())
                .dateSignature(LocalDate.now())
                .montantContratHt(BigDecimal.valueOf(750_000))
                .statut(StatutMarche.EN_COURS)
                .convention(demande.getConvention())
                .demandeCorrection(demande)
                .build();
        marche.getDelegues().add(MarcheDelegue.builder().marche(marche).delegue(delegue).build());
        demande.setMarche(marcheRepository.saveAndFlush(marche));
        return new AuthenticatedUser(delegue.getId(), delegue.getUsername(), Role.AUTORITE_UPM);
    }
}
