package mr.gov.finances.sgci.service.reporting;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.entity.ValeurReferenceDigitalisation;
import mr.gov.finances.sgci.domain.enums.AuditAction;
import mr.gov.finances.sgci.domain.enums.IndicateurReference;
import mr.gov.finances.sgci.repository.ValeurReferenceDigitalisationRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.AuditService;
import mr.gov.finances.sgci.web.dto.reporting.ValeurReferenceDto;
import mr.gov.finances.sgci.web.exception.ApiErrorCode;
import mr.gov.finances.sgci.web.exception.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Les valeurs d'avant la digitalisation, saisies par l'administrateur : une par indicateur.
 *
 * <p>La liste rend tous les indicateurs, saisis ou non, pour que l'écran de saisie montre ce qui
 * reste à renseigner.
 */
@Service
@RequiredArgsConstructor
public class ValeurReferenceService {

    private static final Map<IndicateurReference, String[]> LIBELLES = Map.of(
            IndicateurReference.DELAI_MOYEN_CORRECTION_JOURS, new String[] {"Délai moyen d'une correction", "jours"},
            IndicateurReference.DELAI_MOYEN_MISE_EN_PLACE_JOURS, new String[] {"Délai moyen d'une mise en place", "jours"},
            IndicateurReference.DELAI_MOYEN_UTILISATION_JOURS, new String[] {"Délai moyen d'une utilisation", "jours"},
            IndicateurReference.TAUX_VALIDATION_CORRECTION_PCT, new String[] {"Taux d'adoption des corrections", "%"},
            IndicateurReference.TAUX_REJET_CORRECTION_PCT, new String[] {"Taux de rejet des corrections", "%"},
            IndicateurReference.TAUX_COMPLEMENTS_PCT, new String[] {"Taux de demandes de compléments", "%"},
            IndicateurReference.DEMANDES_TRAITEES_PAR_AN, new String[] {"Demandes traitées par an", "dossiers"});

    private final ValeurReferenceDigitalisationRepository repository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<ValeurReferenceDto> lister() {
        return Arrays.stream(IndicateurReference.values())
                .map(code -> repository.findByCode(code)
                        .map(this::toDto)
                        .orElseGet(() -> ValeurReferenceDto.builder()
                                .code(code)
                                .libelle(LIBELLES.get(code)[0])
                                .unite(LIBELLES.get(code)[1])
                                .build()))
                .toList();
    }

    /** Crée ou remplace la valeur de l'indicateur. */
    @Transactional
    public ValeurReferenceDto enregistrer(ValeurReferenceDto request, AuthenticatedUser user) {
        if (request == null || request.getCode() == null || request.getValeur() == null) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED, "L'indicateur et la valeur sont obligatoires");
        }
        if (request.getValeur().signum() < 0) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED, "La valeur ne peut pas être négative");
        }
        ValeurReferenceDigitalisation entity = repository.findByCode(request.getCode())
                .orElseGet(() -> ValeurReferenceDigitalisation.builder().code(request.getCode()).build());
        entity.setValeur(request.getValeur());
        entity.setPeriodeSource(request.getPeriodeSource());
        entity.setCommentaire(request.getCommentaire());
        entity.setSaisiParUtilisateurId(user != null ? user.getUserId() : null);
        entity.setDateSaisie(Instant.now());
        entity = repository.save(entity);
        ValeurReferenceDto dto = toDto(entity);
        auditService.log(AuditAction.UPDATE, "ValeurReferenceDigitalisation", String.valueOf(entity.getId()), dto);
        return dto;
    }

    @Transactional
    public void supprimer(IndicateurReference code) {
        repository.findByCode(code).ifPresent(entity -> {
            repository.delete(entity);
            auditService.log(AuditAction.DELETE, "ValeurReferenceDigitalisation", String.valueOf(entity.getId()), toDto(entity));
        });
    }

    private ValeurReferenceDto toDto(ValeurReferenceDigitalisation e) {
        String[] libelle = LIBELLES.get(e.getCode());
        return ValeurReferenceDto.builder()
                .id(e.getId())
                .code(e.getCode())
                .libelle(libelle[0])
                .unite(libelle[1])
                .valeur(e.getValeur())
                .periodeSource(e.getPeriodeSource())
                .commentaire(e.getCommentaire())
                .dateSaisie(e.getDateSaisie())
                .build();
    }
}
