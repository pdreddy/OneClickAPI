package com.acme.shop.model;

import jakarta.persistence.*;

@Entity
public class Review {

    @Id
    @GeneratedValue
    private Long id;

    private String text;

    @ManyToOne
    private Book book;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public Book getBook() { return book; }
    public void setBook(Book book) { this.book = book; }
}
