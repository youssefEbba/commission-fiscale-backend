package mr.gov.finances.sgci.config;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import mr.gov.finances.sgci.domain.entity.NumerosJustificatifs;
import mr.gov.finances.sgci.domain.entity.UtilisationDouaniere;
import mr.gov.finances.sgci.domain.entity.UtilisationTVAInterieure;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Une utilisation peut porter plusieurs numéros de déclaration ou de facture. Celles enregistrées
 * avant n'en ont qu'un, dans l'ancien champ : il est recopié dans la liste au démarrage. Idempotent :
 * seules les utilisations dont la liste est vide et l'ancien champ rempli sont touchées.
 */
@Component
@Slf4j
public class NumerosJustificatifsMigration {

    @PersistenceContext
    private EntityManager em;

    @Transactional
    public void migrateIfNeeded() {
        List<UtilisationDouaniere> douane = em.createQuery(
                "select d from UtilisationDouaniere d where d.numeroDeclaration is not null"
                        + " and d.numeroDeclaration <> '' and d.numerosDeclaration is empty", UtilisationDouaniere.class)
                .getResultList();
        douane.forEach(d -> d.remplacerNumerosDeclaration(NumerosJustificatifs.depuis(null, d.getNumeroDeclaration())));
        List<UtilisationTVAInterieure> tva = em.createQuery(
                "select t from UtilisationTVAInterieure t where t.numeroFacture is not null"
                        + " and t.numeroFacture <> '' and t.numerosFacture is empty", UtilisationTVAInterieure.class)
                .getResultList();
        tva.forEach(t -> t.remplacerNumerosFacture(NumerosJustificatifs.depuis(null, t.getNumeroFacture())));
        if (!douane.isEmpty() || !tva.isEmpty()) {
            log.info("Numéros justificatifs : {} déclaration(s) et {} facture(s) recopiée(s) dans les listes",
                    douane.size(), tva.size());
        }
    }
}
