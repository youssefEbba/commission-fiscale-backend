package mr.gov.finances.sgci.web.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.enums.TypeEntiteRattachement;
import mr.gov.finances.sgci.service.DemandeRattachementService;
import mr.gov.finances.sgci.web.dto.CreateDemandeRattachementRequest;
import mr.gov.finances.sgci.web.dto.DemandeRattachementDto;
import mr.gov.finances.sgci.web.dto.SuiviRattachementDto;
import mr.gov.finances.sgci.web.dto.DocumentRequirementDto;
import mr.gov.finances.sgci.web.dto.EntitePubliqueDto;
import mr.gov.finances.sgci.web.exception.ApiErrorCode;
import mr.gov.finances.sgci.web.exception.ApiException;
import mr.gov.finances.sgci.web.support.VerificationPubliqueRateLimiter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.util.List;

/**
 * Dépôt d'une demande de rattachement par une personne qui n'a pas encore de compte.
 *
 * <p>Deuxième surface publique du projet après {@link VerificationPubliqueController}, et la seule
 * qui écrive. Les trois routes sont donc limitées en débit par adresse, et la recherche d'entités ne
 * rend que l'identifiant et le nom : ouverte à tous, elle ne doit pas devenir un annuaire
 * d'entreprises.
 *
 * <p>Le dépôt est <strong>une seule requête</strong> multipart portant les champs et les
 * justificatifs. Un dépôt en deux temps laisserait des demandes à moitié créées, sans session pour
 * les reprendre puisque le demandeur n'est pas authentifié.
 */
@RestController
@RequestMapping("/api/public/rattachement")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class RattachementPublicController {

    private final DemandeRattachementService service;
    private final VerificationPubliqueRateLimiter rateLimiter;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DemandeRattachementDto deposer(@Valid @ModelAttribute CreateDemandeRattachementRequest request,
                                          MultipartHttpServletRequest requete) {
        assertDebitAutorise(requete);
        // Chaque fichier est nommé par son code document : la clé de la map porte le code, ce qui
        // évite d'aligner un tableau de fichiers sur un tableau de codes.
        return service.deposer(request, requete.getFileMap());
    }

    /** Les justificatifs attendus, pour que le formulaire les affiche sans authentification. */
    @GetMapping("/pieces")
    public List<DocumentRequirementDto> pieces(jakarta.servlet.http.HttpServletRequest requete) {
        assertDebitAutorise(requete);
        return service.piecesAttendues();
    }

    @GetMapping("/entites")
    public List<EntitePubliqueDto> entites(@RequestParam TypeEntiteRattachement type,
                                           @RequestParam(required = false) String q,
                                           jakarta.servlet.http.HttpServletRequest requete) {
        assertDebitAutorise(requete);
        return service.rechercherEntites(type, q);
    }

    private void assertDebitAutorise(jakarta.servlet.http.HttpServletRequest requete) {
        if (!rateLimiter.autorise(VerificationPubliqueRateLimiter.adresseAppelante(requete))) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS.value(), ApiErrorCode.TROP_DE_REQUETES,
                    "Trop de requêtes depuis cette adresse. Réessayez dans une minute.");
        }
    }

    /**
     * Le suivi d'une demande, sans compte : référence + courriel du dépôt.
     *
     * <p>Les deux sont exigés parce que la référence est séquentielle, donc devinable. Une
     * référence inconnue et un courriel qui ne correspond pas rendent la même erreur, pour ne pas
     * confirmer l'existence d'une référence à qui la cherche.
     */
    @GetMapping("/suivi")
    public SuiviRattachementDto suivi(@RequestParam String reference, @RequestParam String email) {
        return service.suivre(reference, email);
    }
}
