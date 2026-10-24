package mr.gov.finances.sgci;

import mr.gov.finances.sgci.domain.entity.AutoriteContractante;
import mr.gov.finances.sgci.domain.entity.CertificatCredit;
import mr.gov.finances.sgci.domain.entity.Convention;
import mr.gov.finances.sgci.domain.entity.DemandeCorrection;
import mr.gov.finances.sgci.domain.entity.Entreprise;
import mr.gov.finances.sgci.domain.entity.Marche;
import mr.gov.finances.sgci.domain.entity.ReclamationDemandeCorrection;
import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.domain.enums.StatutCertificat;
import mr.gov.finances.sgci.domain.enums.StatutConvention;
import mr.gov.finances.sgci.domain.enums.StatutMarche;
import mr.gov.finances.sgci.domain.enums.StatutReclamationCorrection;
import mr.gov.finances.sgci.repository.AutoriteContractanteRepository;
import mr.gov.finances.sgci.repository.CertificatCreditRepository;
import mr.gov.finances.sgci.repository.ConventionRepository;
import mr.gov.finances.sgci.repository.DemandeCorrectionRepository;
import mr.gov.finances.sgci.repository.EntrepriseRepository;
import mr.gov.finances.sgci.repository.MarcheRepository;
import mr.gov.finances.sgci.repository.ReclamationDemandeCorrectionRepository;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.CertificatCreditService;
import mr.gov.finances.sgci.service.ConventionService;
import mr.gov.finances.sgci.service.DelegueService;
import mr.gov.finances.sgci.service.MarcheService;
import mr.gov.finances.sgci.service.ReclamationDemandeCorrectionService;
import mr.gov.finances.sgci.service.ReferentielProjetService;
import mr.gov.finances.sgci.web.dto.ConventionDto;
import mr.gov.finances.sgci.web.dto.CreateConventionRequest;
import mr.gov.finances.sgci.web.dto.CreateMarcheRequest;
import mr.gov.finances.sgci.web.dto.CreateReferentielProjetRequest;
import mr.gov.finances.sgci.web.dto.MarcheDto;
import mr.gov.finances.sgci.web.dto.ReferentielProjetDto;
import mr.gov.finances.sgci.web.dto.UpdateMarcheRequest;
import mr.gov.finances.sgci.web.exception.ApiException;
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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La commission relais qui agit pour une autorité contractante ou une entreprise a <b>tous</b> les
 * accès du compte rattaché à cette entité — et aucun de plus.
 *
 * <p>Les permissions suivaient déjà : le jeton de relais porte le rôle de l'entité représentée et
 * ses permissions. Le défaut était ailleurs. Plusieurs services demandaient « quelle est mon
 * autorité » au compte relais <em>en base</em>, qui n'en a aucune. D'où deux échecs opposés :
 *
 * <ul>
 *   <li><b>trop peu</b> — « Aucune autorité contractante liée à l'utilisateur » à la création d'une
 *       convention ou d'un référentiel projet, refus de rôle sur les réclamations et les délégués ;</li>
 *   <li><b>trop</b> — le marché lisait le rôle en base, « commission relais », qui tombait dans la
 *       branche des directions : le relais voyait et modifiait les marchés de toutes les autorités.</li>
 * </ul>
 *
 * <p>Les deux sont testés, parce que corriger l'un sans l'autre serait facile : ouvrir tout règle
 * le premier et aggrave le second.
 *
 * <p>Niveau service : Tomcat ne démarre pas sur ce poste.
 */
@SpringBootTest
@ActiveProfiles("test")
class CommissionRelaisAccesIT {

    @Autowired private ConventionService conventionService;
    @Autowired private ReferentielProjetService referentielProjetService;
    @Autowired private MarcheService marcheService;
    @Autowired private DelegueService delegueService;
    @Autowired private ReclamationDemandeCorrectionService reclamationService;
    @Autowired private CertificatCreditService certificatCreditService;

    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private AutoriteContractanteRepository autoriteRepository;
    @Autowired private ConventionRepository conventionRepository;
    @Autowired private MarcheRepository marcheRepository;
    @Autowired private DemandeCorrectionRepository demandeRepository;
    @Autowired private ReclamationDemandeCorrectionRepository reclamationRepository;
    @Autowired private CertificatCreditRepository certificatRepository;
    @Autowired private EntrepriseRepository entrepriseRepository;

    private Utilisateur compteRelais;
    private Utilisateur compteAc;
    private Long monAutoriteId;

    /** Le relais qui agit pour l'autorité du seed. */
    private AuthenticatedUser relaisEnAutorite;

    @BeforeEach
    void setUp() {
        compteRelais = utilisateurRepository.findByUsername("commission_relais").orElseThrow();
        compteAc = utilisateurRepository.findByUsername("ac").orElseThrow();
        monAutoriteId = compteAc.getAutoriteContractante().getId();
        relaisEnAutorite = new AuthenticatedUser(compteRelais.getId(), compteRelais.getUsername(),
                Role.AUTORITE_CONTRACTANTE, true, null, monAutoriteId);
    }

