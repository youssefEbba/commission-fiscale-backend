package mr.gov.finances.sgci.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Enveloppe de pagination générique (indépendante de Spring Data) pour les réponses de liste paginées.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PageResponse<T> {

    private List<T> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;

    public static <T> PageResponse<T> of(List<T> allSorted, int page, int size) {
        int safeSize = size <= 0 ? 20 : size;
        int safePage = Math.max(page, 0);
        long total = allSorted.size();
        int totalPages = (int) Math.ceil((double) total / safeSize);
        int fromIndex = Math.min(safePage * safeSize, allSorted.size());
        int toIndex = Math.min(fromIndex + safeSize, allSorted.size());
        List<T> slice = allSorted.subList(fromIndex, toIndex);
        return PageResponse.<T>builder()
                .content(slice)
                .page(safePage)
                .size(safeSize)
                .totalElements(total)
                .totalPages(totalPages)
                .build();
    }
}
