package mr.gov.finances.sgci.web.dto.archive;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Correction et suppression d'un crédit repris de l'archive ({@code /api/archive/credits/{certificatId}}),
 * réservées à l'administrateur ({@code archive.import}).
 */
public final class ArchiveCreditAdminDtos {

    private ArchiveCreditAdminDtos() {
    }

    /** Le rattachement voulu : le marché est facultatif ({@code null} = aucun marché). */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RattachementRequest {
        @NotNull
        private Long entrepriseId;
        @NotNull
        private Long autoriteContractanteId;
        @NotNull
        private Long conventionId;
        private Long marcheId;
        @NotBlank
        private String motif;
    }

    /** Un crédit d'archive : son rattachement actuel et ce qu'une suppression effacerait. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ArchiveCreditDto {
        private Long certificatId;
        private String reference;
        private Long demandeCorrectionId;

        private Long entrepriseId;
        private String entrepriseRaisonSociale;
        private String entrepriseNif;
        private Long autoriteContractanteId;
        private String autoriteContractanteNom;
        private Long conventionId;
        private String conventionReference;
        private String conventionIntitule;
        private Long marcheId;
        private String marcheNumero;
        /** Le marché a été créé par l'import : la suppression le supprime aussi. */
        private boolean marcheCreeParImport;

        private SuppressionApercu suppression;
    }

    /** Ce que « Supprimer définitivement » efface, pour que la confirmation le dise. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SuppressionApercu {
        /** Utilisations reprises de l'archive. */
        private long utilisationsArchive;
        /** Utilisations créées dans l'application après l'import. */
        private long utilisationsApplication;
        private long transferts;
        private long avenants;
        private long sousTraitances;
        private long clotures;
        /** Pièces jointes de toutes ces demandes (certificat, utilisations, transfert, avenants…). */
        private long pieces;
        private long lignesStockTva;
        private long echanges;
        /** Crédits de lot qui redeviendront importables. */
        private long creditsDeLot;
    }
}
