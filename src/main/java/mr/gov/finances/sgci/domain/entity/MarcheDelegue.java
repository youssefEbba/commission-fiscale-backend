package mr.gov.finances.sgci.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "marche_delegue",
        uniqueConstraints = @UniqueConstraint(name = "uk_marche_delegue", columnNames = {"marche_id", "delegue_id"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MarcheDelegue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "marche_id", nullable = false)
    private Marche marche;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delegue_id", nullable = false)
    private Utilisateur delegue;
}
