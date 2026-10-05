package mr.gov.finances.sgci;

import mr.gov.finances.sgci.domain.entity.CertificatCredit;
import mr.gov.finances.sgci.domain.entity.LigneBulletinLiquidation;
import mr.gov.finances.sgci.domain.entity.UtilisationDouaniere;
import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.domain.enums.AffectationTaxe;
import mr.gov.finances.sgci.domain.enums.DecisionCorrectionType;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.domain.enums.StatutUtilisation;
import mr.gov.finances.sgci.domain.enums.TypeLigneTaxe;
import mr.gov.finances.sgci.domain.enums.TypeUtilisation;
import mr.gov.finances.sgci.repository.CertificatCreditRepository;
import mr.gov.finances.sgci.repository.LigneBulletinLiquidationRepository;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.repository.UtilisationCreditRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.DecisionUtilisationCreditService;
import mr.gov.finances.sgci.service.UtilisationCreditService;
import mr.gov.finances.sgci.web.dto.CreateUtilisationCreditRequest;
import mr.gov.finances.sgci.web.exception.ApiErrorCode;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * L'entreprise corrige sa demande pendant un rejet temporaire.
 *
 * <p>Le test qui compte est {@link #modifierLesLignes_invalideLesAgregats()} : réécrire les lignes
 * détruit l'annotation de la DGD, donc les totaux calculés à partir d'elles deviennent faux. On les
 * invalide, et c'est cela — et non un contrôle supplémentaire — qui empêche le dossier d'avancer.
 */
@SpringBootTest
@ActiveProfiles("test")
class UtilisationEditionRejetTempIT {

    private static final String DEMO_CERTIFICAT = "CI-DEMO-SCEN-E";

    @Autowired
    private UtilisationCreditService service;

    @Autowired
    private DecisionUtilisationCreditService decisionService;

    @Autowired
    private UtilisationCreditRepository utilisationRepository;

    @Autowired
    private LigneBulletinLiquidationRepository ligneRepository;

    @Autowired
    private CertificatCreditRepository certificatRepository;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    private AuthenticatedUser entreprise;
    private AuthenticatedUser dgd;
    private CertificatCredit certificatDemo;

    @BeforeEach
    void setUpUsers() {
        Utilisateur ent = utilisateurRepository.findByUsername("entreprise").orElseThrow();
        entreprise = new AuthenticatedUser(ent.getId(), ent.getUsername(), ent.getRole());
        certificatDemo = certificatRepository.findByNumero(DEMO_CERTIFICAT).orElseThrow();
        Utilisateur agentDgd = utilisateurRepository.findByUsername("dgd").orElseThrow();
        dgd = new AuthenticatedUser(agentDgd.getId(), agentDgd.getUsername(), agentDgd.getRole());
    }

    // ── l'édition s'ouvre, mais pas plus grand qu'il ne faut ───────────────────────────────────

    @Test
    @Transactional
    void sousRejetOuvert_editionAcceptee() {
        UtilisationDouaniere util = douaneVisee();
        rejetTemporaire(util);

        service.update(util.getId(), requete(BigDecimal.valueOf(700)), entreprise);

        assertThat(utilisationRepository.findById(util.getId()).orElseThrow().getMontant())
                .isEqualByComparingTo(BigDecimal.valueOf(700));
    }

    @Test
    @Transactional
    void sansRejetOuvert_editionToujoursRefusee() {
        UtilisationDouaniere util = douaneVisee();

        assertThatThrownBy(() -> service.update(util.getId(), requete(BigDecimal.valueOf(700)), entreprise))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode())
                        .isEqualTo(ApiErrorCode.DEMANDE_NON_EDITABLE));
    }

    // ── le cœur : les agrégats deviennent nuls, le dossier ne peut plus avancer ────────────────

    @Test
    @Transactional
    void modifierLesLignes_invalideLesAgregats() {
        UtilisationDouaniere util = douaneVisee();
        assertThat(util.getTotalPrisEnCharge()).isNotNull();
        rejetTemporaire(util);

        service.update(util.getId(), requete(BigDecimal.valueOf(700)), entreprise);

        UtilisationDouaniere apres = (UtilisationDouaniere) utilisationRepository
                .findById(util.getId()).orElseThrow();
        assertThat(apres.getTotalPrisEnCharge()).isNull();
        assertThat(apres.getTotalAPayer()).isNull();
        assertThat(apres.getMontantTVA()).isNull();
        assertThat(apres.getMontantDroits()).isNull();
    }

    @Test
    @Transactional
    void modifierLesLignes_rendLeChequeCaduc() {
        UtilisationDouaniere util = douaneVisee();
        util.setNumeroCheque("CHQ-001");
        util.setMontantCheque(BigDecimal.valueOf(300));
        util.setBanqueNom("BMCI");
        utilisationRepository.save(util);
        rejetTemporaire(util);

        service.update(util.getId(), requete(BigDecimal.valueOf(700)), entreprise);

        UtilisationDouaniere apres = (UtilisationDouaniere) utilisationRepository
                .findById(util.getId()).orElseThrow();
        assertThat(apres.getNumeroCheque()).isNull();
        assertThat(apres.getMontantCheque()).isNull();
        assertThat(apres.getBanqueNom()).isNull();
    }

    @Test
    @Transactional
    void agregatsInvalides_laLiquidationEstRefusee() {
        UtilisationDouaniere util = douaneVisee();
        rejetTemporaire(util);
        service.update(util.getId(), requete(BigDecimal.valueOf(700)), entreprise);

        // Aucun contrôle nouveau n'a été ajouté : c'est la garde existante de liquiderDouane,
        // qui exige totalPrisEnCharge > 0, qui refuse le dossier.
        assertThatThrownBy(() -> service.liquiderDouane(util.getId(),
                new AuthenticatedUser(2L, "dgtcp", Role.DGTCP)))
                .isInstanceOf(ApiException.class);
    }

    @Test
    @Transactional
    void modifierSansToucherAuxLignes_preserveLeCheque() {
        UtilisationDouaniere util = douaneVisee();
        util.setNumeroCheque("CHQ-002");
        util.setMontantCheque(BigDecimal.valueOf(300));
        utilisationRepository.save(util);
        rejetTemporaire(util);

        // Une coquille sur un numéro de déclaration ne doit pas coûter le chèque.
        CreateUtilisationCreditRequest req = requete(BigDecimal.valueOf(700));
        req.setLignes(null);
        service.update(util.getId(), req, entreprise);

        UtilisationDouaniere apres = (UtilisationDouaniere) utilisationRepository
                .findById(util.getId()).orElseThrow();
        assertThat(apres.getNumeroCheque()).isEqualTo("CHQ-002");
        assertThat(apres.getTotalPrisEnCharge()).isNotNull();
    }

    // ── fixtures ───────────────────────────────────────────────────────────────────────────────

    /** Un dossier douanier visé par la DGD : agrégats renseignés, annotation posée. */
    private UtilisationDouaniere douaneVisee() {
        CertificatCredit cert = certificatRepository.findByNumero(DEMO_CERTIFICAT)
                .orElseThrow(() -> new IllegalStateException("Certificat seed absent: " + DEMO_CERTIFICAT));

        UtilisationDouaniere util = new UtilisationDouaniere();
        util.setType(TypeUtilisation.DOUANIER);
        util.setStatut(StatutUtilisation.VISE);
        util.setDateDemande(Instant.now());
        util.setMontant(BigDecimal.valueOf(1000));
        util.setCertificatCredit(cert);
        util.setEntreprise(cert.getEntreprise());
        util.setNumeroDeclaration("DECL-EDIT-" + System.nanoTime());
        util.setNumeroBulletin("BUL-EDIT-" + System.nanoTime());
        util.setTotalPrisEnCharge(BigDecimal.valueOf(1000));
        util.setTotalAPayer(BigDecimal.valueOf(300));
        util.setMontantTVA(BigDecimal.valueOf(200));
        util.setMontantDroits(BigDecimal.valueOf(800));
        util = (UtilisationDouaniere) utilisationRepository.save(util);

        LigneBulletinLiquidation ligne = LigneBulletinLiquidation.builder()
                .utilisationDouaniere(util)
                .codeTaxe("DD")
                .denominationTaxe("Droits de douane")
                .typeLigne(TypeLigneTaxe.ARTICLE)
                .valeurTaxe(BigDecimal.valueOf(1000))
                .affectationEntreprise(AffectationTaxe.AU_CI)
                .affectation(AffectationTaxe.AU_CI)
                .build();
        util.getLignes().add(ligneRepository.save(ligne));
        return util;
    }

    private void rejetTemporaire(UtilisationDouaniere util) {
        decisionService.saveDecision(util.getId(), DecisionCorrectionType.REJET_TEMP,
                "Bulletin à corriger", Set.of("BULLETIN_LIQUIDATION"), dgd);
    }

    private CreateUtilisationCreditRequest requete(BigDecimal valeurLigne) {
        CertificatCredit cert = certificatRepository.findByNumero(DEMO_CERTIFICAT).orElseThrow();
        CreateUtilisationCreditRequest req = new CreateUtilisationCreditRequest();
        req.setType(TypeUtilisation.DOUANIER);
        req.setCertificatCreditId(cert.getId());
        req.setEntrepriseId(cert.getEntreprise().getId());
        req.setNumeroDeclaration("DECL-CORRIGE-" + System.nanoTime());
        req.setNumeroBulletin("BUL-CORRIGE-" + System.nanoTime());
        req.setMontant(valeurLigne);

        CreateUtilisationCreditRequest.LigneBulletinRequest ligne =
                new CreateUtilisationCreditRequest.LigneBulletinRequest();
        ligne.setCodeTaxe("DD");
        ligne.setDenominationTaxe("Droits de douane");
        ligne.setTypeLigne(TypeLigneTaxe.ARTICLE);
        ligne.setValeurTaxe(valeurLigne);
        ligne.setAffectation(AffectationTaxe.AU_CI);
        req.setLignes(List.of(ligne));
        return req;
    }
}
