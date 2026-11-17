package mr.gov.finances.sgci;

import mr.gov.finances.sgci.domain.entity.CertificatCredit;
import mr.gov.finances.sgci.domain.enums.StatutCertificat;
import mr.gov.finances.sgci.domain.enums.TypeUtilisation;
import mr.gov.finances.sgci.repository.CertificatCreditRepository;
import mr.gov.finances.sgci.service.UtilisationCreditService;
import mr.gov.finances.sgci.web.dto.CreateUtilisationCreditRequest;
import mr.gov.finances.sgci.web.dto.UtilisationCreditDto;
import mr.gov.finances.sgci.web.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Une utilisation porte un ou plusieurs n° de déclaration (douane) ou de facture (TVA intérieure),
 * et aucun ne peut resservir sur le même crédit (remarque 7 de la recette).
 */
@SpringBootTest
@ActiveProfiles("test")
class UtilisationNumerosMultiplesIT {

    @Autowired private UtilisationCreditService service;
    @Autowired private CertificatCreditRepository certificatRepository;

    @Test
    @Transactional
    void plusieursFacturesEtAucuneNeResert() {
        CertificatCredit c = unCreditOuvert();
        String n = String.valueOf(System.nanoTime());

        UtilisationCreditDto dto = service.create(tva(c, List.of(" FAC-A-" + n, "FAC-B-" + n, "fac-a-" + n, "")));

        assertThat(dto.getNumerosFacture()).containsExactly("FAC-A-" + n, "FAC-B-" + n);
        assertThat(dto.getNumeroFacture()).isEqualTo("FAC-A-" + n);
        assertThatThrownBy(() -> service.create(tva(c, List.of("FAC-C-" + n, "fac-b-" + n))))
                .isInstanceOf(ApiException.class).hasMessageContaining("FAC-B-" + n);
        // L'ancien champ seul reste accepté, comme une liste d'un numéro.
        CreateUtilisationCreditRequest ancien = tva(c, null);
        ancien.setNumeroFacture("FAC-D-" + n);
        assertThat(service.create(ancien).getNumerosFacture()).containsExactly("FAC-D-" + n);
    }

    @Test
    @Transactional
    void plusieursDeclarationsEtAucuneNeResert() {
        CertificatCredit c = unCreditOuvert();
        String n = String.valueOf(System.nanoTime());

        UtilisationCreditDto dto = service.create(douane(c, List.of("DEC-1-" + n, "DEC-2-" + n, "DEC-3-" + n)));

        assertThat(dto.getNumerosDeclaration()).containsExactly("DEC-1-" + n, "DEC-2-" + n, "DEC-3-" + n);
        assertThat(dto.getNumeroDeclaration()).isEqualTo("DEC-1-" + n);
        assertThatThrownBy(() -> service.create(douane(c, List.of("DEC-3-" + n))))
                .isInstanceOf(ApiException.class).hasMessageContaining("déclaration");
    }

    private CertificatCredit unCreditOuvert() {
        CertificatCredit c = certificatRepository.findByNumero("CI-DEMO-SCEN-E").orElseThrow();
        c.setStatut(StatutCertificat.OUVERT);
        return certificatRepository.saveAndFlush(c);
    }

    private static CreateUtilisationCreditRequest tva(CertificatCredit c, List<String> factures) {
        return CreateUtilisationCreditRequest.builder()
                .type(TypeUtilisation.TVA_INTERIEURE).certificatCreditId(c.getId()).entrepriseId(c.getEntreprise().getId())
                .numerosFacture(factures).dateFacture(Instant.now())
                .montantTVAInterieure(BigDecimal.ONE).brouillon(true).build();
    }

    private static CreateUtilisationCreditRequest douane(CertificatCredit c, List<String> declarations) {
        return CreateUtilisationCreditRequest.builder()
                .type(TypeUtilisation.DOUANIER).certificatCreditId(c.getId()).entrepriseId(c.getEntreprise().getId())
                .numerosDeclaration(declarations).dateDeclaration(Instant.now()).brouillon(true).build();
    }
}
