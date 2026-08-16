package mr.gov.finances.sgci.service.archive;

import mr.gov.finances.sgci.domain.entity.AutoriteContractante;
import mr.gov.finances.sgci.domain.entity.CertificatCredit;
import mr.gov.finances.sgci.domain.entity.Convention;
import mr.gov.finances.sgci.domain.entity.DemandeCorrection;
import mr.gov.finances.sgci.domain.entity.Entreprise;
import mr.gov.finances.sgci.domain.entity.UtilisationCredit;
import mr.gov.finances.sgci.domain.entity.UtilisationDouaniere;
import mr.gov.finances.sgci.domain.enums.AffectationTaxe;
import mr.gov.finances.sgci.domain.enums.StatutCertificat;
import mr.gov.finances.sgci.domain.enums.StatutConvention;
import mr.gov.finances.sgci.domain.enums.StatutDemande;
import mr.gov.finances.sgci.domain.enums.StatutUtilisation;
import mr.gov.finances.sgci.repository.AutoriteContractanteRepository;
import mr.gov.finances.sgci.repository.CertificatCreditRepository;
import mr.gov.finances.sgci.repository.ConventionRepository;
import mr.gov.finances.sgci.repository.DemandeCorrectionRepository;
import mr.gov.finances.sgci.repository.EntrepriseRepository;
import mr.gov.finances.sgci.repository.UtilisationCreditRepository;
import mr.gov.finances.sgci.repository.TransfertCreditRepository;
import mr.gov.finances.sgci.domain.entity.TransfertCredit;
import mr.gov.finances.sgci.domain.enums.StatutTransfert;
import mr.gov.finances.sgci.service.UtilisationCreditService;
import mr.gov.finances.sgci.domain.enums.TypeAchat;
import mr.gov.finances.sgci.domain.enums.TypeUtilisation;
import mr.gov.finances.sgci.web.dto.CreateUtilisationCreditRequest;
import mr.gov.finances.sgci.web.dto.UtilisationCreditDto;
import mr.gov.finances.sgci.web.dto.archive.ImportArchiveResultatDto;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Vérifie qu'un relevé d'archive produit bien un certificat de crédit et ses utilisations en base.
 *
 * <p>Le relevé n'étant pas versionné (données nominatives), le test se met en pause si la propriété
 * système {@code releve.archive.file} n'est pas fournie.
 */
@SpringBootTest
@ActiveProfiles("test")
class ImportArchiveCreditServiceIT {

    @Autowired
    private ImportArchiveCreditService service;
    @Autowired
    private CertificatCreditRepository certificatRepository;
    @Autowired
    private UtilisationCreditRepository utilisationRepository;
    @Autowired
    private EntrepriseRepository entrepriseRepository;
    @Autowired
    private AutoriteContractanteRepository autoriteRepository;
    @Autowired
    private ConventionRepository conventionRepository;
    @Autowired
    private DemandeCorrectionRepository demandeRepository;
    @Autowired
    private UtilisationCreditService utilisationCreditService;
    @Autowired
    private TransfertCreditRepository transfertCreditRepository;

    private AutoriteContractante autoriteCible() {
        AutoriteContractante ac = new AutoriteContractante();
        ac.setNom("Autorité de reprise d'archive " + System.nanoTime());
        return autoriteRepository.save(ac);
    }

    private Convention conventionCible(AutoriteContractante autorite) {
        Convention c = new Convention();
        c.setReference("CONV-IT-" + System.nanoTime());
        c.setIntitule("Convention de reprise d'archive");
        c.setStatut(StatutConvention.VALIDE);
        c.setAutoriteContractante(autorite);
        return conventionRepository.save(c);
    }

    private MultipartFile releve() throws Exception {
        String chemin = System.getProperty("releve.archive.file");
        Assumptions.assumeTrue(chemin != null && !chemin.isBlank(),
                "Relevé non fourni : passer -Dreleve.archive.file=<chemin>");
        Path fichier = Path.of(chemin);
        Assumptions.assumeTrue(Files.exists(fichier), "Relevé introuvable : " + chemin);
        return new MockMultipartFile("fichier", fichier.getFileName().toString(),
                "application/vnd.ms-excel", Files.readAllBytes(fichier));
    }

    private Entreprise entrepriseCible() {
        Entreprise e = new Entreprise();
        e.setRaisonSociale("Entreprise de reprise d'archive");
        e.setNif("IT-" + System.nanoTime());
        e.setEntrepriseEtrangere(false);
        return entrepriseRepository.save(e);
    }

