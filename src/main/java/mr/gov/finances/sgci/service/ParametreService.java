package mr.gov.finances.sgci.service;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.entity.ParametreApplication;
import mr.gov.finances.sgci.domain.enums.AuditAction;
import mr.gov.finances.sgci.repository.ParametreApplicationRepository;
import mr.gov.finances.sgci.web.dto.ParametreDto;
import mr.gov.finances.sgci.web.exception.ApiErrorCode;
import mr.gov.finances.sgci.web.exception.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Les réglages de l'application : lecture publique restreinte, écriture contrôlée.
 *
 * <p><b>La liste des clés publiques est en dur ici, et ce n'est pas un détail.</b> Un drapeau
 * « publiable » en base aurait été modifiable depuis l'écran : le jour où un réglage sensible est
 * ajouté, un clic suffirait à l'exposer sans authentification, et personne ne s'en apercevrait.
 * Rendre un réglage public demande donc une modification de code — donc une relecture.
 *
 * <p><b>Le référentiel des clés est fermé.</b> Modifier une clé inconnue est refusé, plutôt que de
 * la créer : sans cela la table deviendrait un dépotoir, et on ne saurait plus ce qui est lu, par
 * quel écran, ni si une clé encore présente sert encore à quelque chose.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ParametreService {

    /** Coordonnées affichées en pied de page, y compris sur la page de connexion. */
    public static final String CONTACT_ADRESSE = "contact.adresse";
    public static final String CONTACT_EMAIL = "contact.email";
    public static final String CONTACT_TELEPHONE = "contact.telephone";

    /**
     * Les seules clés lisibles sans authentification.
     *
     * <p>Des coordonnées de contact sont publiques par nature — elles figurent déjà sur les
     * documents de la Commission. Tout le reste exige un compte.
     */
    private static final Set<String> CLES_PUBLIQUES =
            Set.of(CONTACT_ADRESSE, CONTACT_EMAIL, CONTACT_TELEPHONE);

    /** Le référentiel fermé : seules ces clés existent, et aucune autre ne peut être écrite. */
    private static final Set<String> CLES_CONNUES = CLES_PUBLIQUES;

    private final ParametreApplicationRepository repository;
    private final AuditService auditService;

    /** Lecture sans authentification : rien d'autre que les clés de la liste blanche. */
    public Map<String, String> lirePubliques() {
        Map<String, String> out = new LinkedHashMap<>();
        for (ParametreApplication p : repository.findAllByOrderByCleAsc()) {
            if (CLES_PUBLIQUES.contains(p.getCle())) {
                out.put(p.getCle(), p.getValeur());
            }
        }
        return out;
    }

    public List<ParametreDto> lireToutes() {
        return repository.findAllByOrderByCleAsc().stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional
    public ParametreDto modifier(String cle, String valeur) {
        if (cle == null || !CLES_CONNUES.contains(cle)) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED,
                    "Réglage inconnu : " + cle + ". Les réglages sont un référentiel fermé, "
                            + "une nouvelle clé s'ajoute par livraison et non par appel.");
        }
        ParametreApplication entity = repository.findByCle(cle)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND,
                        "Réglage non initialisé : " + cle));
        entity.setValeur(valeur);
        entity = repository.save(entity);

        ParametreDto dto = toDto(entity);
        auditService.log(AuditAction.UPDATE, "ParametreApplication", cle, dto);
        return dto;
    }

    private ParametreDto toDto(ParametreApplication p) {
        return ParametreDto.builder()
                .cle(p.getCle())
                .valeur(p.getValeur())
                .description(p.getDescription())
                .dateModification(p.getDateModification())
                .publique(CLES_PUBLIQUES.contains(p.getCle()))
                .build();
    }
}
