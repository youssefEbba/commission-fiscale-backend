package mr.gov.finances.sgci;

import mr.gov.finances.sgci.domain.entity.CertificatCredit;
import mr.gov.finances.sgci.domain.entity.Entreprise;
import mr.gov.finances.sgci.domain.entity.SecteurActivite;
import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.domain.enums.CategorieEntreprise;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.domain.enums.StatutCertificat;
import mr.gov.finances.sgci.repository.CertificatCreditRepository;
import mr.gov.finances.sgci.repository.EntrepriseRepository;
import mr.gov.finances.sgci.repository.SecteurActiviteRepository;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.reporting.BeneficiairesService;
import mr.gov.finances.sgci.web.dto.reporting.BeneficiairesDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/** Le suivi des bénéficiaires (cahier des charges, point 4), isolé par le filtre entreprise. */
@SpringBootTest
@ActiveProfiles("test")
class BeneficiairesIT {

    @Autowired private BeneficiairesService service;
    @Autowired private EntrepriseRepository entrepriseRepository;
    @Autowired private CertificatCreditRepository certificatRepository;
    @Autowired private SecteurActiviteRepository secteurRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;

    @Test
    @Transactional
    void uneEntrepriseEtrangerePmeDuBtpEstRecenseeParAnnee() {
        SecteurActivite btp = secteurRepository.findAll().stream()
                .filter(s -> s.getCode().equals("BTP")).findFirst().orElseThrow();
        Entreprise e = entrepriseRepository.save(Entreprise.builder()
                .raisonSociale("Bénéficiaire " + System.nanoTime())
                .registreCommerceEtranger("RC-ETR-" + System.nanoTime())
                .entrepriseEtrangere(true)
                .categorie(CategorieEntreprise.PME)
                .secteur(btp)
                .build());
        Instant an2025 = LocalDate.of(2025, 3, 15).atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant an2026 = LocalDate.of(2026, 2, 10).atStartOfDay(ZoneId.systemDefault()).toInstant();
        credit(e, an2025, "1000", "400", "300");
        credit(e, an2026, "500", "0", "500");

        BeneficiairesDto dto = service.beneficiaires(president(), null, null, null, e.getId());

        assertThat(dto.getTotalBeneficiaires()).isEqualTo(1);
        assertThat(dto.getTotalCredits()).isEqualTo(2);
        assertThat(dto.getMontantAccorde()).isEqualByComparingTo("1500");
        // (1000 - 400 - 300) + (500 - 0 - 500)
        assertThat(dto.getMontantConsomme()).isEqualByComparingTo("300");
        assertThat(ventilation(dto.getParNature(), "ETRANGERE").getNbBeneficiaires()).isEqualTo(1);
        assertThat(ventilation(dto.getParNature(), "NATIONALE").getNbBeneficiaires()).isZero();
        assertThat(ventilation(dto.getParCategorie(), "PME").getNbCredits()).isEqualTo(2);
        assertThat(dto.getParSecteur()).extracting(BeneficiairesDto.Ventilation::getLibelle).containsExactly(btp.getLibelle());
        // Sans demande de correction, donc sans convention : fonds nationaux.
        assertThat(ventilation(dto.getParFinancement(), "FONDS_NATIONAUX").getNbCredits()).isEqualTo(2);
        assertThat(dto.getParAnnee()).extracting(BeneficiairesDto.Annee::getAnnee).containsExactly(2025, 2026);
        assertThat(dto.getParAnnee().get(0).getNouveauxBeneficiaires()).isEqualTo(1);
        assertThat(dto.getParAnnee().get(1).getNouveauxBeneficiaires()).isZero();
        assertThat(dto.getParAnnee().get(1).getBeneficiaires()).isEqualTo(1);
    }

    @Test
    @Transactional
    void unCreditNonMisEnPlaceNeFaitPasUnBeneficiaire() {
        Entreprise e = entrepriseRepository.save(Entreprise.builder()
                .raisonSociale("Pas encore bénéficiaire " + System.nanoTime())
                .nif("NIF-BEN-" + System.nanoTime())
                .build());
        certificatRepository.saveAndFlush(CertificatCredit.builder()
                .numero("CERT-BEN-" + System.nanoTime())
                .statut(StatutCertificat.EN_CONTROLE)
                .entreprise(e)
                .dateEmission(Instant.now())
                .montantCordon(new BigDecimal("100"))
                .build());

        BeneficiairesDto dto = service.beneficiaires(president(), null, null, null, e.getId());

        assertThat(dto.getTotalBeneficiaires()).isZero();
        assertThat(ventilation(dto.getParCategorie(), "NON_RENSEIGNEE").getNbBeneficiaires()).isZero();
    }

    private void credit(Entreprise e, Instant miseEnPlace, String accorde, String soldeCordon, String soldeTva) {
        certificatRepository.saveAndFlush(CertificatCredit.builder()
                .numero("CERT-BEN-" + System.nanoTime())
                .statut(StatutCertificat.OUVERT)
                .entreprise(e)
                .dateEmission(miseEnPlace)
                .dateMiseEnPlace(miseEnPlace)
                .montantCordon(new BigDecimal(accorde))
                .montantTVAInterieure(BigDecimal.ZERO)
                .soldeCordon(new BigDecimal(soldeCordon))
                .soldeTVA(new BigDecimal(soldeTva))
                .tvaImportationDouane(BigDecimal.ZERO)
                .build());
    }

    private AuthenticatedUser president() {
        Utilisateur u = utilisateurRepository.findByUsername("president").orElseThrow();
        return new AuthenticatedUser(u.getId(), u.getUsername(), Role.PRESIDENT);
    }

    private static BeneficiairesDto.Ventilation ventilation(java.util.List<BeneficiairesDto.Ventilation> liste, String cle) {
        return liste.stream().filter(v -> v.getCle().equals(cle)).findFirst().orElseThrow();
    }
}