    // ── créer au nom de l'autorité représentée ─────────────────────────────────────────────────

    @Test
    @Transactional
    void leRelaisCreeUneConventionAuNomDeLAutoriteRepresentee() {
        // Le cas remonté par le front : « Aucune autorité contractante liée à l'utilisateur ».
        ConventionDto creee = conventionService.create(nouvelleConvention(), relaisEnAutorite);

        Convention enBase = conventionRepository.findById(creee.getId()).orElseThrow();
        assertThat(enBase.getAutoriteContractante().getId()).isEqualTo(monAutoriteId);
        assertThat(enBase.getCreeParAutoriteContractante().getId())
                .as("la créatrice est l'autorité représentée, pas le compte relais")
                .isEqualTo(monAutoriteId);
    }

    @Test
    @Transactional
    void lAutoriteElleMemeCreeToujoursSaConvention() {
        // Le pendant : la correction ne doit rien changer pour un vrai compte d'autorité.
        AuthenticatedUser ac = new AuthenticatedUser(compteAc.getId(), compteAc.getUsername(),
                Role.AUTORITE_CONTRACTANTE);

        ConventionDto creee = conventionService.create(nouvelleConvention(), ac);

        assertThat(conventionRepository.findById(creee.getId()).orElseThrow()
                .getAutoriteContractante().getId()).isEqualTo(monAutoriteId);
    }

    @Test
    @Transactional
    void leRelaisCreeUnReferentielProjetAuNomDeLAutoriteRepresentee() {
        // Un référentiel exige une convention validée par la DGB.
        Convention validee = conventionRepository.saveAndFlush(Convention.builder()
                .reference("CONV-VALIDEE-" + System.nanoTime())
                .intitule("Convention validée")
                .statut(StatutConvention.VALIDE)
                .autoriteContractante(compteAc.getAutoriteContractante())
                .actif(true)
                .dateCreation(Instant.now())
                .build());
        CreateReferentielProjetRequest req = new CreateReferentielProjetRequest();
        req.setConventionId(validee.getId());
        req.setNomProjet("Projet créé en relais");

        ReferentielProjetDto cree = referentielProjetService.create(req, relaisEnAutorite);

        assertThat(cree.getAutoriteContractanteId()).isEqualTo(monAutoriteId);
    }

    // ── le marché : ni trop, ni trop peu ───────────────────────────────────────────────────────

    @Test
    @Transactional
    void leRelaisNeVoitQueLesMarchesDeLAutoriteRepresentee() {
        Marche etranger = marcheDUneAutreAutorite();

        List<Long> vus = marcheService.findAll(relaisEnAutorite).stream().map(MarcheDto::getId).toList();

        assertThat(vus).as("les marchés de l'autorité représentée").contains(monMarche().getId());
        assertThat(vus).as("ceux d'une autre autorité").doesNotContain(etranger.getId());
    }

    @Test
    @Transactional
    void leRelaisNeModifiePasLeMarcheDUneAutreAutorite() {
        Marche etranger = marcheDUneAutreAutorite();

        assertThatThrownBy(() -> marcheService.update(etranger.getId(),
                modification(etranger.getNumeroMarche()), relaisEnAutorite))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("hors périmètre");
    }

    @Test
    @Transactional
    void leRelaisModifieLesMarchesDeLAutoriteRepresentee() {
        Marche mien = monMarche();

        assertThatCode(() -> marcheService.update(mien.getId(),
                modification(mien.getNumeroMarche()), relaisEnAutorite))
                .doesNotThrowAnyException();
    }

    @Test
    @Transactional
    void leRelaisNeCreePasDeMarcheSurLaConventionDUneAutreAutorite() {
        Convention etrangere = marcheDUneAutreAutorite().getConvention();
        etrangere.setActif(true);
        conventionRepository.saveAndFlush(etrangere);

        CreateMarcheRequest req = CreateMarcheRequest.builder()
                .conventionId(etrangere.getId())
                .numeroMarche("M-RELAIS-" + System.nanoTime())
                .intitule("Marché hors périmètre")
                .montantContratHt(BigDecimal.valueOf(100_000))
                .statut(StatutMarche.EN_COURS)
                .build();

        assertThatThrownBy(() -> marcheService.create(req, relaisEnAutorite))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("hors périmètre");
    }

