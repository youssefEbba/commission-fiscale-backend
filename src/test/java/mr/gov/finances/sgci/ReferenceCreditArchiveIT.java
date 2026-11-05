package mr.gov.finances.sgci;

import mr.gov.finances.sgci.config.ReferenceBackfillMigration;
import mr.gov.finances.sgci.domain.entity.CertificatCredit;
import mr.gov.finances.sgci.domain.entity.Entreprise;
import mr.gov.finances.sgci.domain.enums.StatutCertificat;
import mr.gov.finances.sgci.repository.CertificatCreditRepository;
import mr.gov.finances.sgci.repository.EntrepriseRepository;
import mr.gov.finances.sgci.service.archive.ImportArchiveCreditService;
import mr.gov.finances.sgci.web.dto.archive.ReleveArchiveDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Un crédit repris de l'archive porte la référence « ARCHIVE- » + N° Credit du fichier. Ceux repris
 * avant cette règle avaient reçu une référence de crédit nouveau (CR-…) : le démarrage la remplace.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ReferenceCreditArchiveIT {

    @Autowired private ReferenceBackfillMigration migration;
    @Autowired private CertificatCreditRepository certificatRepository;
    @Autowired private EntrepriseRepository entrepriseRepository;
    @Autowired private ImportArchiveCreditService importService;

    @Test
    void unCreditRepriseAvecUneReferenceCrEstRenommeArchive() {
        long n = System.nanoTime() % 1_000_000_000L;
        Entreprise e = entrepriseRepository.save(Entreprise.builder().raisonSociale("Archive " + n).nif("88" + n).build());
        CertificatCredit repris = certificat(e, "ARCHIVE-" + n + "/2026", "CR-9" + n + "-11-2026");
        CertificatCredit nouveau = certificat(e, "CERT-" + n, "CR-8" + n + "-11-2026");

        migration.migrateIfNeeded();

        assertThat(certificatRepository.findById(repris.getId()).orElseThrow().getReference())
                .isEqualTo("ARCHIVE-" + n + "/2026");
        assertThat(certificatRepository.findById(nouveau.getId()).orElseThrow().getReference())
                .as("un crédit créé dans l'application garde sa référence").isEqualTo("CR-8" + n + "-11-2026");
    }

    @Test
    void leNumeroDeCreditSeCompareAuxSeulesReferencesArchive() {
        long n = System.nanoTime() % 1_000_000_000L;
        Entreprise e = entrepriseRepository.save(Entreprise.builder().raisonSociale("Archive bis " + n).nif("89" + n).build());
        // Une référence « N° Credit » sans préfixe n'est pas un crédit repris…
        certificat(e, "CERT-A" + n, n + "/2026");
        assertThat(importService.certificatExistant(releve(n + "/2026"))).isEmpty();
        // … une référence « ARCHIVE-N° Credit » l'est, quel que soit le numéro interne du crédit.
        CertificatCredit repris = certificat(e, "CERT-B" + n, "ARCHIVE-" + (n + 1) + "/2026");
        assertThat(importService.certificatExistant(releve((n + 1) + "/2026"))).get()
                .extracting(CertificatCredit::getId).isEqualTo(repris.getId());
    }

    private ReleveArchiveDto releve(String numeroCredit) {
        ReleveArchiveDto r = new ReleveArchiveDto();
        r.setNumeroCredit(numeroCredit);
        return r;
    }

    private CertificatCredit certificat(Entreprise e, String numero, String reference) {
        CertificatCredit c = new CertificatCredit();
        c.setNumero(numero);
        c.setReference(reference);
        c.setEntreprise(e);
        c.setStatut(StatutCertificat.OUVERT);
        return certificatRepository.saveAndFlush(c);
    }
}
