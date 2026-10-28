package com.bbcc.kidly.boundedcontext.product.out.search;

import com.bbcc.kidly.boundedcontext.product.domain.search.ProductSearchDocument;
import com.bbcc.kidly.boundedcontext.product.domain.search.ProductSearchDocumentRepository;
import org.springframework.data.jpa.repository.JpaRepository;

// saveIfNewer는 JpaProductSearchDocumentRepositoryImpl이 구현한다
public interface JpaProductSearchDocumentRepository
    extends JpaRepository<ProductSearchDocument, Long>, ProductSearchDocumentRepository {
}
