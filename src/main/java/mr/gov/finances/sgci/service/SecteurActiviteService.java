package mr.gov.finances.sgci.service;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.entity.SecteurActivite;
import mr.gov.finances.sgci.domain.enums.AuditAction;
import mr.gov.finances.sgci.repository.SecteurActiviteRepository;
import mr.gov.finances.sgci.web.dto.SecteurActiviteDto;
import mr.gov.finances.sgci.web.exception.ApiErrorCode;
import mr.gov.finances.sgci.web.exception.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/**
 * Le référentiel des secteurs d'activité. Un secteur ne se supprime pas : il se désactive, pour que
 * les entreprises qui le portent gardent leur classement dans l'historique des tableaux de bord.
 */
@Service
@RequiredArgsConstructor
public class SecteurActiviteService {

    /** Le référentiel de départ : les secteurs des marchés publics que la Commission rencontre. */
    private static final String[][] SECTEURS_INITIAUX = {
            {"BTP", "Bâtiment et travaux publics", "البناء والأشغال العامة"},
            {"ENERGIE", "Énergie", "الطاقة"},
            {"EAU_ASSAINISSEMENT", "Eau et assainissement", "المياه والصرف الصحي"},
            {"SANTE", "Santé", "الصحة"},
            {"EDUCATION", "Éducation et formation", "التعليم والتكوين"},
            {"TRANSPORT", "Transport et logistique", "النقل واللوجستيك"},
            {"AGRICULTURE", "Agriculture, élevage et pêche", "الزراعة والتنمية الحيوانية والصيد"},
            {"MINES", "Mines et hydrocarbures", "المعادن والمحروقات"},
            {"TELECOMS", "Télécommunications et numérique", "الاتصالات والرقمنة"},
            {"SERVICES", "Services et conseil", "الخدمات والاستشارات"},
            {"AUTRE", "Autre", "أخرى"}
    };

    private final SecteurActiviteRepository repository;
    private final AuditService auditService;

    /** Amorçage idempotent : n'ajoute que les codes absents, ne retouche aucun libellé. */
    @Transactional
    public void seedInitial() {
        for (String[] s : SECTEURS_INITIAUX) {
            if (!repository.existsByCode(s[0])) {
                repository.save(SecteurActivite.builder().code(s[0]).libelle(s[1]).libelleAr(s[2]).actif(true).build());
            }
        }
    }

    @Transactional(readOnly = true)
    public List<SecteurActiviteDto> lister(boolean actifsSeulement) {
        return repository.findAllByOrderByLibelleAsc().stream()
                .filter(s -> !actifsSeulement || s.isActif())
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public SecteurActiviteDto creer(SecteurActiviteDto dto) {
        String code = normaliserCode(dto.getCode());
        if (repository.existsByCode(code)) {
            throw ApiException.conflict(ApiErrorCode.CONFLICT, "Un secteur porte déjà le code " + code);
        }
        SecteurActivite entity = repository.save(SecteurActivite.builder()
                .code(code)
                .libelle(dto.getLibelle().trim())
                .libelleAr(dto.getLibelleAr())
                .actif(dto.getActif() == null || dto.getActif())
                .build());
        SecteurActiviteDto result = toDto(entity);
        auditService.log(AuditAction.CREATE, "SecteurActivite", String.valueOf(entity.getId()), result);
        return result;
    }

    @Transactional
    public SecteurActiviteDto modifier(Long id, SecteurActiviteDto dto) {
        SecteurActivite entity = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Secteur non trouvé : " + id));
        entity.setLibelle(dto.getLibelle().trim());
        entity.setLibelleAr(dto.getLibelleAr());
        if (dto.getActif() != null) {
            entity.setActif(dto.getActif());
        }
        entity = repository.save(entity);
        SecteurActiviteDto result = toDto(entity);
        auditService.log(AuditAction.UPDATE, "SecteurActivite", String.valueOf(id), result);
        return result;
    }

    private static String normaliserCode(String code) {
        String normalise = code == null ? "" : code.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_");
        if (normalise.isBlank()) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED, "Le code du secteur est obligatoire");
        }
        return normalise;
    }

    private SecteurActiviteDto toDto(SecteurActivite s) {
        return SecteurActiviteDto.builder()
                .id(s.getId())
                .code(s.getCode())
                .libelle(s.getLibelle())
                .libelleAr(s.getLibelleAr())
                .actif(s.isActif())
                .build();
    }
}
