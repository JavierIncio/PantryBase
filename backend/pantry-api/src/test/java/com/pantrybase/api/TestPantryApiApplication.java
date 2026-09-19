package com.pantrybase.api;

import org.springframework.boot.SpringApplication;

public class TestPantryApiApplication {

    public static void main(String[] args) {
        SpringApplication.from(PantryApiApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