    @Test
    @Transactional
    void cree_le_certificat_ses_utilisations_et_les_lignes_de_bulletin() throws Exception {
        MultipartFile fichier = releve();
        Entreprise entreprise = entrepriseCible();

        AutoriteContractante autorite = autoriteCible();
        Convention convention = conventionCible(autorite);

        ImportArchiveResultatDto resultat = service.importer(
                fichier, entreprise.getId(), autorite.getId(), convention.getId(), null, true, null);

        // --- Le certificat existe bien en base ---
        assertNotNull(resultat.getCertificatId(), "aucun certificat créé");
        CertificatCredit certificat = certificatRepository.findById(resultat.getCertificatId()).orElseThrow();

        assertEquals(entreprise.getId(), certificat.getEntreprise().getId());
        assertEquals(0, certificat.getMontantCordon().compareTo(new BigDecimal("145829345.49")));
        assertEquals(0, certificat.getMontantTVAInterieure().compareTo(new BigDecimal("137336993.31")));

        // Soldes repris tels quels du relevé : douanier soldé, intérieur encore pourvu.
        assertEquals(0, certificat.getSoldeCordon().compareTo(BigDecimal.ZERO));
        assertEquals(0, certificat.getSoldeTVA().compareTo(new BigDecimal("31725849.31")));

        // Le crédit intérieur restant rend le certificat exploitable : il doit rester OUVERT.
        assertEquals(StatutCertificat.OUVERT, certificat.getStatut());

        // --- La chaîne de rattachement est complète ---
        assertNotNull(resultat.getDemandeCorrectionId(), "aucune demande d'archive créée");
        DemandeCorrection demande = demandeRepository.findById(resultat.getDemandeCorrectionId()).orElseThrow();
        assertEquals(demande.getId(), certificat.getDemandeCorrection().getId());
        assertEquals(autorite.getId(), demande.getAutoriteContractante().getId());
        assertEquals(convention.getId(), demande.getConvention().getId());
        assertEquals(entreprise.getId(), demande.getEntreprise().getId());
        // Dossier déjà instruit : statut final et visas acquis, pour ne pas réapparaître en file d'attente.
        assertEquals(StatutDemande.NOTIFIEE, demande.getStatut());
        assertTrue(demande.isValidationDgd() && demande.isValidationDgtcp()
                && demande.isValidationDgi() && demande.isValidationDgb());
        // Les enveloppes du relevé sont reportées sur la demande.
        assertEquals(0, demande.getCreditExterieur().compareTo(new BigDecimal("145829345.49")));
        assertEquals(0, demande.getCreditInterieur().compareTo(new BigDecimal("137336993.31")));

        // La référence lisible est laissée vide : le rattrapage applicatif l'attribuera.
        assertEquals(null, certificat.getReference());
        assertTrue(certificat.getNumero().startsWith("ARCHIVE-"));

        // --- Les utilisations sont rattachées au certificat ---
        List<UtilisationCredit> utilisations =
                utilisationRepository.findByCertificatCreditId(certificat.getId());
        assertEquals(54, utilisations.size());
        assertEquals(54, resultat.getUtilisationsDouanieres() + resultat.getUtilisationsInterieures());
        assertTrue(utilisations.stream().allMatch(u -> u.getStatut() == StatutUtilisation.APUREE),
                "les utilisations d'archive doivent être apurées");

        // --- La ventilation fiscale est bien matérialisée en lignes de bulletin ---
        assertTrue(resultat.getLignesTaxeCreees() > 0, "aucune ligne de bulletin créée");

        UtilisationDouaniere douaniere = utilisations.stream()
                .filter(UtilisationDouaniere.class::isInstance)
                .map(UtilisationDouaniere.class::cast)
                .filter(u -> u.getNumeroDeclaration() != null && u.getNumeroDeclaration().startsWith("UT 13 "))
                .findFirst().orElseThrow();

        assertEquals(0, douaniere.getMontant().compareTo(new BigDecimal("3971743.73")));
        assertEquals(0, douaniere.getTotalPrisEnCharge().compareTo(new BigDecimal("3971743.73")));
        assertEquals(0, douaniere.getTotalAPayer().compareTo(new BigDecimal("253246.01")));
        assertFalse(douaniere.getLignes().isEmpty());
        assertTrue(douaniere.getLignes().stream()
                        .anyMatch(l -> "DD".equals(l.getCodeTaxe()) && l.getAffectation() == AffectationTaxe.AU_CI),
                "la ligne DD doit être imputée au crédit");
        assertTrue(douaniere.getLignes().stream()
                        .anyMatch(l -> "IMF".equals(l.getCodeTaxe()) && l.getAffectation() == AffectationTaxe.A_PAYER),
                "la ligne IMF doit rester à la charge de l'entreprise");
    }

    @Test
    @Transactional
    void refuse_un_second_import_du_meme_releve() throws Exception {
        Entreprise entreprise = entrepriseCible();
        AutoriteContractante autorite = autoriteCible();
        Convention convention = conventionCible(autorite);
        service.importer(releve(), entreprise.getId(), autorite.getId(), convention.getId(), null, true, null);

        // Le numéro dérivé du relevé sert de garde contre le double import.
        assertThrows(RuntimeException.class, () -> service.importer(
                releve(), entreprise.getId(), autorite.getId(), convention.getId(), null, true, null));
    }

