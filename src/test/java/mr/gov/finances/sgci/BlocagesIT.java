package mr.gov.finances.sgci;

import mr.gov.finances.sgci.domain.entity.CertificatCredit;
import mr.gov.finances.sgci.domain.entity.DecisionCertificatCredit;
import mr.gov.finances.sgci.domain.entity.DecisionCorrection;
import mr.gov.finances.sgci.domain.entity.DemandeCorrection;
import mr.gov.finances.sgci.domain.entity.Entreprise;
import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.domain.enums.DecisionCorrectionType;
import mr.gov.finances.sgci.domain.enums.RejetTempStatus;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.domain.enums.StatutCertificat;
import mr.gov.finances.sgci.domain.enums.StatutDemande;
import mr.gov.finances.sgci.repository.CertificatCreditRepository;
import mr.gov.finances.sgci.repository.DecisionCertificatCreditRepository;
import mr.gov.finances.sgci.repository.DecisionCorrectionRepository;
import mr.gov.finances.sgci.repository.DemandeCorrectionRepository;
import mr.gov.finances.sgci.repository.EntrepriseRepository;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.reporting.BlocagesService;
import mr.gov.finances.sgci.web.dto.reporting.BlocagesDto;
import mr.gov.finances.sgci.web.dto.reporting.DelaiDirectionDto;
import mr.gov.finances.sgci.web.dto.reporting.DossierImmobileDto;
import mr.gov.finances.sgci.web.dto.reporting.RejetTempOuvertDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Où les dossiers sont arrêtés, et ce que mesurent vraiment les délais affichés.
 *
 * <p>Deux tests portent l'essentiel. {@link #unVisaParAdminNeCompteDansAucuneMoyenne()} : un visa
 * posé par un administrateur à la place d'une direction, fondu dans la moyenne, ferait mesurer
 * l'administrateur au lieu de la direction — et le chiffre servirait ensuite à juger cette
 * direction. {@link #lEtiquetteNePretendPasMesurerLAgeDansLeStatut()} : la définition doit voyager
 * avec le chiffre, parce que « immobile depuis 40 jours » et « dans ce statut depuis 40 jours » ne
 * sont pas la même affirmation, et que seule la première est calculable aujourd'hui.
 *
 * <p>Niveau service : Tomcat ne démarre pas sur ce poste.
 */
@SpringBootTest
@ActiveProfiles("test")
class BlocagesIT {

    private static final Instant DEBUT = Instant.now().minus(3650, ChronoUnit.DAYS);
    private static final Instant FIN = Instant.now().plus(1, ChronoUnit.DAYS);

    @Autowired
    private BlocagesService service;

    @Autowired
    private DemandeCorrectionRepository demandeRepository;

    @Autowired
    private CertificatCreditRepository certificatRepository;

    @Autowired
    private DecisionCorrectionRepository decisionCorrectionRepository;

    @Autowired
    private DecisionCertificatCreditRepository decisionCertificatRepository;

    @Autowired
    private EntrepriseRepository entrepriseRepository;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    private AuthenticatedUser president;
    private Utilisateur agent;
    private Entreprise titulaire;

    @BeforeEach
    void setUp() {
        agent = utilisateurRepository.findByUsername("dgd").orElseThrow();
        Utilisateur p = utilisateurRepository.findByUsername("president").orElseThrow();
        president = new AuthenticatedUser(p.getId(), p.getUsername(), Role.PRESIDENT);
        titulaire = entrepriseRepository.save(Entreprise.builder()
                .raisonSociale("Titulaire blocages " + System.nanoTime())
                .nif("NIF-BLOC-" + System.nanoTime())
                .build());
    }

    // ── les rejets temporaires ouverts ─────────────────────────────────────────────────────────

    @Test
    @Transactional
    void unRejetTempOuvertSurUneDemandeRemonte() {
        DemandeCorrection demande = uneDemande(StatutDemande.RECUE);
        decisionCorrectionRepository.saveAndFlush(DecisionCorrection.builder()
                .role(Role.DGD)
                .decision(DecisionCorrectionType.REJET_TEMP)
                .motifRejet("Attestation fiscale manquante")
                .dateDecision(Instant.now().minus(76, ChronoUnit.DAYS))
                .rejetTempStatus(RejetTempStatus.OUVERT)
                .utilisateur(agent)
                .demandeCorrection(demande)
                .build());

        BlocagesDto dto = service.blocages(president, DEBUT, FIN, null, null, null);

        assertThat(dto.getRejetsTempOuverts())
                .filteredOn(r -> "DemandeCorrection".equals(r.getType())
                        && demande.getId().equals(r.getDossierId()))
                .singleElement()
                .satisfies(r -> {
                    assertThat(r.getRole()).isEqualTo(Role.DGD.name());
                    // L'agent est atteint par une vraie clé étrangère, contrairement aux visas
                    // portés par la demande, qui ne gardent qu'un identifiant nu.
                    assertThat(r.getAgentUsername()).isEqualTo("dgd");
                    assertThat(r.getJoursOuvert()).isGreaterThanOrEqualTo(75L);
                    assertThat(r.getMotifRejet()).contains("Attestation fiscale");
                });
        assertThat(dto.getAnciennetePlusVieuxRejetJours()).isGreaterThanOrEqualTo(75L);
    }

    @Test
    @Transactional
    void unRejetTempResoluNeRemontePas() {
        // Le défaut de la colonne est RESOLU : « ouvert » ne doit jamais être déduit d'un null,
        // ni confondu avec « a été rejeté un jour ».
        DemandeCorrection demande = uneDemande(StatutDemande.RECUE);
        decisionCorrectionRepository.saveAndFlush(DecisionCorrection.builder()
                .role(Role.DGI)
                .decision(DecisionCorrectionType.REJET_TEMP)
                .motifRejet("Pièce reçue depuis")
                .dateDecision(Instant.now().minus(10, ChronoUnit.DAYS))
                .rejetTempStatus(RejetTempStatus.RESOLU)
                .rejetTempResolvedAt(Instant.now().minus(2, ChronoUnit.DAYS))
                .utilisateur(agent)
                .demandeCorrection(demande)
                .build());

        BlocagesDto dto = service.blocages(president, DEBUT, FIN, null, null, null);

        assertThat(dto.getRejetsTempOuverts())
                .filteredOn(r -> demande.getId().equals(r.getDossierId())
                        && Role.DGI.name().equals(r.getRole()))
                .isEmpty();
    }

    @Test
    @Transactional
    void unRejetTempOuvertSurUnCertificatRemonteAussi() {
        // Les quatre circuits doivent être couverts : un blocage sur le certificat arrête le
        // dossier tout autant qu'un blocage sur la demande.
        CertificatCredit certificat = unCertificat();
        decisionCertificatRepository.saveAndFlush(DecisionCertificatCredit.builder()
                .role(Role.DGTCP)
                .decision(DecisionCorrectionType.REJET_TEMP)
                .motifRejet("Contrat enregistré absent")
                .dateDecision(Instant.now().minus(20, ChronoUnit.DAYS))
                .rejetTempStatus(RejetTempStatus.OUVERT)
                .utilisateur(agent)
                .certificatCredit(certificat)
                .build());

        BlocagesDto dto = service.blocages(president, DEBUT, FIN, null, titulaire.getId(), null);

        assertThat(dto.getRejetsTempOuverts())
                .filteredOn(r -> "CertificatCredit".equals(r.getType()))
                .extracting(RejetTempOuvertDto::getDossierId)
                .contains(certificat.getId());
    }

    // ── les délais par direction ───────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void unVisaParAdminNeCompteDansAucuneMoyenne() {
        // Le fondre dans la moyenne ferait mesurer l'administrateur, et le chiffre servirait
        // ensuite à juger la direction.
        // Une demande neuve : le jeu de démonstration porte déjà un visa de chaque direction,
        // et les hériter empêcherait d'affirmer quoi que ce soit sur la moyenne.
        DemandeCorrection demande = demandeNeuve(Instant.now().minus(10, ChronoUnit.DAYS));
        decisionCorrectionRepository.saveAndFlush(visa(demande, Role.DGB, false,
                Instant.now().minus(6, ChronoUnit.DAYS)));
        decisionCorrectionRepository.saveAndFlush(visa(demande, Role.DGB, true,
                Instant.now().minus(1, ChronoUnit.DAYS)));

        BlocagesDto dto = service.blocages(president, DEBUT, FIN, null, titulaire.getId(), null);

        DelaiDirectionDto dgb = dto.getDelaisParDirection().stream()
                .filter(d -> Role.DGB.name().equals(d.getRole()) && "CORRECTION".equals(d.getCircuit()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Aucun délai pour la DGB ; "
                        + dto.getDelaisParDirection()));

        assertThat(dgb.getNbVisas()).isEqualTo(1);
        assertThat(dgb.getNbVisasParAdmin()).isEqualTo(1);
        // 4 jours du dépôt au visa de la direction ; le visa administrateur, à 9 jours, est exclu.
        assertThat(dgb.getDelaiMoyenJours()).isEqualTo(4.0);
        assertThat(dgb.getDefinitionDelai())
                .contains("Du dépôt du dossier au visa")
                .contains("comptés à part");
    }

    // ── l'étiquette ────────────────────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void lEtiquetteNePretendPasMesurerLAgeDansLeStatut() {
        BlocagesDto dto = service.blocages(president, DEBUT, FIN, null, null, null);

        assertThat(dto.getDefinitionAge())
                .contains("depuis la dernière décision enregistrée")
                .contains("Ce n'est pas l'ancienneté dans le statut actuel");
        assertThat(dto.getSeuilImmobiliteJours()).isEqualTo(BlocagesService.SEUIL_IMMOBILITE_JOURS);
    }

    @Test
    @Transactional
    void unDossierTermineNestPasImmobile() {
        // Un dossier adopté n'attend plus rien : le compter noierait ceux qui attendent vraiment.
        DemandeCorrection adoptee = uneDemande(StatutDemande.ADOPTEE);
        adoptee.setDateDepot(Instant.now().minus(400, ChronoUnit.DAYS));
        demandeRepository.saveAndFlush(adoptee);

        BlocagesDto dto = service.blocages(president, DEBUT, FIN, null, null, null);

        assertThat(dto.getDossiersImmobiles())
                .extracting(DossierImmobileDto::getId)
                .doesNotContain(adoptee.getId());
    }

    @Test
    @Transactional
    void lageEstCalculeDepuisLaDerniereDecisionQuandElleExiste() {
        DemandeCorrection demande = demandeNeuve(Instant.now().minus(300, ChronoUnit.DAYS));
        decisionCorrectionRepository.saveAndFlush(visa(demande, Role.DGD, false,
                Instant.now().minus(40, ChronoUnit.DAYS)));

        BlocagesDto dto = service.blocages(president, DEBUT, FIN, null, titulaire.getId(), null);

        List<DossierImmobileDto> lignes = dto.getDossiersImmobiles().stream()
                .filter(l -> demande.getId().equals(l.getId()))
                .toList();
        assertThat(lignes).singleElement().satisfies(l -> {
            // 40 jours, pas 300 : la décision prime sur la date de dépôt.
            assertThat(l.getJoursDepuisDerniereDecision()).isBetween(39L, 41L);
            assertThat(l.isDateDeRepli()).isFalse();
        });
    }

    // ── fixtures ───────────────────────────────────────────────────────────────────────────────

    private DecisionCorrection visa(DemandeCorrection demande, Role role, boolean parAdmin, Instant quand) {
        return DecisionCorrection.builder()
                .role(role)
                .decision(DecisionCorrectionType.VISA)
                .dateDecision(quand)
                .rejetTempStatus(RejetTempStatus.RESOLU)
                .visaParAdmin(parAdmin)
                .utilisateur(agent)
                .demandeCorrection(demande)
                .build();
    }

    /**
     * Une demande neuve, sans aucune décision, calquée sur le rattachement d'une existante.
     *
     * <p>Indispensable dès qu'un test mesure des décisions : le jeu de démonstration pose un visa
     * par direction sur ses demandes, et les hériter rendrait toute moyenne invérifiable.
     */
    private DemandeCorrection demandeNeuve(Instant dateDepot) {
        DemandeCorrection modele = demandeRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Aucune demande amorcée"));
        return demandeRepository.saveAndFlush(DemandeCorrection.builder()
                .numero("DC-BLOC-" + System.nanoTime())
                .statut(StatutDemande.EN_EVALUATION)
                .dateDepot(dateDepot)
                .autoriteContractante(modele.getAutoriteContractante())
                // L'entreprise du test, et non celle du modèle : l'agrégat des délais porte sur
                // tout le périmètre, donc l'isoler par l'entreprise est le seul moyen de pouvoir
                // affirmer un compte exact.
                .entreprise(titulaire)
                .convention(modele.getConvention())
                .build());
    }

    /** Une demande existante repositionnée, pour les tests qui ne mesurent pas de décision. */
    private DemandeCorrection uneDemande(StatutDemande statut) {
        DemandeCorrection demande = demandeRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Aucune demande amorcée"));
        demande.setStatut(statut);
        if (demande.getDateDepot() == null) {
            demande.setDateDepot(Instant.now().minus(60, ChronoUnit.DAYS));
        }
        return demandeRepository.saveAndFlush(demande);
    }

    private CertificatCredit unCertificat() {
        return certificatRepository.saveAndFlush(CertificatCredit.builder()
                .numero("CI-BLOC-" + System.nanoTime())
                .dateEmission(Instant.now().minus(30, ChronoUnit.DAYS))
                .statut(StatutCertificat.EN_CONTROLE)
                .entreprise(titulaire)
                .soldeCordon(BigDecimal.ZERO)
                .soldeTVA(BigDecimal.ZERO)
                .build());
    }
}
