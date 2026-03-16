package mr.gov.finances.sgci.domain.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "devise")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Devise {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 10)
    private String code;

    @Column(nullable = false)
    private String libelle;

    private String symbole;
}
