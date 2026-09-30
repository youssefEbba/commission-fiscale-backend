package mr.gov.finances.sgci.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Les empreintes de l'utilisateur courant, en un seul appel.
 *
 * <p>Évite au client de connaître son propre identifiant puis d'enchaîner deux requêtes par
 * empreinte. Chaque champ peut être nul : le front doit alors n'offrir que le chemin manuel
 * (télécharger le modèle, signer et cacheter à la main, téléverser le scan).
 *
 * <p>Les {@code dataUrl} ne sont renseignés que sur demande explicite ({@code withContent=true}),
 * pour l'écran de composition : un PNG de 1 Mo pèse environ 1,3 Mo une fois encodé en base64.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MesEmpreintesDto {

    /** Métadonnées de la signature active, {@code null} si aucune n'a été déposée. */
    private SignatureDto signature;

    /** Métadonnées du cachet actif — personnel, ou générique du rôle à défaut. */
    private SignatureDto cachet;

    /** {@code data:image/png;base64,…}, uniquement si {@code withContent=true}. */
    private String signatureDataUrl;

    private String cachetDataUrl;

    /** {@code true} si le cachet résolu est l'empreinte générique du rôle, pas une empreinte personnelle. */
    private boolean cachetGeneriqueDuRole;
}
