package mr.gov.finances.sgci;

import mr.gov.finances.sgci.config.NomsRepresentantsMigration;
import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.service.PermissionService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La commission relais crée l'entreprise ou l'autorité qu'elle doit représenter quand elle n'existe
 * pas encore ; les comptes des directions s'appellent « Représentant … ».
 */
@SpringBootTest
@ActiveProfiles("test")
class CommissionRelaisCreationIT {

    @Autowired private PermissionService permissionService;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private NomsRepresentantsMigration migration;

    @Test
    void laCommissionRelaisPeutCreerEntreprisesEtAutorites() {
        assertThat(permissionService.findPermissionCodesByRole(Role.COMMISSION_RELAIS))
                .contains("entreprise.create", "autorite.create",
                        "commission.relais.list.entreprises", "commission.relais.list.autorites");
    }

    @Test
    @Transactional
    void lesComptesDeDirectionDeviennentRepresentantsSansToucherUnNomSaisi() {
        assertThat(utilisateurRepository.findByUsername("dgd").orElseThrow().getNomComplet()).isEqualTo("Représentant DGD");

        String n = String.valueOf(System.nanoTime());
        Utilisateur ancien = utilisateurRepository.save(Utilisateur.builder()
                .username("ancien-dgb-" + n).passwordHash("x").role(Role.DGB).nomComplet("Agent DGB").actif(true).build());
        Utilisateur perso = utilisateurRepository.save(Utilisateur.builder()
                .username("perso-dgb-" + n).passwordHash("x").role(Role.DGB).nomComplet("Agent DGB — Mme Sall").actif(true).build());

        migration.run(null);

        assertThat(utilisateurRepository.findById(ancien.getId()).orElseThrow().getNomComplet()).isEqualTo("Représentant DGB");
        assertThat(utilisateurRepository.findById(perso.getId()).orElseThrow().getNomComplet()).isEqualTo("Agent DGB — Mme Sall");
    }
}
