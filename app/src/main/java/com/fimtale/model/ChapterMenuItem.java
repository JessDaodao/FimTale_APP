package com.fimtale.model;
import com.google.gson.annotations.SerializedName;
public class ChapterMenuItem {
    private int id;
    private String title;
    @SerializedName("order_num") private int orderNum;
    @SerializedName("status_del") private int statusDel;
    public int getId() { return id; }
    public String getTitle() { return title; }
    public int getOrderNum() { return orderNum; }
    public boolean isDeleted() { return statusDel != 0; }
}
