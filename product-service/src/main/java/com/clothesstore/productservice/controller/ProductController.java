package com.clothesstore.productservice.controller;


import com.clothesstore.productservice.model.Product;
import com.clothesstore.productservice.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "http://localhost:5173", maxAge = 3600)
public class ProductController {
    @GetMapping("/")
    ResponseEntity<String> getCustomer () {

        return ResponseEntity.ok("response");
    }

    @Autowired
    private ProductService productService;


    @GetMapping("/collections/{id}")
    ResponseEntity<List<Product>> findProductsInCollection (@PathVariable Long id) {
        List<Product> response = productService.findProductsInCollection(id);
        return ResponseEntity.ok(response);
    }
    @GetMapping("/products/{id}")
    ResponseEntity<Product> findProductid (@PathVariable Long id) {
        Product response = productService.findById(id);
        return ResponseEntity.ok(response);
    }


}
