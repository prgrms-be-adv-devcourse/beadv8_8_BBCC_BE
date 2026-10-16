package com.bbcc.kidly.boundedcontext.product.app.product;

import com.bbcc.kidly.boundedcontext.product.app.port.product.ProductImageStorage;
import com.bbcc.kidly.boundedcontext.product.app.port.product.ProductImageUploadUrl;
import com.bbcc.kidly.boundedcontext.product.app.product.dto.ProductImageUploadUrlRequest;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductImageUpload;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProductImageUploadService {

    private final ProductImageStorage imageStorage;

    // DB를 쓰지 않으므로 트랜잭션을 열지 않는다
    public List<ProductImageUploadUrl> issue(long sellerId, ProductImageUploadUrlRequest request) {
        List<ProductImageUploadUrl> issued = new ArrayList<>();
        for (ProductImageUploadUrlRequest.FileInput file : request.files()) {
            ProductImageUpload upload = ProductImageUpload.of(file.contentType(), file.contentLength());
            issued.add(imageStorage.issueUploadUrl(upload, sellerId));
        }
        return issued;
    }
}
