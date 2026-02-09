package mr.gov.finances.sgci.config;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.repository.UtilisateurRepository;

import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UtilisateurRepository utilisateurRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (utilisateurRepository.findByUsername("admin").isEmpty()) {
            Utilisateur admin = Utilisateur.builder()
                    .username("admin")
                    .passwordHash(passwordEncoder.encode("admin"))
                    .role(Role.PRESIDENT)
                    .nomComplet("Administrateur SGCI")
                    .actif(true)
                    .build();
            utilisateurRepository.save(admin);
        }
    }
}
