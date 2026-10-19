package com.acme.shop.pojo;

import java.time.LocalDate;

public class Customer {

    private Long id;
    private String name;
    private int age;
    private String email;
    private boolean active;
    private LocalDate registered;

    public Customer() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getAge() { return age; }
    public void setAge(int age) { this.age = age; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public LocalDate getRegistered() { return registered; }
    public void setRegistered(LocalDate registered) { this.registered = registered; }
}
