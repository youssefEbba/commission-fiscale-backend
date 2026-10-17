package mr.gov.finances.sgci;

import mr.gov.finances.sgci.domain.entity.CertificatCredit;
import mr.gov.finances.sgci.domain.entity.Entreprise;
import mr.gov.finances.sgci.domain.entity.UtilisationDouaniere;
import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.domain.enums.StatutCertificat;
import mr.gov.finances.sgci.domain.enums.StatutUtilisation;
import mr.gov.finances.sgci.domain.enums.TypeUtilisation;
import mr.gov.finances.sgci.repository.CertificatCreditRepository;
import mr.gov.finances.sgci.repository.EntrepriseRepository;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.repository.UtilisationCreditRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.CertificatCreditService;
import mr.gov.finances.sgci.web.dto.AdminCorrectionCertificatRequest;
import mr.gov.finances.sgci.web.dto.UpdateCertificatCreditMontantsRequest;
import mr.gov.finances.sgci.web.exception.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Les montants et les soldes d'un crédit se figent quand ils sont engagés.
 *
 * <p>Quatre trous vérifiés dans le code tenaient la porte ouverte :
 *
 * <ul>
 *   <li>{@code updateMontants} n'avait <b>aucun</b> contrôle de statut, alors que son pendant
 *       administrateur en avait un. Les montants se réécrivaient en {@code CLOTURE}.</li>
 *   <li>La garde des soldes testait {@code != OUVERT}, ce qui laissait passer {@code MODIFIE} et
 *       {@code CLOTURE}.</li>
 *   <li>{@code applyRecapFromMontantsRequest} écrivait {@code tvaImportationDouane} — le quota TVA
 *       <em>restant</em> — avec la valeur <em>accordée</em>, à tout stade : une saisie de montants
 *       remettait au plafond un compteur déjà dépensé.</li>
 *   <li>{@code adminCorrectInfo} protégeait deux champs sur dix et était le seul chemin d'écriture
 *       du récapitulatif à ne pas vérifier sa cohérence.</li>
 * </ul>
 *
 * <p>Les tests « pendant » comptent autant que les tests de refus : tout verrouiller fermerait la
 * saisie normale de la DGTCP et rendrait l'override inutile, ce qui serait un défaut aussi réel que
 * celui qu'on corrige.
 *
 * <p>Niveau service : Tomcat ne démarre pas sur ce poste.
 */
@SpringBootTest
@ActiveProfiles("test")
class VerrousFinanciersIT {

    @Autowired
    private CertificatCreditService service;

    @Autowired
    private CertificatCreditRepository certificatRepository;

    @Autowired
    private UtilisationCreditRepository utilisationRepository;

    @Autowired
    private EntrepriseRepository entrepriseRepository;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    private AuthenticatedUser dgtcp;
    private AuthenticatedUser adminSi;
    private Entreprise titulaire;

    @BeforeEach
    void setUp() {
        dgtcp = compte(Role.DGTCP);
        adminSi = compte(Role.ADMIN_SI);
        titulaire = entrepriseRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Aucune entreprise amorcée"));
    }

    // ── les montants se figent à l'ouverture ───────────────────────────────────────────────────

    @Test
    @Transactional
    void lesMontantsNeSontPlusModifiablesDesLeCreditOuvert() {
        for (StatutCertificat statut : List.of(StatutCertificat.OUVERT, StatutCertificat.MODIFIE,
                StatutCertificat.CLOTURE)) {
            CertificatCredit c = certificat(statut);

            assertThatThrownBy(() -> service.updateMontants(c.getId(), saisieDeMontants(), dgtcp))
                    .as("la saisie des montants doit être refusée à l'état %s", statut)
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("ne sont plus modifiables");
        }
    }

    @Test
    @Transactional
    void leQuotaTvaConsommeNEstPasRemonteAuPlafond() {
        // Le cœur du lot. Le certificat a un plafond de 500 dont 300 ont été consommés : il reste
        // 200. Une saisie de montants redéclarant le plafond ne doit pas rendre les 300 dépensés.
        CertificatCredit c = certificat(StatutCertificat.OUVERT);
        c.setTvaImportationDouane(BigDecimal.valueOf(200));
        certificatRepository.saveAndFlush(c);

        assertThatThrownBy(() -> service.updateMontants(c.getId(), saisieDeMontants(), dgtcp))
                .isInstanceOf(ApiException.class);

        assertThat(certificatRepository.findById(c.getId()).orElseThrow().getTvaImportationDouane())
                .as("le quota restant ne doit pas avoir bougé")
                .isEqualByComparingTo(BigDecimal.valueOf(200));
    }

