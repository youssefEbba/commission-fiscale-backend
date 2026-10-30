package mr.gov.finances.sgci;

import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.repository.AutoriteContractanteRepository;
import mr.gov.finances.sgci.repository.ConventionRepository;
import mr.gov.finances.sgci.repository.EntrepriseRepository;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.DemandeCorrectionService;
import mr.gov.finances.sgci.web.dto.AdminCorrectionDemandeCorrectionRequest;
import mr.gov.finances.sgci.web.dto.CreateDemandeCorrectionRequest;
import mr.gov.finances.sgci.web.dto.DemandeCorrectionDto;
import mr.gov.finances.sgci.web.dto.UpdateDemandeCorrectionRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La demande de correction porte la référence de l'appel d'offres, demandée par l'équipe.
 *
 * <p>Facultative : les demandes existantes n'en ont pas, et une demande se prépare en brouillon.
 * Elle se saisit à la création, se modifie avec les autres informations, et l'administrateur peut
 * la corriger.
 *
 * <p>Niveau service : Tomcat ne démarre pas sur ce poste.
 */
@SpringBootTest
@ActiveProfiles("test")
class ReferenceAppelOffreIT {

    @Autowired private DemandeCorrectionService service;
    @Autowired private AutoriteContractanteRepository autoriteRepository;
    @Autowired private EntrepriseRepository entrepriseRepository;
    @Autowired private ConventionRepository conventionRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;

    private AuthenticatedUser ac;
    private AuthenticatedUser admin;

    @BeforeEach
    void setUp() {
        ac = compte("ac");
        admin = compte("admin");
    }

    @Test
    @Transactional
    void saisieALaCreationEtRelueDansLaFiche() {
        DemandeCorrectionDto creee = service.create(creation("AO-2026-015"), ac);

        assertThat(creee.getReferenceAppelOffre()).isEqualTo("AO-2026-015");
        assertThat(service.findById(creee.getId(), ac).getReferenceAppelOffre()).isEqualTo("AO-2026-015");
    }

    @Test
    @Transactional
    void modifiableAvecLesAutresInformations() {
        DemandeCorrectionDto creee = service.create(creation("AO-2026-015"), ac);

        UpdateDemandeCorrectionRequest maj = UpdateDemandeCorrectionRequest.builder()
                .autoriteContractanteId(creee.getAutoriteContractanteId())
                .entrepriseId(creee.getEntrepriseId())
                .conventionId(creee.getConventionId())
                .intituleMarche(creee.getIntituleMarche())
                .referenceAppelOffre("AO-2026-016")
                .build();

        assertThat(service.update(creee.getId(), maj, ac).getReferenceAppelOffre()).isEqualTo("AO-2026-016");
    }

    @Test
    @Transactional
    void lAdministrateurPeutLaCorriger() {
        DemandeCorrectionDto creee = service.create(creation(null), ac);
        AdminCorrectionDemandeCorrectionRequest corr = new AdminCorrectionDemandeCorrectionRequest();
        corr.setReferenceAppelOffre("AO-2026-099");

        assertThat(service.adminCorrectInfo(creee.getId(), corr, "Référence oubliée au dépôt", admin)
                .getReferenceAppelOffre()).isEqualTo("AO-2026-099");
    }

    private CreateDemandeCorrectionRequest creation(String referenceAppelOffre) {
        return CreateDemandeCorrectionRequest.builder()
                .autoriteContractanteId(autoriteRepository.findByCode("AC_DEFAULT").orElseThrow().getId())
                .entrepriseId(entrepriseRepository.findByNif("NIF_DEFAULT").orElseThrow().getId())
                .conventionId(conventionRepository.findByReference("CONV-DEFAULT").orElseThrow().getId())
                .intituleMarche("Réhabilitation de la voirie urbaine")
                .referenceAppelOffre(referenceAppelOffre)
                .brouillon(true)
                .build();
    }

    private AuthenticatedUser compte(String username) {
        Utilisateur u = utilisateurRepository.findByUsername(username).orElseThrow();
        return new AuthenticatedUser(u.getId(), u.getUsername(), u.getRole());
    }
}