    @Test
    @Transactional
    void un_certificat_repris_reste_exploitable_pour_de_nouvelles_utilisations() throws Exception {
        Entreprise entreprise = entrepriseCible();
        AutoriteContractante autorite = autoriteCible();
        Convention convention = conventionCible(autorite);

        ImportArchiveResultatDto resultat = service.importer(
                releve(), entreprise.getId(), autorite.getId(), convention.getId(), null, true, null);

        CertificatCredit certificat = certificatRepository.findById(resultat.getCertificatId()).orElseThrow();
        BigDecimal soldeTvaAvant = certificat.getSoldeTVA();

        // Le crédit intérieur du relevé est encore pourvu : une nouvelle utilisation doit passer
        // le contrôle d'éligibilité comme sur un certificat créé nativement.
        CreateUtilisationCreditRequest requete = new CreateUtilisationCreditRequest();
        requete.setType(TypeUtilisation.TVA_INTERIEURE);
        requete.setCertificatCreditId(certificat.getId());
        requete.setEntrepriseId(entreprise.getId());
        requete.setTypeAchat(TypeAchat.ACHAT_LOCAL);
        requete.setNumeroFacture("FA-APRES-ARCHIVE-001");
        requete.setMontantTVAInterieure(new BigDecimal("100000"));

        UtilisationCreditDto creee = utilisationCreditService.create(requete, null);

        assertNotNull(creee.getId(), "l'utilisation n'a pas été créée sur le certificat repris");
        assertEquals(certificat.getId(), creee.getCertificatCreditId());

        // Le certificat reste ouvert et son solde intérieur demeure disponible pour la suite
        // du circuit (le décompte effectif intervient plus loin dans le workflow).
        CertificatCredit apres = certificatRepository.findById(certificat.getId()).orElseThrow();
        assertEquals(StatutCertificat.OUVERT, apres.getStatut());
        assertNotNull(soldeTvaAvant);
        assertTrue(apres.getSoldeTVA().compareTo(BigDecimal.ZERO) > 0,
                "le crédit intérieur repris doit rester mobilisable");
    }

    @Test
    @Transactional
    void enregistre_le_transfert_du_releve_et_verrouille_les_utilisations_douanieres() throws Exception {
        Entreprise entreprise = entrepriseCible();
        AutoriteContractante autorite = autoriteCible();
        Convention convention = conventionCible(autorite);

        ImportArchiveResultatDto resultat = service.importer(
                releve(), entreprise.getId(), autorite.getId(), convention.getId(), null, true, null);

        // Le transfert du relevé est matérialisé, au montant réellement transféré (part TVA).
        assertNotNull(resultat.getTransfertCreditId(), "aucun transfert enregistré");
        assertEquals(0, resultat.getTransfertCreditMontant().compareTo(new BigDecimal("32136560.62")));

        TransfertCredit transfert =
                transfertCreditRepository.findById(resultat.getTransfertCreditId()).orElseThrow();
        assertEquals(StatutTransfert.TRANSFERE, transfert.getStatut());
        assertEquals(resultat.getCertificatId(), transfert.getCertificatCredit().getId());

        // Conséquence métier attendue : plus aucune utilisation douanière n'est admise.
        CreateUtilisationCreditRequest douaniere = new CreateUtilisationCreditRequest();
        douaniere.setType(TypeUtilisation.DOUANIER);
        douaniere.setCertificatCreditId(resultat.getCertificatId());
        douaniere.setEntrepriseId(entreprise.getId());
        douaniere.setNumeroDeclaration("IM4-APRES-ARCHIVE");
        RuntimeException refus = assertThrows(RuntimeException.class,
                () -> utilisationCreditService.create(douaniere, null));
        assertTrue(refus.getMessage() != null && refus.getMessage().toLowerCase().contains("transfert"),
                () -> "le refus doit invoquer le transfert exécuté, message : " + refus.getMessage());

        // La TVA intérieure, elle, reste mobilisable.
        CreateUtilisationCreditRequest interieure = new CreateUtilisationCreditRequest();
        interieure.setType(TypeUtilisation.TVA_INTERIEURE);
        interieure.setCertificatCreditId(resultat.getCertificatId());
        interieure.setEntrepriseId(entreprise.getId());
        interieure.setTypeAchat(TypeAchat.ACHAT_LOCAL);
        interieure.setNumeroFacture("FA-APRES-TRANSFERT-001");
        interieure.setMontantTVAInterieure(new BigDecimal("50000"));
        assertNotNull(utilisationCreditService.create(interieure, null).getId());
    }
}
