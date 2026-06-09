package mr.gov.finances.sgci.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateExplicationMessageRequest {

    @NotBlank
    @Size(max = 2000)
    private String message;
}
