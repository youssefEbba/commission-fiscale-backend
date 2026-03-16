package mr.gov.finances.sgci.web.dto;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ForexRateResponse {

    private String from;
    private String to;
    private BigDecimal rate;
    private String source;
}
