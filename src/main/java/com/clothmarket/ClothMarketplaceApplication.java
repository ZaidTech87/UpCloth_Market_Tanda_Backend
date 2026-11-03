package com.clothmarket;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ClothMarketplaceApplication{
    
    public static void main(String[] args) {
        SpringApplication.run(ClothMarketplaceApplication.class, args);
        System.out.println("\n==============================================");
        System.out.println("   Cloth Marketplace API is running!");
        System.out.println("   Server: http://localhost:8080/api");
        System.out.println("==============================================\n");
    }
}
