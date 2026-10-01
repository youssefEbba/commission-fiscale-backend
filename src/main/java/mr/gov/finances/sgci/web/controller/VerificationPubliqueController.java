package mr.gov.finances.sgci.web.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.service.VerificationPubliqueService;
import mr.gov.finances.sgci.web.dto.VerificationPubliqueDto;
import mr.gov.finances.sgci.web.exception.ApiErrorCode;
import mr.gov.finances.sgci.web.exception.ApiException;
import mr.gov.finances.sgci.web.support.VerificationPubliqueRateLimiter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Vérification publique d'un document officiel, depuis le QR code qu'il porte.
 *
 * <p><b>Seul endpoint métier ouvert sans authentification</b>, et c'est délibéré : un agent du
 * Trésor, de la DGI ou de la douane doit pouvoir contrôler une pièce au guichet avec son téléphone,
 * sans compte sur l'application. Le QR encode l'URL de l'écran public de vérification, qui appelle
 * cette route.
 *
 * <p>La réponse est réduite à ce qui atteste la pièce — authenticité, état, date, bénéficiaire — et
 * ne contient <b>aucun montant ni solde</b>. C'est cette pauvreté, plus que la limitation de débit,
 * qui rend l'ouverture acceptable.
 *
 * <p>Toute réponse est un 200, y compris pour un code inconnu : un scanner a besoin d'un résultat
 * lisible, pas d'un 404.
 */
@RestController
@RequestMapping("/api/public/verification")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class VerificationPubliqueController {

    private final VerificationPubliqueService service;
    private final VerificationPubliqueRateLimiter rateLimiter;

    @GetMapping
    public VerificationPubliqueDto verifier(@RequestParam("code") String code,
                                            HttpServletRequest request) {
        if (!rateLimiter.autorise(adresseAppelante(request))) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS.value(), ApiErrorCode.TROP_DE_REQUETES,
                    "Trop de vérifications depuis cette adresse. Réessayez dans une minute.");
        }
        return service.verifier(code);
    }

    /**
     * Adresse de l'appelant, en tenant compte d'un éventuel proxy.
     *
     * <p>{@code X-Forwarded-For} est déclaré par le client et donc falsifiable : il n'est retenu que
     * parce que l'application tourne derrière nginx, qui le réécrit. Sans ce reverse proxy, la
     * limitation serait contournable d'un simple en-tête.
     */
    private static String adresseAppelante(HttpServletRequest request) {
        String transmise = request.getHeader("X-Forwarded-For");
        if (transmise != null && !transmise.isBlank()) {
            int virgule = transmise.indexOf(',');
            return (virgule > 0 ? transmise.substring(0, virgule) : transmise).trim();
        }
        return request.getRemoteAddr();
    }
}
