package mr.gov.finances.sgci.service;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.web.dto.SousTraitantUtilisateurDto;
import mr.gov.finances.sgci.web.dto.UtilisateurDto;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UtilisateurService {

    private final UtilisateurRepository utilisateurRepository;

    @Transactional(readOnly = true)
    public List<UtilisateurDto> findAll() {
        return utilisateurRepository.findAll()
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<SousTraitantUtilisateurDto> findSousTraitants() {
        return utilisateurRepository.findByRole(Role.SOUS_TRAITANT)
                .stream()
                .map(u -> SousTraitantUtilisateurDto.builder()
                        .id(u.getId())
                        .username(u.getUsername())
                        .nomComplet(u.getNomComplet())
                        .email(u.getEmail())
                        .actif(u.getActif())
                        .entrepriseId(u.getEntreprise() != null ? u.getEntreprise().getId() : null)
                        .entrepriseRaisonSociale(u.getEntreprise() != null ? u.getEntreprise().getRaisonSociale() : null)
                        .entrepriseNif(u.getEntreprise() != null ? u.getEntreprise().getNif() : null)
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<UtilisateurDto> findPending() {
        return utilisateurRepository.findAll()
                .stream()
                .filter(u -> Boolean.FALSE.equals(u.getActif()))
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public UtilisateurDto setActif(Long id, boolean actif) {
        Utilisateur u = utilisateurRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé: " + id));
        u.setActif(actif);
        u = utilisateurRepository.save(u);
        return toDto(u);
    }

    private UtilisateurDto toDto(Utilisateur u) {
        return UtilisateurDto.builder()
                .id(u.getId())
                .username(u.getUsername())
                .role(u.getRole())
                .nomComplet(u.getNomComplet())
                .email(u.getEmail())
                .actif(u.getActif())
                .build();
    }
}

