package mr.gov.finances.sgci;

import mr.gov.finances.sgci.domain.entity.AutoriteContractante;
import mr.gov.finances.sgci.domain.entity.CertificatCredit;
import mr.gov.finances.sgci.domain.entity.Convention;
import mr.gov.finances.sgci.domain.entity.DemandeCorrection;
import mr.gov.finances.sgci.domain.entity.Entreprise;
import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.domain.enums.StatutCertificat;
import mr.gov.finances.sgci.domain.enums.StatutConvention;
import mr.gov.finances.sgci.repository.AutoriteContractanteRepository;
import mr.gov.finances.sgci.repository.CertificatCreditRepository;
import mr.gov.finances.sgci.repository.ConventionRepository;
import mr.gov.finances.sgci.repository.DemandeCorrectionRepository;
import mr.gov.finances.sgci.repository.EntrepriseRepository;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.reporting.AFermerService;
import mr.gov.finances.sgci.web.dto.reporting.AFermerDto;
import mr.gov.finances.sgci.web.dto.reporting.LigneAFermerDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ce qu'il faut fermer, et les trois pièges qui produisent chacun un chiffre faux crédible.
 *
 * <p>{@link #lesConventionsAEcheanceSontFiltreesEnDateCivile()} est celui qui attrape le défaut le
 * plus sournois : {@code Convention.dateFin} est un {@code LocalDate} alors que la fenêtre de
 * reporting est en {@code Instant}, et lier l'un contre l'autre produit une comparaison qui a l'air
 * de fonctionner tout en renvoyant les mauvaises lignes.
 *
 * <p>Chaque test travaille sur sa propre entreprise et filtre dessus : les chiffres ne contiennent
 * rien du jeu de démonstration.
 *
 * <p>Niveau service : Tomcat ne démarre pas sur ce poste.
 */
@SpringBootTest
@ActiveProfiles("test")
class AFermerIT {

    private static final Instant DEBUT = Instant.now().minus(30, ChronoUnit.DAYS);
    private static final Instant FIN = Instant.now().plus(1, ChronoUnit.DAYS);

    @Autowired
    private AFermerService service;

    @Autowired
    private CertificatCreditRepository certificatRepository;

    @Autowired
    private DemandeCorrectionRepository demandeRepository;

    @Autowired
    private ConventionRepository conventionRepository;

    @Autowired
    private AutoriteContractanteRepository autoriteRepository;

    @Autowired
    private EntrepriseRepository entrepriseRepository;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    private AuthenticatedUser president;
    private Entreprise titulaire;

    @BeforeEach
    void setUp() {
        Utilisateur u = utilisateurRepository.findByUsername("president").orElseThrow();
        president = new AuthenticatedUser(u.getId(), u.getUsername(), Role.PRESIDENT);
        titulaire = entrepriseRepository.save(Entreprise.builder()
                .raisonSociale("Titulaire à fermer " + System.nanoTime())
                .nif("NIF-FERM-" + System.nanoTime())
                .build());
    }

    // ── les crédits échus ──────────────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void unCreditEchuAvecSoldeRemonte() {
        CertificatCredit c = certificat(Instant.now().minus(10, ChronoUnit.DAYS),
                BigDecimal.valueOf(100), BigDecimal.valueOf(50));

        AFermerDto dto = aFermer();

        assertThat(dto.getCertificatsExpiresAvecSolde())
                .extracting(LigneAFermerDto::getId)
                .contains(c.getId());
        // Le montant immobilisé est ce qui justifie de s'en occuper : 100 + 50.
        assertThat(dto.getMontantImmobiliseExpires()).isEqualByComparingTo(BigDecimal.valueOf(150));
        assertThat(dto.getCertificatsExpiresAvecSolde().stream()
                .filter(l -> c.getId().equals(l.getId()))
                .findFirst().orElseThrow().getJoursAvantEcheance()).isNegative();
    }

    @Test
    @Transactional
    void unCreditEchuEntierementSoldeNeRemontePas() {
        // Sans ce test, la liste se remplirait de dossiers qui n'ont plus rien à solder.
        CertificatCredit c = certificat(Instant.now().minus(10, ChronoUnit.DAYS),
                BigDecimal.ZERO, BigDecimal.ZERO);

        assertThat(aFermer().getCertificatsExpiresAvecSolde())
                .extracting(LigneAFermerDto::getId)
                .doesNotContain(c.getId());
    }

    @Test
    @Transactional
    void unCertificatSansDateDeValiditeApparaitDansSonPropreSeau() {
        // La colonne est nullable, et un certificat sans date n'échoit jamais : il ne sortirait
        // d'aucun des deux autres seaux, et passerait donc totalement inaperçu.
        CertificatCredit c = certificat(null, BigDecimal.valueOf(100), BigDecimal.ZERO);

        AFermerDto dto = aFermer();

        assertThat(dto.getCertificatsSansDateValidite())
                .extracting(LigneAFermerDto::getId)
                .contains(c.getId());
        assertThat(dto.getCertificatsExpiresAvecSolde())
                .extracting(LigneAFermerDto::getId)
                .doesNotContain(c.getId());
        assertThat(dto.getCertificatsExpirantProchainement())
                .extracting(LigneAFermerDto::getId)
                .doesNotContain(c.getId());
    }

    // ── le piège de type ───────────────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void lesConventionsAEcheanceSontFiltreesEnDateCivile() {
        // dateFin est un LocalDate ; la fenêtre du reporting est en Instant. Lier l'un contre
        // l'autre renvoie silencieusement les mauvaises lignes.
        AutoriteContractante autorite = autoriteRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Aucune autorité contractante amorcée"));
        Convention proche = convention(autorite, LocalDate.now().plusDays(15));
        Convention lointaine = convention(autorite, LocalDate.now().plusDays(400));

        AFermerDto dto = service.aFermer(president, DEBUT, FIN, autorite.getId(), null, 60, null);

        assertThat(dto.getConventionsAEcheance())
                .extracting(LigneAFermerDto::getId)
                .contains(proche.getId())
                .doesNotContain(lointaine.getId());
        assertThat(dto.getConventionsAEcheance().stream()
                .filter(l -> proche.getId().equals(l.getId()))
                .findFirst().orElseThrow().getJoursAvantEcheance()).isEqualTo(15L);
    }

    // ── le dénominateur des équipements ────────────────────────────────────────────────────────

    @Test
    @Transactional
    void leRegimeNonRenseigneEstCompteAPart() {
        // « Trois dossiers sous admission temporaire » se lit comme un inventaire complet si l'on ne
        // dit pas combien de dossiers n'ont jamais reçu la question. Le dénominateur sera élevé au
        // début, et c'est l'information juste : le champ est neuf.
        DemandeCorrection sansReponse = demandeRepository.findAll().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Aucune demande amorcée"));
        sansReponse.setAdmissionTemporaire(null);
        demandeRepository.saveAndFlush(sansReponse);

        AFermerDto dto = service.aFermer(president, DEBUT, FIN, null, null, null, null);

        assertThat(dto.getEquipementsNonRenseignes()).isPositive();
    }

    @Test
    @Transactional
    void laListeSuitLeRegimeEtNonLaSimplePresenceDEquipements() {
        // Le critère a été déplacé : c'est l'admission temporaire qui crée une obligation de
        // réexportation, donc un risque à la clôture. Du matériel importé définitivement n'a rien à
        // ressortir, et encombrait la liste de dossiers sans enjeu.
        DemandeCorrection dossier = demandeRepository.findAll().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Aucune demande amorcée"));
        dossier.setEquipementsPresents(Boolean.TRUE);
        dossier.setAdmissionTemporaire(Boolean.FALSE);
        demandeRepository.saveAndFlush(dossier);

        AFermerDto dto = service.aFermer(president, DEBUT, FIN, null, null, null, null);

        assertThat(dto.getEquipementsSurCreditOuvert())
                .as("du matériel importé définitivement ne doit pas remonter")
                .noneSatisfy(l -> assertThat(l.getReference()).isEqualTo(dossier.getReference()));
    }

    // ── la définition voyage avec le chiffre ───────────────────────────────────────────────────

    @Test
    @Transactional
    void laDefinitionDeLaDormanceEstPubliee() {
        // Aucune donnée ne porte la dormance : le critère est un choix, et il doit être lisible par
        // qui reçoit le chiffre, sinon la Commission hérite d'un seuil qu'elle n'a pas validé.
        AFermerDto dto = service.aFermer(president, DEBUT, FIN, null, null, null, null);

        assertThat(dto.getSeuilDormanceJours()).isEqualTo(AFermerService.SEUIL_DORMANCE_JOURS);
        assertThat(dto.getDefinitionDormance())
                .contains("180")
                .contains("Aucune donnée ne porte la notion de dormance");
    }

    @Test
    @Transactional
    void leSeuilDeDormanceEstParametrable() {
        AFermerDto dto = service.aFermer(president, DEBUT, FIN, null, null, null, 90);

        assertThat(dto.getSeuilDormanceJours()).isEqualTo(90);
        assertThat(dto.getDefinitionDormance()).contains("90");
    }

    // ── fixtures ───────────────────────────────────────────────────────────────────────────────

    private AFermerDto aFermer() {
        return service.aFermer(president, DEBUT, FIN, null, titulaire.getId(), null, null);
    }

    private CertificatCredit certificat(Instant dateValidite, BigDecimal soldeCordon, BigDecimal soldeTva) {
        return certificatRepository.saveAndFlush(CertificatCredit.builder()
                .numero("CI-FERM-" + System.nanoTime())
                .dateEmission(Instant.now().minus(5, ChronoUnit.DAYS))
                .dateValidite(dateValidite)
                .statut(StatutCertificat.OUVERT)
                .entreprise(titulaire)
                .soldeCordon(soldeCordon)
                .soldeTVA(soldeTva)
                .tvaImportationDouane(BigDecimal.ZERO)
                .build());
    }

    private Convention convention(AutoriteContractante autorite, LocalDate dateFin) {
        return conventionRepository.saveAndFlush(Convention.builder()
                .reference("CONV-FERM-" + System.nanoTime())
                .intitule("Convention à échéance")
                .statut(StatutConvention.VALIDE)
                .autoriteContractante(autorite)
                .dateFin(dateFin)
                .actif(true)
                .dateCreation(Instant.now())
                .build());
    }
}
