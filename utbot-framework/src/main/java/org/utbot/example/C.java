package org.utbot.example;

enum C {
    A("A"), B("B");
    private final String title;

    C(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }

}
