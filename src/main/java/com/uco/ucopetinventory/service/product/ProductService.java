package com.uco.ucopetinventory.service.product;

import com.uco.ucopetinventory.domain.product.ProductDomain;
import com.uco.ucopetinventory.repository.product.IProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {

    private final IProductRepository productRepository;

    public ProductService(IProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional
    public void save(ProductDomain message) {
        if (message.getId() == null) {
            throw new IllegalArgumentException("El mensaje no trae el id: lo asigna la API antes de publicar");
        }
        if (!Boolean.TRUE.equals(message.getSellable())) {
            message.setPrice(null);
        }
        if (message.getActive() == null) {
            message.setActive(true);
        }
        productRepository.saveAndFlush(message);
    }

    @Transactional
    public void deactivate(ProductDomain message) {
        if (message.getId() == null) {
            throw new IllegalArgumentException("El mensaje no trae el id del producto a dar de baja");
        }
        ProductDomain product = productRepository.findLockedById(message.getId())
                .orElseThrow(() -> new IllegalArgumentException("No existe un producto con id " + message.getId()));
        product.setActive(false);
        productRepository.save(product);
    }
}
