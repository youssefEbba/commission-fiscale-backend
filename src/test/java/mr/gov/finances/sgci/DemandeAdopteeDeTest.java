package mr.gov.finances.sgci;

import mr.gov.finances.sgci.domain.entity.AutoriteContractante;
import mr.gov.finances.sgci.domain.entity.Convention;
import mr.gov.finances.sgci.domain.entity.DemandeCorrection;
import mr.gov.finances.sgci.domain.entity.Dqe;
import mr.gov.finances.sgci.domain.entity.Entreprise;
import mr.gov.finances.sgci.domain.entity.Marche;
import mr.gov.finances.sgci.domain.entity.ModeleFiscal;
import mr.gov.finances.sgci.domain.enums.StatutDemande;
import mr.gov.finances.sgci.domain.enums.StatutMarche;
import mr.gov.finances.sgci.repository.AutoriteContractanteRepository;
import mr.gov.finances.sgci.repository.ConventionRepository;
import mr.gov.finances.sgci.repository.DemandeCorrectionRepository;
import mr.gov.finances.sgci.repository.EntrepriseRepository;
import mr.gov.finances.sgci.repository.MarcheRepository;
import mr.gov.finances.sgci.service.DossierGedService;
import org.springframework.context.ApplicationContext;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Une demande de correction ADOPTEE neuve, sans certificat, pour les tests qui mettent un crédit en
 * place.
 *
 * <p>Ces tests prenaient la première demande adoptée de la démonstration, après avoir annulé le
 * certificat {@code CI-TEST-OUVERT} pour la « libérer ». Mais ce certificat n'est plus rattaché à une
 * demande adoptée, toutes celles de la démonstration ont déjà leur certificat (un seul actif par
 * demande), et l'annulation cassait les tests suivants qui comptent sur un crédit ouvert. Chaque test
 * crée désormais la sienne, comme le fait la démonstration : marché signé, visas des directions posés.
 */
final class DemandeAdopteeDeTest {

    record Ids(Long demandeId, Long entrepriseId) {}

    private DemandeAdopteeDeTest() {
    }

    static Ids creer(ApplicationContext context) {
        AutoriteContractante autorite = context.getBean(AutoriteContractanteRepository.class).findByCode("AC_DEFAULT")
                .orElseThrow(() -> new AssertionError("Autorité AC_DEFAULT absente du seed"));
        Entreprise entreprise = context.getBean(EntrepriseRepository.class).findByNif("NIF_DEFAULT")
                .orElseThrow(() -> new AssertionError("Entreprise NIF_DEFAULT absente du seed"));
        Convention convention = context.getBean(ConventionRepository.class).findByReference("CONV-DEFAULT")
                .orElseThrow(() -> new AssertionError("Convention CONV-DEFAULT absente du seed"));
        MarcheRepository marches = context.getBean(MarcheRepository.class);
        DemandeCorrectionRepository demandes = context.getBean(DemandeCorrectionRepository.class);
        String suffixe = Long.toString(System.nanoTime(), 36).toUpperCase();

        Marche marche = marches.save(Marche.builder()
                .numeroMarche("MARCHE-TEST-" + suffixe)
                .dateSignature(LocalDate.now())
                .montantContratHt(BigDecimal.valueOf(1_000_000))
                .statut(StatutMarche.EN_COURS)
                .convention(convention)
                .build());

        DemandeCorrection demande = DemandeCorrection.builder()
                .numero("DC-TEST-" + suffixe)
                .dateDepot(Instant.now())
                .statut(StatutDemande.ADOPTEE)
                .autoriteContractante(autorite)
                .entreprise(entreprise)
                .convention(convention)
                .build();
        demande.setValidationDgd(true);
        demande.setValidationDgdDate(Instant.now());
        demande.setValidationDgtcp(true);
        demande.setValidationDgtcpDate(Instant.now());
        demande.setValidationDgi(true);
        demande.setValidationDgiDate(Instant.now());
        demande.setValidationDgb(true);
        demande.setValidationDgbDate(Instant.now());
        demande.setModeleFiscal(ModeleFiscal.builder().demandeCorrection(demande).build());
        demande.setDqe(Dqe.builder().demandeCorrection(demande).build());
        demande = demandes.save(demande);

        context.getBean(DossierGedService.class).ensureCreatedForDemandeCorrection(demande.getId());

        marche.setDemandeCorrection(demande);
        marche = marches.save(marche);
        demande.setMarche(marche);
        demandes.save(demande);
        return new Ids(demande.getId(), entreprise.getId());
    }
}
