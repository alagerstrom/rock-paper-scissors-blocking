package com.andreas.rockpaperscissors.viewcontroller;

public enum ImagePath {
    PAPER("/images/paper.png"),
    ROCK("/images/rock.png"),
    SCISSORS("/images/scissors.png"),
    CONNECT("/images/connect.jpg");

    String name;



    ImagePath(String s) {
        this.name = s;
    }

    @Override
    public String toString() {
        return name;
    }
}