package com.enterprise.b2b.ordermanagement.service;

import com.enterprise.b2b.ordermanagement.entity.Product;

import java.util.List;

public interface ProductService {
    /**
     * Browse all B2B products in the system.
     *
     * @return List of all products
     */
    List<Product> getAllProducts();

    /**
     * Retrieve a product by its SKU, cached using Caffeine.
     *
     * @param sku Unique SKU string
     * @return The Product entity
     */
    Product getProductBySku(String sku);

    /**
     * Retrieve a product by its primary key ID.
     *
     * @param id Product ID
     * @return The Product entity
     */
    Product getProductById(Long id);

    /**
     * Create a new product.
     *
     * @param product Product definition to save
     * @return The saved Product entity
     */
    Product createProduct(Product product);
}
