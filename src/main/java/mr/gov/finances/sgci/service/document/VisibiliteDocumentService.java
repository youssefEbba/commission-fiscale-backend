package mr.gov.finances.sgci.service.document;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.document.OuvertureDocumentsPolicy;
import mr.gov.finances.sgci.domain.entity.DocumentRequirement;
import mr.gov.finances.sgci.domain.entity.UtilisationCredit;
import mr.gov.finances.sgci.domain.enums.ProcessusDocument;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.domain.enums.SensDocument;
import mr.gov.finances.sgci.repository.DocumentRequirementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Qui voit quelle pièce, et à partir de quand.
 *
 * <p>La règle tient en une phrase : <b>une pièce de sortie n'est communicable au déposant que
 * lorsque le statut du dossier ouvre les pièces</b> ; tout le reste est visible.
 *
 * <p>Elle remplace une politique statique qui portait deux listes de codes en dur et ne couvrait que
 * les utilisations de crédit. Le classement vit désormais dans le référentiel, paramétrable depuis
 * l'écran de configuration de la GED, et la règle vaut pour tous les processus.
 *
 * <p>Deux invariants sont repris de la politique d'origine, parce qu'ils sont justes :
 *
 * <ul>
 *   <li><b>Les directions voient tout, toujours.</b> Elles instruisent le dossier ; leur masquer une
 *       pièce les empêcherait de travailler.</li>
 *   <li><b>Une pièce non classée reste visible.</b> Le défaut penche du côté de la transparence. Ce
 *       qui garantit qu'aucune pièce n'est oubliée, c'est un test de couverture du référentiel —
 *       pas le défaut lui-même, qui doit rester le moins nuisible des deux.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VisibiliteDocumentService {

    private final DocumentRequirementRepository requirementRepository;

    /**
     * Filtre une liste de pièces pour ce rôle, en une seule lecture du référentiel.
     *
     * <p>Une lecture par liste et non par document : la table est petite, mais une liste de vingt
     * pièces ne doit pas coûter vingt requêtes.
     *
     * @param codeDe comment lire le code documentaire d'un élément de la liste
     */
    public <T> List<T> filtrer(ProcessusDocument processus, Object statutDuDossier, Role role,
                               List<T> documents, Function<T, String> codeDe) {
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }
        if (acteurVoitTout(role)) {
            return documents;
        }
        if (OuvertureDocumentsPolicy.ouvertesPour(processus, statutDuDossier)) {
            return documents;
        }
        Map<String, SensDocument> sens = sensParCode(processus);
        List<T> out = new ArrayList<>(documents.size());
        for (T document : documents) {
            String code = codeDe.apply(document);
            if (sens.getOrDefault(code, SensDocument.ENTREE) != SensDocument.SORTIE) {
                out.add(document);
            }
        }
        return out;
    }

    /** La même décision, pour une pièce isolée — téléchargement, aperçu. */
    public boolean visiblePour(ProcessusDocument processus, String codeDocument,
                               Object statutDuDossier, Role role) {
        if (role == null) {
            return false;
        }
        if (acteurVoitTout(role)) {
            return true;
        }
        if (OuvertureDocumentsPolicy.ouvertesPour(processus, statutDuDossier)) {
            return true;
        }
        return sensParCode(processus).getOrDefault(codeDocument, SensDocument.ENTREE)
                != SensDocument.SORTIE;
    }

    /**
     * Les administrations instruisent le dossier : rien ne leur est masqué.
     *
     * <p>Les autorités contractantes n'y figurent pas, et c'est voulu : elles sont du côté du
     * déposant pour les pièces que la Commission produit.
     */
    public static boolean acteurVoitTout(Role role) {
        return role == Role.DGD || role == Role.DGTCP || role == Role.DGI
                || role == Role.DGB || role == Role.PRESIDENT || role == Role.ADMIN_SI;
    }

    /**
     * Le processus documentaire d'une utilisation, selon son type.
     *
     * <p>Publique et partagée : la même résolution existait déjà en privé dans deux services, et
     * trois chemins en avaient besoin. Une troisième copie aurait fini par diverger des deux
     * autres — c'est exactement ce qui est arrivé au classement entrée/sortie, éparpillé en trois
     * endroits avant ce lot.
     */
    public static ProcessusDocument processusDe(UtilisationCredit utilisation) {
        if (utilisation == null || utilisation.getType() == null) {
            return ProcessusDocument.UTILISATION_CI;
        }
        return switch (utilisation.getType()) {
            case DOUANIER -> ProcessusDocument.UTILISATION_CI_DOUANE;
            case TVA_INTERIEURE -> ProcessusDocument.UTILISATION_CI_TVA_INTERIEURE;
        };
    }

    private Map<String, SensDocument> sensParCode(ProcessusDocument processus) {
        Map<String, SensDocument> out = new HashMap<>();
        if (processus == null) {
            return out;
        }
        for (DocumentRequirement req : requirementRepository.findByProcessusOrderByOrdreAffichageAsc(processus)) {
            out.put(req.getCodeDocument(), req.sensEffectif());
        }
        return out;
    }
}
