package mr.gov.finances.sgci.service.archive;

import mr.gov.finances.sgci.web.dto.archive.ReleveArchiveDto;

/**
 * Relevé d'un crédit lu dans un export de période, avec sa place dans la feuille
 * (numéros de ligne tels qu'Excel les affiche, à partir de 1).
 */
public record BlocReleve(int ligneDebut, int ligneFin, ReleveArchiveDto releve) {
}