    @Test
    @Transactional
    void lesSoldesNeSontPasReecritsEnStatutModifieNiCloture() {
        // Les deux stades que la garde « != OUVERT » laissait passer.
        for (StatutCertificat statut : List.of(StatutCertificat.MODIFIE, StatutCertificat.CLOTURE)) {
            CertificatCredit c = certificat(statut);
            c.setSoldeCordon(BigDecimal.ZERO);
            c.setSoldeTVA(BigDecimal.ZERO);
            certificatRepository.saveAndFlush(c);

            assertThatThrownBy(() -> service.updateMontants(c.getId(), saisieDeMontants(), dgtcp))
                    .isInstanceOf(ApiException.class);

            CertificatCredit apres = certificatRepository.findById(c.getId()).orElseThrow();
            assertThat(apres.getSoldeCordon())
                    .as("un solde épuisé ne doit pas se recharger à l'état %s", statut)
                    .isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    // ── le pendant : la saisie normale doit rester possible ────────────────────────────────────

    @Test
    @Transactional
    void laDgtcpSaisitLesMontantsPendantLInstruction() {
        CertificatCredit c = certificat(StatutCertificat.EN_CONTROLE);

        assertThatCode(() -> service.updateMontants(c.getId(), saisieDeMontants(), dgtcp))
                .as("c'est l'étape normale de saisie : la fermer bloquerait l'instruction")
                .doesNotThrowAnyException();
    }

    @Test
    @Transactional
    void lesSoldesSeSynchronisentAvantLOuverture() {
        CertificatCredit c = certificat(StatutCertificat.EN_CONTROLE);
        c.setSoldeCordon(null);
        c.setSoldeTVA(null);
        certificatRepository.saveAndFlush(c);

        service.updateMontants(c.getId(), saisieDeMontants(), dgtcp);

        assertThat(certificatRepository.findById(c.getId()).orElseThrow().getSoldeCordon())
                .as("avant ouverture, les soldes provisoires se calculent encore")
                .isNotNull();
    }

    // ── la correction administrateur ───────────────────────────────────────────────────────────

    @Test
    @Transactional
    void laCorrectionAdministrateurNeTouchePasAuQuotaDejaEntame() {
        CertificatCredit c = certificat(StatutCertificat.OUVERT);
        uneUtilisationSur(c);

        AdminCorrectionCertificatRequest req = new AdminCorrectionCertificatRequest();
        req.setTvaImportationDouane(BigDecimal.valueOf(999));

        assertThatThrownBy(() -> service.adminCorrectInfo(c.getId(), req, "motif de test", adminSi))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("déjà entamé");
    }

    @Test
    @Transactional
    void laCorrectionAdministrateurRefuseUnRecapitulatifIncoherent() {
        CertificatCredit c = certificat(StatutCertificat.EN_CONTROLE);

        // montantCordon doit égaler b + c + d = 400 + 100 + 500 = 1000. On en déclare 7777.
        AdminCorrectionCertificatRequest req = new AdminCorrectionCertificatRequest();
        req.setMontantCordon(BigDecimal.valueOf(7777));

        assertThatThrownBy(() -> service.adminCorrectInfo(c.getId(), req, "motif de test", adminSi))
                .as("c'était le seul chemin d'écriture du récapitulatif à ne pas se vérifier")
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Récapitulatif incohérent");
    }

    @Test
    @Transactional
    void uneCorrectionPurementDescriptiveResteAutoriseeMemeSurUnCreditCloture() {
        // Le pendant : un override qui ne corrige plus rien est un défaut, au même titre qu'un
        // override qui casse les soldes. Une date de validité n'a aucun effet financier.
        CertificatCredit c = certificat(StatutCertificat.CLOTURE);
        uneUtilisationSur(c);

        AdminCorrectionCertificatRequest req = new AdminCorrectionCertificatRequest();
        req.setDateValidite(Instant.now().plusSeconds(86_400));

        assertThatCode(() -> service.adminCorrectInfo(c.getId(), req, "motif de test", adminSi))
                .doesNotThrowAnyException();
    }

    // ── la porte dérobée du statut ─────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void aucunActeurNeDeclareUnCreditModifieALaMain() {
        // MODIFIE relève du circuit d'avenant, qui n'existe pas. La route de statut y menait, et
        // elle est ouverte aux trois rôles d'autorité contractante.
        CertificatCredit c = certificat(StatutCertificat.OUVERT);

        assertThatThrownBy(() -> service.updateStatut(
                c.getId(), StatutCertificat.MODIFIE, dgtcp))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("circuit d'avenant");
    }

    @Test
    @Transactional
    void laClotureNePasseParLaRouteDeStatut() {
        CertificatCredit c = certificat(StatutCertificat.OUVERT);

        assertThatThrownBy(() -> service.updateStatut(
                c.getId(), StatutCertificat.CLOTURE, dgtcp))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("circuit");
    }

    // ── le commit réel ─────────────────────────────────────────────────────────────────────────

    /**
     * Sans {@code @Transactional}, délibérément.
     *
     * <p>Un test transactionnel ne commet jamais : il est structurellement aveugle aux défauts de
     * type « transaction marquée rollback-only ». Un défaut de ce genre a déjà traversé dix tests
     * verts dans ce projet. Ici le refus doit laisser la transaction saine et la suivante
     * fonctionner.
     */
    @Test
    void lesVerrousTiennentEnCommitReel() {
        CertificatCredit ferme = certificat(StatutCertificat.CLOTURE);
        CertificatCredit ouvert = certificat(StatutCertificat.EN_CONTROLE);
        try {
            assertThatThrownBy(() -> service.updateMontants(ferme.getId(), saisieDeMontants(), dgtcp))
                    .isInstanceOf(ApiException.class);

            assertThatCode(() -> service.updateMontants(ouvert.getId(), saisieDeMontants(), dgtcp))
                    .as("un refus ne doit pas empoisonner l'appel suivant")
                    .doesNotThrowAnyException();
        } finally {
            certificatRepository.deleteById(ferme.getId());
            certificatRepository.deleteById(ouvert.getId());
        }
    }

    // ── fixtures ───────────────────────────────────────────────────────────────────────────────

    private AuthenticatedUser compte(Role role) {
        Utilisateur u = utilisateurRepository.findAll().stream()
                .filter(x -> x.getRole() == role).findFirst()
                .orElseThrow(() -> new IllegalStateException("Aucun compte " + role + " amorcé"));
        return new AuthenticatedUser(u.getId(), u.getUsername(), role);
    }

    /** Un certificat au récapitulatif cohérent : montantCordon = 400 + 100 + 500. */
    private CertificatCredit certificat(StatutCertificat statut) {
        return certificatRepository.saveAndFlush(CertificatCredit.builder()
                .numero("CI-VERROU-" + System.nanoTime())
                .dateEmission(Instant.now())
                .statut(statut)
                .entreprise(titulaire)
                .montantCordon(BigDecimal.valueOf(1000))
                .droitsEtTaxesDouaneHorsTva(BigDecimal.valueOf(400))
                .taxesConsommation(BigDecimal.valueOf(100))
                .tvaImportationDouaneAccordee(BigDecimal.valueOf(500))
                .tvaImportationDouane(BigDecimal.valueOf(500))
                .montantTVAInterieure(BigDecimal.valueOf(200))
                .soldeCordon(BigDecimal.valueOf(500))
                .soldeTVA(BigDecimal.valueOf(200))
                .build());
    }

    /** La même saisie que celle du certificat : cohérente, donc jamais rejetée pour ce motif. */
    private UpdateCertificatCreditMontantsRequest saisieDeMontants() {
        UpdateCertificatCreditMontantsRequest req = new UpdateCertificatCreditMontantsRequest();
        req.setMontantCordon(BigDecimal.valueOf(1000));
        req.setMontantTVAInterieure(BigDecimal.valueOf(200));
        req.setDroitsEtTaxesDouaneHorsTva(BigDecimal.valueOf(400));
        req.setTaxesConsommation(BigDecimal.valueOf(100));
        req.setTvaImportationDouane(BigDecimal.valueOf(500));
        return req;
    }

    private void uneUtilisationSur(CertificatCredit c) {
        UtilisationDouaniere util = new UtilisationDouaniere();
        util.setType(TypeUtilisation.DOUANIER);
        util.setStatut(StatutUtilisation.DEMANDEE);
        util.setDateDemande(Instant.now());
        util.setMontant(BigDecimal.valueOf(100));
        util.setCertificatCredit(c);
        util.setEntreprise(titulaire);
        util.setNumeroDeclaration("DECL-VERROU-" + System.nanoTime());
        util.setNumeroBulletin("BUL-VERROU-" + System.nanoTime());
        utilisationRepository.saveAndFlush(util);
    }
}
