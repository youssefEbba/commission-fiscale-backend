package mr.gov.finances.sgci.web.dto;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ForexConvertResponse {

    private String from;
    private String to;
    private BigDecimal amount;
    private BigDecimal result;
    private String source;
}
