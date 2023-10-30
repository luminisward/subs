package xyz.monado.subs;


import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

@Data
public class QueryParams {
    @NotEmpty(message = "Url cannot be empty")
    private String url;

    private ClientType clientType;
}
