package mr.gov.finances.sgci.domain.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Compteur de séquence persistant pour la génération de références lisibles (ex. {@code DC-01/2025}).
 * Une ligne par couple préfixe + année ({@link #sequenceKey}, ex. {@code DC-2025}).
 */
@Entity
@Table(name = "reference_sequence",
        uniqueConstraints = @UniqueConstraint(name = "uk_reference_sequence_key", columnNames = "sequence_key"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReferenceSequence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sequence_key", nullable = false, unique = true, length = 50)
    private String sequenceKey;

    @Column(name = "current_value", nullable = false)
    @Builder.Default
    private long currentValue = 0L;
}
