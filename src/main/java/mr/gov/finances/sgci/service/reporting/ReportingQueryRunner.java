package mr.gov.finances.sgci.service.reporting;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import mr.gov.finances.sgci.web.dto.reporting.NamedCountDto;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Exécute les requêtes d'agrégation du reporting en liant le périmètre.
 *
 * <p>Toutes les méthodes prennent le {@link ReportingScope} : c'est lui qui fournit les paramètres
 * liés, donc une requête dont le HQL aurait oublié le fragment de périmètre
 * ({@link ReportingPredicates}) échoue au liage. JPA refuse {@code setParameter} sur un paramètre
 * que la requête ne déclare pas : l'oubli se solde par une {@code IllegalArgumentException} au
 * premier appel, pas par une fuite silencieuse. C'est le garde-fou réel, et il est involontaire —
 * d'où ce commentaire, pour que personne ne « simplifie » le liage conditionnel.
 *
 * <p>Un périmètre fermé ({@link ReportingScope#perimetreVide()}) est court-circuité sans exécuter
 * la requête : aucune ligne ne peut sortir, même si le fragment a été oublié.
 *
 * <p><b>Règle, pas habitude</b> : toutes les requêtes de reporting sont en projection
 * ({@code select c.statut, count(c)}), jamais {@code select c from CertificatCredit c}. C'est ce
 * qui borne la mémoire, et c'est pourquoi ces méthodes ne renvoient que des scalaires et des
 * {@code Object[]}.
 */
@Component
public class ReportingQueryRunner {

    @PersistenceContext
    private EntityManager entityManager;

    public long countSingle(String hql, ReportingScope scope, Map<String, Object> extra) {
        if (scope.perimetreVide()) {
            return 0L;
        }
        Query q = prepare(hql, scope, extra);
        return ((Number) q.getSingleResult()).longValue();
    }

    /** Comptages groupés sur la première colonne, triés par clé. */
    public List<NamedCountDto> groupedCounts(String hql, ReportingScope scope) {
        List<Object[]> rows = lignes(hql, scope, Map.of());
        List<NamedCountDto> out = new ArrayList<>();
        for (Object[] row : rows) {
            String key = row[0] != null ? row[0].toString() : "NULL";
            out.add(NamedCountDto.builder().key(key).count(((Number) row[1]).longValue()).build());
        }
        out.sort(Comparator.comparing(NamedCountDto::getKey));
        return out;
    }

    /** Une ligne unique d'agrégats, ou {@code null} si le périmètre est fermé. */
    public Object[] uneLigne(String hql, ReportingScope scope, Map<String, Object> extra) {
        if (scope.perimetreVide()) {
            return null;
        }
        return (Object[]) prepare(hql, scope, extra).getSingleResult();
    }

    public List<Object[]> lignes(String hql, ReportingScope scope, Map<String, Object> extra) {
        if (scope.perimetreVide()) {
            return List.of();
        }
        @SuppressWarnings("unchecked")
        List<Object[]> rows = prepare(hql, scope, extra).getResultList();
        return rows != null ? rows : List.of();
    }

    /**
     * Lignes paginées, pour les listes de dossiers des blocs qui en produisent.
     *
     * <p>Les agrégats sont bornés par le référentiel, pas par le nombre de dossiers ; les listes ne
     * le sont pas. C'est la seule méthode qui doit servir à les lire, et l'appelant reste tenu
     * d'inscrire la troncature dans sa sortie plutôt que de couper en silence.
     */
    public List<Object[]> lignesPaginees(String hql, ReportingScope scope, Map<String, Object> extra,
                                         int offset, int limite) {
        if (scope.perimetreVide()) {
            return List.of();
        }
        Query q = prepare(hql, scope, extra);
        q.setFirstResult(Math.max(0, offset));
        q.setMaxResults(Math.max(1, limite));
        @SuppressWarnings("unchecked")
        List<Object[]> rows = q.getResultList();
        return rows != null ? rows : List.of();
    }

    private Query prepare(String hql, ReportingScope scope, Map<String, Object> extra) {
        Query q = entityManager.createQuery(hql);
        // La fenêtre est optionnelle : un bloc peut interroger sans borne de date — une liste de
        // dossiers à fermer, par exemple. Tout autre paramètre de date doit être nommé autrement
        // que « from… » pour que ce test reste exact.
        if (hql.contains(":from")) {
            q.setParameter("from", scope.from());
            q.setParameter("to", scope.to());
        }
        if (scope.autoriteContractanteId() != null) {
            q.setParameter("scopeAc", scope.autoriteContractanteId());
        }
        if (scope.entrepriseId() != null) {
            q.setParameter("scopeEnt", scope.entrepriseId());
        }
        if (scope.delegueUserId() != null) {
            q.setParameter("scopeDelegue", scope.delegueUserId());
        }
        extra.forEach(q::setParameter);
        return q;
    }
}
