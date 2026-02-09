package mr.gov.finances.sgci.security;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.repository.UtilisateurRepository;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UtilisateurRepository utilisateurRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Utilisateur u = utilisateurRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Utilisateur non trouvé: " + username));
        if (Boolean.FALSE.equals(u.getActif())) {
            throw new UsernameNotFoundException("Compte désactivé");
        }
        return new User(
                u.getUsername(),
                u.getPasswordHash(),
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + u.getRole().name()))
        );
    }
}
