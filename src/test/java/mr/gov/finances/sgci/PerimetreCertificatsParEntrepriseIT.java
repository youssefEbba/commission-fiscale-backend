package mr.gov.finances.sgci;

import mr.gov.finances.sgci.domain.entity.AutoriteContractante;
import mr.gov.finances.sgci.domain.entity.Bailleur;
import mr.gov.finances.sgci.domain.entity.CertificatCredit;
import mr.gov.finances.sgci.domain.entity.Convention;
import mr.gov.finances.sgci.domain.entity.DemandeCorrection;
import mr.gov.finances.sgci.domain.entity.Entreprise;
import mr.gov.finances.sgci.domain.entity.Marche;
import mr.gov.finances.sgci.domain.entity.MarcheDelegue;
import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.domain.enums.StatutCertificat;
import mr.gov.finances.sgci.domain.enums.StatutConvention;
import mr.gov.finances.sgci.domain.enums.StatutDemande;
import mr.gov.finances.sgci.domain.enums.StatutMarche;
import mr.gov.finances.sgci.repository.AutoriteContractanteRepository;
import mr.gov.finances.sgci.repository.BailleurRepository;
import mr.gov.finances.sgci.repository.CertificatCreditRepository;
import mr.gov.finances.sgci.repository.ConventionRepository;
import mr.gov.finances.sgci.repository.DemandeCorrectionRepository;
import mr.gov.finances.sgci.repository.EntrepriseRepository;
import mr.gov.finances.sgci.repository.MarcheRepository;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.CertificatCreditService;
import mr.gov.finances.sgci.web.dto.CertificatCreditDto;
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
 * La liste des certificats de crédit « par entreprise » applique le périmètre de l'appelant.
 *
 * <p>{@code CertificatCreditService.findByEntreprise} ne recevait même pas l'utilisateur. Sa route
 * accepte {@code mise_en_place.entreprise.queue.view}, que détient le rôle ENTREPRISE, et
 * {@code mise_en_place.view}, que détiennent l'autorité contractante et ses délégués : une entreprise
 * lisait, en changeant l'identifiant, les certificats d'une autre — montants accordés et soldes
 * compris ; une autorité, ceux nés des demandes des autres autorités.
 *
 * <p>Niveau service : Tomcat ne démarre pas sur ce poste.
 */
@SpringBootTest
@ActiveProfiles("test")
class PerimetreCertificatsParEntrepriseIT {

    @Autowired private CertificatCreditService service;
    @Autowired private CertificatCreditRepository certificatRepository;
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
    /** Délégué de l'autre autorité, sur le marché du certificat étranger. */
    private AuthenticatedUser delegueEtranger;

    private Long entrepriseId;
    private Long autreEntrepriseId;

    /** Ni l'autorité ni l'entreprise du seed : seule une direction doit le voir. */
    private CertificatCredit certificatEtranger;
    /** L'entreprise du seed, né d'une demande d'une autre autorité. */
    private CertificatCredit certificatCroise;
    /** L'autorité et l'entreprise du seed : le témoin. */
    private CertificatCredit certificatPropre;

    @BeforeEach
    void setUp() {
        Utilisateur compteEntreprise = utilisateurRepository.findByUsername("entreprise").orElseThrow();
        entreprise = compte(compteEntreprise);
        Entreprise entrepriseDuSeed = compteEntreprise.getEntreprise();
        entrepriseId = entrepriseDuSeed.getId();

        Utilisateur compteAc = utilisateurRepository.findByUsername("ac").orElseThrow();
        ac = compte(compteAc);
        AutoriteContractante autoriteDuSeed = compteAc.getAutoriteContractante();

        dgtcp = compte(utilisateurRepository.findByUsername("dgtcp").orElseThrow());

        Utilisateur compteRelais = utilisateurRepository.findByUsername("commission_relais").orElseThrow();
        relaisPourEntreprise = new AuthenticatedUser(compteRelais.getId(), compteRelais.getUsername(),
                Role.ENTREPRISE, true, entrepriseId, null);

        AutoriteContractante autreAutorite = autoriteRepository.save(AutoriteContractante.builder()
                .nom("Autorité témoin du périmètre des certificats")
                .code("AC-CERTIFS-" + System.nanoTime())
                .contact("temoin@test.local")
                .build());
        Entreprise autreEntreprise = entrepriseRepository.save(Entreprise.builder()
                .raisonSociale("Entreprise témoin du périmètre des certificats")
                .nif("NIF-CERTIFS-" + System.nanoTime())
                .build());
        autreEntrepriseId = autreEntreprise.getId();

        DemandeCorrection demandeEtrangere = demande(autreAutorite, autreEntreprise);
        certificatEtranger = certificat(demandeEtrangere);
        certificatCroise = certificat(demande(autreAutorite, entrepriseDuSeed));
        certificatPropre = certificat(demande(autoriteDuSeed, entrepriseDuSeed));

        delegueEtranger = delegueSurLeMarcheDe(demandeEtrangere, autreAutorite);
    }

