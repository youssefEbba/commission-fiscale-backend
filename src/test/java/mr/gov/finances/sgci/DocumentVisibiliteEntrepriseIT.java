package mr.gov.finances.sgci;

import mr.gov.finances.sgci.domain.enums.ProcessusDocument;
import mr.gov.finances.sgci.domain.enums.SensDocument;
import mr.gov.finances.sgci.service.document.VisibiliteDocumentService;
import mr.gov.finances.sgci.domain.entity.CertificatCredit;
import mr.gov.finances.sgci.domain.entity.DocumentRequirement;
import mr.gov.finances.sgci.domain.entity.DocumentUtilisationCredit;
import mr.gov.finances.sgci.domain.entity.UtilisationDouaniere;
import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.domain.enums.ProcessusDocument;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.domain.enums.StatutUtilisation;
import mr.gov.finances.sgci.domain.enums.TypeUtilisation;
import mr.gov.finances.sgci.repository.CertificatCreditRepository;
import mr.gov.finances.sgci.repository.DocumentRequirementRepository;
import mr.gov.finances.sgci.repository.DocumentUtilisationCreditRepository;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.repository.UtilisationCreditRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.DocumentUtilisationCreditService;
import mr.gov.finances.sgci.service.UtilisationCreditService;
import mr.gov.finances.sgci.web.dto.DocumentUtilisationCreditDto;
import mr.gov.finances.sgci.web.dto.UtilisationCreditDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Les pièces produites par l'administration ne sont communiquées à l'entreprise qu'après liquidation.
 *
 * <p>Le test le plus important est {@link #toutCodeParametreEstClasse()} : une politique par liste
 * souffre de l'oubli, et c'est lui qui transforme « code oublié ⇒ pièce visible » en « code oublié ⇒
 * test rouge ». Les autres vérifient que le masquage tient sur chacun des chemins de lecture, car
 * il suffit d'en laisser un ouvert pour que tous les autres deviennent cosmétiques.
 */
@SpringBootTest
@ActiveProfiles("test")
class DocumentVisibiliteEntrepriseIT {

    private static final String DEMO_CERTIFICAT = "CI-DEMO-SCEN-E";

    @Autowired
    private UtilisationCreditService utilisationService;

    @Autowired
    private VisibiliteDocumentService visibilite;


    @Autowired
    private DocumentUtilisationCreditService documentService;

    @Autowired
    private UtilisationCreditRepository utilisationRepository;

    @Autowired
    private DocumentUtilisationCreditRepository documentRepository;

    @Autowired
    private DocumentRequirementRepository requirementRepository;

    @Autowired
    private CertificatCreditRepository certificatRepository;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    private AuthenticatedUser entreprise;
    private AuthenticatedUser dgd;

    @BeforeEach
    void setUpUsers() {
        // Les identifiants doivent être ceux des comptes amorcés : le contrôle de périmètre résout
        // l'entreprise depuis l'utilisateur en base, un id inventé le ferait échouer.
        Utilisateur ent = utilisateurRepository.findByUsername("entreprise").orElseThrow();
        entreprise = new AuthenticatedUser(ent.getId(), ent.getUsername(), ent.getRole());
        Utilisateur agentDgd = utilisateurRepository.findByUsername("dgd").orElseThrow();
        dgd = new AuthenticatedUser(agentDgd.getId(), agentDgd.getUsername(), agentDgd.getRole());
    }

    // ── le garde-fou contre l'oubli ────────────────────────────────────────────────────────────

    @Test
    void toutCodeParametreAUnSens() {
        // Le garde-fou a changé de nature : il portait sur deux ensembles écrits en dur, il porte
        // maintenant sur la donnée. Une pièce ajoutée au référentiel sans sens se comporte comme
        // une entrée — visible —, ce qui est le défaut le moins nuisible, mais ce test oblige à
        // trancher plutôt qu'à laisser le défaut décider.
        for (ProcessusDocument processus : List.of(ProcessusDocument.UTILISATION_CI_DOUANE,
                ProcessusDocument.UTILISATION_CI_TVA_INTERIEURE,
                ProcessusDocument.CORRECTION_OFFRE_FISCALE,
                ProcessusDocument.MISE_EN_PLACE_CI)) {
            for (DocumentRequirement req : requirementRepository
                    .findByProcessusOrderByOrdreAffichageAsc(processus)) {
                assertThat(req.sensEffectif())
                        .as("la pièce %s du processus %s doit porter un sens",
                                req.getCodeDocument(), processus)
                        .isNotNull();
            }
        }
    }

    @Test
    void lesPiecesProduitesParLaCommissionSontClasseesEnSortie() {
        // La reprise des trois classements en dur doit avoir pris effet au démarrage.
        assertSens(ProcessusDocument.UTILISATION_CI_DOUANE, "QUITTANCE_TRESOR", SensDocument.SORTIE);
        assertSens(ProcessusDocument.UTILISATION_CI_DOUANE, "BULLETIN_ANNOTE", SensDocument.SORTIE);
        assertSens(ProcessusDocument.UTILISATION_CI_DOUANE, "CERTIFICAT_UTILISATION", SensDocument.SORTIE);
        assertSens(ProcessusDocument.UTILISATION_CI_TVA_INTERIEURE, "QUITTANCE_DGI", SensDocument.SORTIE);
        assertSens(ProcessusDocument.CORRECTION_OFFRE_FISCALE, "LETTRE_ADOPTION", SensDocument.SORTIE);
        assertSens(ProcessusDocument.CORRECTION_OFFRE_FISCALE, "OFFRE_FISCALE_CORRIGEE", SensDocument.SORTIE);
        assertSens(ProcessusDocument.MISE_EN_PLACE_CI, "CERTIFICAT_CREDIT_IMPOTS", SensDocument.SORTIE);
    }

    @Test
    void lesPiecesDuDeposantRestentEnEntree() {
        assertSens(ProcessusDocument.UTILISATION_CI_DOUANE, "DECLARATION_DOUANE", SensDocument.ENTREE);
        assertSens(ProcessusDocument.UTILISATION_CI_DOUANE, "CHEQUE_CERTIFIE", SensDocument.ENTREE);
        assertSens(ProcessusDocument.CORRECTION_OFFRE_FISCALE, "OFFRE_FISCALE", SensDocument.ENTREE);
        // Le reçu de dépôt est dû au déposant immédiatement : le classer en sortie le lui
        // masquerait jusqu'à l'adoption, ce qui serait un recul.
        assertSens(ProcessusDocument.CORRECTION_OFFRE_FISCALE, "RECU_DEPOT", SensDocument.ENTREE);
    }

    private void assertSens(ProcessusDocument processus, String code, SensDocument attendu) {
        assertThat(requirementRepository.findByProcessusAndCodeDocument(processus, code))
                .as("la pièce %s doit être paramétrée pour %s", code, processus)
                .isPresent()
                .get()
                .extracting(DocumentRequirement::sensEffectif)
                .isEqualTo(attendu);
    }

    @Test
    void avantLiquidation_lentrepriseNeVoitPasLesPiecesDeLadministration() {
        assertThat(visibilite.visiblePour(ProcessusDocument.UTILISATION_CI_DOUANE,
                "BULLETIN_ANNOTE", StatutUtilisation.CHEQUE_SAISI, Role.ENTREPRISE)).isFalse();
        assertThat(visibilite.visiblePour(ProcessusDocument.UTILISATION_CI_DOUANE,
                "QUITTANCE_TRESOR", StatutUtilisation.ENVOYEE_AU_TRESOR, Role.ENTREPRISE)).isFalse();
        assertThat(visibilite.visiblePour(ProcessusDocument.UTILISATION_CI_DOUANE,
                "CERTIFICAT_UTILISATION", StatutUtilisation.CERTIFICAT_EMIS, Role.ENTREPRISE)).isFalse();
    }

    @Test
    void apresLiquidation_ellesDeviennentVisibles() {
        assertThat(visibilite.visiblePour(ProcessusDocument.UTILISATION_CI_DOUANE,
                "BULLETIN_ANNOTE", StatutUtilisation.LIQUIDEE, Role.ENTREPRISE)).isTrue();
        assertThat(visibilite.visiblePour(ProcessusDocument.UTILISATION_CI_DOUANE,
                "QUITTANCE_DGI", StatutUtilisation.APUREE, Role.ENTREPRISE)).isTrue();
    }

    @Test
    void aLaCloture_ellesLeRestent() {
        // Sans CLOTUREE dans la liste, les pièces se re-masqueraient à l'instant où l'entreprise
        // accuse réception — exactement le contraire de ce qu'on veut.
        assertThat(visibilite.visiblePour(ProcessusDocument.UTILISATION_CI_DOUANE,
                "CERTIFICAT_UTILISATION", StatutUtilisation.CLOTUREE, Role.ENTREPRISE)).isTrue();
    }

    @Test
    void lesPiecesDeLentrepriseSontToujoursVisibles() {
        assertThat(visibilite.visiblePour(ProcessusDocument.UTILISATION_CI_DOUANE,
                "CHEQUE_CERTIFIE", StatutUtilisation.CHEQUE_SAISI, Role.ENTREPRISE)).isTrue();
        assertThat(visibilite.visiblePour(ProcessusDocument.UTILISATION_CI_DOUANE,
                "FACTURE", StatutUtilisation.DEMANDEE, Role.ENTREPRISE)).isTrue();
    }

    @Test
    void lesAdministrationsVoientTout() {
        for (Role role : List.of(Role.DGD, Role.DGTCP, Role.DGI, Role.DGB, Role.PRESIDENT, Role.ADMIN_SI)) {
            assertThat(visibilite.visiblePour(ProcessusDocument.UTILISATION_CI_DOUANE,
                "BULLETIN_ANNOTE", StatutUtilisation.DEMANDEE, role))
                    .as("rôle %s", role).isTrue();
        }
    }

    @Test
    void unRejetNouvrePasLesPieces() {
        // Le motif d'un rejet est porté par la décision, pas par les papiers de travail.
        assertThat(visibilite.visiblePour(ProcessusDocument.UTILISATION_CI_DOUANE,
                "BULLETIN_ANNOTE", StatutUtilisation.REJETEE, Role.ENTREPRISE)).isFalse();
    }

    // ── les chemins de lecture ─────────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void listeDesDocuments_masqueeAvantLiquidation() {
        UtilisationDouaniere util = douaneAvecBulletin(StatutUtilisation.CHEQUE_SAISI);

        List<DocumentUtilisationCreditDto> vuEntreprise =
                documentService.findByUtilisationCreditId(util.getId(), entreprise);
        List<DocumentUtilisationCreditDto> vuDgd =
                documentService.findByUtilisationCreditId(util.getId(), dgd);

        assertThat(vuEntreprise).extracting(DocumentUtilisationCreditDto::getCodeDocument)
                .doesNotContain("BULLETIN_ANNOTE");
        assertThat(vuDgd).extracting(DocumentUtilisationCreditDto::getCodeDocument)
                .contains("BULLETIN_ANNOTE");
    }

    @Test
    @Transactional
    void listeDesDocuments_ouverteApresLiquidation() {
        UtilisationDouaniere util = douaneAvecBulletin(StatutUtilisation.LIQUIDEE);

        List<DocumentUtilisationCreditDto> vuEntreprise =
                documentService.findByUtilisationCreditId(util.getId(), entreprise);

        assertThat(vuEntreprise).extracting(DocumentUtilisationCreditDto::getCodeDocument)
                .contains("BULLETIN_ANNOTE");
    }

    @Test
    @Transactional
    void leDossierLuiMeme_neLivrePasLesJustificatifsDeQuittance() {
        UtilisationDouaniere util = douaneAvecBulletin(StatutUtilisation.ENVOYEE_AU_TRESOR);

        UtilisationCreditDto dto = utilisationService.findById(util.getId(), entreprise);

        // Le dossier reste lisible ; ce sont les justificatifs qui ne le sont pas encore.
        assertThat(dto).isNotNull();
        if (dto.getQuittances() != null) {
            assertThat(dto.getQuittances()).allSatisfy(q ->
                    assertThat(q.getDocumentChemin()).isNull());
        }
    }

    // ── fixtures ───────────────────────────────────────────────────────────────────────────────

    private UtilisationDouaniere douaneAvecBulletin(StatutUtilisation statut) {
        CertificatCredit cert = certificatRepository.findByNumero(DEMO_CERTIFICAT)
                .orElseThrow(() -> new IllegalStateException("Certificat seed absent: " + DEMO_CERTIFICAT));

        UtilisationDouaniere util = new UtilisationDouaniere();
        util.setType(TypeUtilisation.DOUANIER);
        util.setStatut(statut);
        util.setDateDemande(Instant.now());
        util.setMontant(BigDecimal.valueOf(1000));
        util.setCertificatCredit(cert);
        util.setEntreprise(cert.getEntreprise());
        util.setNumeroDeclaration("DECL-VIS-" + System.nanoTime());
        util.setNumeroBulletin("BUL-VIS-" + System.nanoTime());
        util = (UtilisationDouaniere) utilisationRepository.save(util);

        documentRepository.save(DocumentUtilisationCredit.builder()
                .codeDocument("BULLETIN_ANNOTE")
                .nomFichier("bulletin-annote.pdf")
                .chemin("/api/local-files/bulletin-" + System.nanoTime() + ".pdf")
                .dateUpload(Instant.now())
                .taille(2048L)
                .version(1)
                .actif(true)
                .utilisationCredit(util)
                .build());
        documentRepository.save(DocumentUtilisationCredit.builder()
                .codeDocument("FACTURE")
                .nomFichier("facture.pdf")
                .chemin("/api/local-files/facture-" + System.nanoTime() + ".pdf")
                .dateUpload(Instant.now())
                .taille(1024L)
                .version(1)
                .actif(true)
                .utilisationCredit(util)
                .build());
        return util;
    }
}
