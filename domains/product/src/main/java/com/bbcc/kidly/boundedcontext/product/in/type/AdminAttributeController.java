package com.bbcc.kidly.boundedcontext.product.in.type;

import com.bbcc.kidly.boundedcontext.product.app.type.AttributeService;
import com.bbcc.kidly.boundedcontext.product.app.type.dto.AttributeCreateRequest;
import com.bbcc.kidly.boundedcontext.product.app.type.dto.AttributeValueRequest;
import com.bbcc.kidly.boundedcontext.product.in.type.dto.AttributeResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/attributes")
@RequiredArgsConstructor
public class AdminAttributeController {

    private final AttributeService attributeService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AttributeResponse create(
        @Valid @RequestBody AttributeCreateRequest request, HttpServletResponse response) {
        var result = attributeService.create(request);
        response.setHeader(HttpHeaders.LOCATION, "/api/v1/admin/attributes/" + result.attributeId());
        return AttributeResponse.from(result);
    }

    @GetMapping
    public List<AttributeResponse> list() {
        return attributeService.findAll().stream().map(AttributeResponse::from).toList();
    }

    @PostMapping("/{attributeId}/values")
    @ResponseStatus(HttpStatus.CREATED)
    public AttributeResponse.Value addValue(
        @PathVariable Long attributeId,
        @Valid @RequestBody AttributeValueRequest request,
        HttpServletResponse response) {
        var result = attributeService.addValue(attributeId, request);
        response.setHeader(
            HttpHeaders.LOCATION, "/api/v1/admin/attributes/" + attributeId + "/values/" + result.valueId());
        return AttributeResponse.Value.from(result);
    }

    @DeleteMapping("/{attributeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long attributeId) {
        attributeService.delete(attributeId);
    }

    @DeleteMapping("/{attributeId}/values/{valueId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteValue(@PathVariable Long attributeId, @PathVariable Long valueId) {
        attributeService.deleteValue(attributeId, valueId);
    }
}