    // ── la fuite ───────────────────────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void uneEntrepriseNeListePasLesCertificatsDUneAutre() {
        assertThat(service.findByEntreprise(autreEntrepriseId, entreprise)).isEmpty();
    }

    @Test
    @Transactional
    void leRelaisNeSortPasDeLEntrepriseRepresentee() {
        assertThat(service.findByEntreprise(autreEntrepriseId, relaisPourEntreprise)).isEmpty();
        assertThat(ids(service.findByEntreprise(entrepriseId, relaisPourEntreprise)))
                .contains(certificatCroise.getId(), certificatPropre.getId());
    }

    @Test
    @Transactional
    void uneAutoriteNeVoitQueLesCertificatsDeSesDemandes() {
        assertThat(ids(service.findByEntreprise(entrepriseId, ac)))
                .doesNotContain(certificatCroise.getId())
                .contains(certificatPropre.getId());
        assertThat(service.findByEntreprise(autreEntrepriseId, ac)).isEmpty();
    }

    @Test
    @Transactional
    void unDelegueNeVoitQueLesCertificatsDeSesMarches() {
        assertThat(service.findByEntreprise(entrepriseId, delegueEtranger)).isEmpty();
        assertThat(ids(service.findByEntreprise(autreEntrepriseId, delegueEtranger)))
                .containsExactly(certificatEtranger.getId());
    }

    // ── le pendant : sans lui, tout fermer passerait pour une réussite ──────────────────────────

    @Test
    @Transactional
    void uneEntrepriseListeTousSesCertificats() {
        // Crédits, mise en place, transferts, sous-traitance et utilisations appellent cette route
        // avec l'entreprise du compte : elle y retrouve aussi les certificats nés chez une autre autorité.
        assertThat(ids(service.findByEntreprise(entrepriseId, entreprise)))
                .contains(certificatCroise.getId(), certificatPropre.getId());
    }

    @Test
    @Transactional
    void uneDirectionVoitTousLesCertificatsDeLEntreprise() {
        assertThat(ids(service.findByEntreprise(autreEntrepriseId, dgtcp)))
                .containsExactly(certificatEtranger.getId());
        assertThat(ids(service.findByEntreprise(entrepriseId, dgtcp)))
                .contains(certificatCroise.getId(), certificatPropre.getId());
    }

    // ── fixtures ───────────────────────────────────────────────────────────────────────────────

    private static List<Long> ids(List<CertificatCreditDto> dtos) {
        return dtos.stream().map(CertificatCreditDto::getId).toList();
    }

    private static AuthenticatedUser compte(Utilisateur u) {
        return new AuthenticatedUser(u.getId(), u.getUsername(), u.getRole());
    }

    private DemandeCorrection demande(AutoriteContractante autorite, Entreprise titulaire) {
        Bailleur bailleur = bailleurRepository.findAll().stream().findFirst()
                .orElseGet(() -> bailleurRepository.save(Bailleur.builder().nom("Bailleur témoin").build()));
        Convention convention = conventionRepository.save(Convention.builder()
                .reference("CONV-CERTIFS-" + System.nanoTime())
                .intitule("Convention témoin")
                .bailleur(bailleur)
                .autoriteContractante(autorite)
                .statut(StatutConvention.EN_ATTENTE)
                .build());
        return demandeRepository.saveAndFlush(DemandeCorrection.builder()
                .numero("DC-CERTIFS-" + System.nanoTime())
                .dateDepot(Instant.now())
                .statut(StatutDemande.ADOPTEE)
                .autoriteContractante(autorite)
                .entreprise(titulaire)
                .convention(convention)
                .build());
    }

    private CertificatCredit certificat(DemandeCorrection demande) {
        return certificatRepository.saveAndFlush(CertificatCredit.builder()
                .numero("CI-CERTIFS-" + System.nanoTime())
                .dateEmission(Instant.now())
                .statut(StatutCertificat.OUVERT)
                .entreprise(demande.getEntreprise())
                .demandeCorrection(demande)
                .build());
    }

    /** Un délégué UPM de cette autorité, sur un marché rattaché à la demande. */
    private AuthenticatedUser delegueSurLeMarcheDe(DemandeCorrection demande, AutoriteContractante autorite) {
        Utilisateur delegue = utilisateurRepository.save(Utilisateur.builder()
                .username("delegue-certifs-" + System.nanoTime())
                .passwordHash("non-utilise")
                .role(Role.AUTORITE_UPM)
                .autoriteContractante(autorite)
                .nomComplet("Délégué témoin du périmètre des certificats")
                .build());
        Marche marche = Marche.builder()
                .numeroMarche("MP-CERTIFS-" + System.nanoTime())
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
