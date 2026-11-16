package com.example.gajeontiming.model;

import java.io.Serializable;

/**
 * 제품 하나. 검색 결과와 트렌드 목록이 공통으로 쓰는 필드.
 * Serializable: 인텐트로 다음 화면에 통째로 넘기기 위함.
 */
public class Product implements Serializable {
    public String id;
    public String title;
    public String brand;
    public int price;
    public String image;
    public String link;
    public String mallName;
    public double reviewScore;
    public int reviewCount;
}