    // ── les délégués de l'autorité ─────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void leRelaisConsulteLesDeleguesDeLAutoriteRepresentee() {
        // Interdit jusqu'ici par une règle explicite « pendant une session d'impersonation ».
        assertThatCode(() -> delegueService.findMyDelegues(relaisEnAutorite))
                .doesNotThrowAnyException();
    }

    @Test
    @Transactional
    void leRelaisAccedeAuxAffectationsDesDelegues() {
        // Un identifiant de délégué inexistant : si le contrôle d'autorité passe, l'appel échoue
        // ensuite sur « délégué non trouvé ». S'il ne passe pas, il échoue avant, sur le rôle.
        assertThatThrownBy(() -> marcheService.findMarchesForDelegue(-1L, relaisEnAutorite))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Délégué non trouvé");
    }

    // ── les réclamations ───────────────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void leRelaisDeposeUneReclamationCommeLAutorite() {
        // Sans pièce jointe : l'appel doit franchir les contrôles de rôle et de périmètre, puis
        // s'arrêter sur la pièce manquante. Le rôle était relu en base — « commission relais ».
        DemandeCorrection demande = maDemande();

        assertThatThrownBy(() -> reclamationService.create(demande.getId(),
                "Réclamation déposée en relais", null, relaisEnAutorite))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("pièce jointe");
    }

    @Test
    @Transactional
    void leRelaisAnnuleUneReclamationDeposeeParLAutorite() {
        DemandeCorrection demande = maDemande();
        ReclamationDemandeCorrection rec = reclamationRepository.saveAndFlush(
                ReclamationDemandeCorrection.builder()
                        .demandeCorrection(demande)
                        .utilisateur(compteAc)
                        .texte("Réclamation de l'autorité")
                        .statut(StatutReclamationCorrection.SOUMISE)
                        .dateCreation(Instant.now())
                        .pieceJointeChemin("/api/local-files/reclamation-" + System.nanoTime() + ".pdf")
                        .pieceJointeNomFichier("reclamation.pdf")
                        .build());

        reclamationService.annuler(demande.getId(), rec.getId(), relaisEnAutorite);

        assertThat(reclamationRepository.findById(rec.getId()).orElseThrow().getStatut())
                .isEqualTo(StatutReclamationCorrection.ANNULEE);
    }

    // ── la même règle pour l'entreprise ────────────────────────────────────────────────────────

    @Test
    @Transactional
    void leRelaisEnEntrepriseAccedeAuxCertificatsDeCetteEntrepriseSeulement() {
        Utilisateur compteEntreprise = utilisateurRepository.findByUsername("entreprise").orElseThrow();
        Entreprise representee = compteEntreprise.getEntreprise();
        Entreprise autre = utilisateurRepository.findByUsername("test").orElseThrow().getEntreprise();
        AuthenticatedUser relaisEnEntreprise = new AuthenticatedUser(compteRelais.getId(),
                compteRelais.getUsername(), Role.ENTREPRISE, true, representee.getId(), null);

        CertificatCredit sien = certificatDe(representee);
        CertificatCredit etranger = certificatDe(autre);

        assertThat(certificatCreditService.canAccessCertificat(sien.getId(), relaisEnEntreprise))
                .as("le certificat de l'entreprise représentée").isTrue();
        assertThat(certificatCreditService.canAccessCertificat(etranger.getId(), relaisEnEntreprise))
                .as("celui d'une autre entreprise").isFalse();
    }

    // ── fixtures ───────────────────────────────────────────────────────────────────────────────

    private CreateConventionRequest nouvelleConvention() {
        CreateConventionRequest req = new CreateConventionRequest();
        req.setReference("CONV-RELAIS-" + System.nanoTime());
        req.setIntitule("Convention déposée en relais");
        return req;
    }

    private Marche monMarche() {
        return marcheRepository.findAll().stream()
                .filter(m -> m.getConvention() != null && m.getConvention().getAutoriteContractante() != null
                        && monAutoriteId.equals(m.getConvention().getAutoriteContractante().getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Aucun marché amorcé pour l'autorité"));
    }

    private DemandeCorrection maDemande() {
        return demandeRepository.findAll().stream()
                .filter(d -> d.getAutoriteContractante() != null
                        && monAutoriteId.equals(d.getAutoriteContractante().getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Aucune demande amorcée pour l'autorité"));
    }

    private Marche marcheDUneAutreAutorite() {
        AutoriteContractante autre = autoriteRepository.save(AutoriteContractante.builder()
                .nom("Autorité tierce du test relais")
                .code("AC-RELAIS-" + System.nanoTime())
                .build());
        Convention convention = conventionRepository.save(Convention.builder()
                .reference("CONV-TIERS-" + System.nanoTime())
                .intitule("Convention d'une autre autorité")
                .statut(StatutConvention.VALIDE)
                .autoriteContractante(autre)
                .actif(true)
                .dateCreation(Instant.now())
                .build());
        return marcheRepository.saveAndFlush(Marche.builder()
                .numeroMarche("MP-TIERS-" + System.nanoTime())
                .dateSignature(LocalDate.now())
                .montantContratHt(BigDecimal.valueOf(300_000))
                .statut(StatutMarche.EN_COURS)
                .convention(convention)
                .build());
    }

    private UpdateMarcheRequest modification(String numero) {
        return UpdateMarcheRequest.builder()
                .numeroMarche(numero)
                .intitule("Intitulé modifié en relais")
                .dateSignature(LocalDate.now())
                .montantContratHt(BigDecimal.valueOf(400_000))
                .statut(StatutMarche.EN_COURS)
                .build();
    }

    private CertificatCredit certificatDe(Entreprise entreprise) {
        return certificatRepository.saveAndFlush(CertificatCredit.builder()
                .numero("CI-RELAIS-" + System.nanoTime())
                .dateEmission(Instant.now())
                .statut(StatutCertificat.OUVERT)
                .entreprise(entreprise)
                .build());
    }
}
