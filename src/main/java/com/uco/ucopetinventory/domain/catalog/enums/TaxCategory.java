package com.uco.ucopetinventory.domain.catalog.enums;

public enum TaxCategory {
    EXEMPT(0),
    REDUCED(5),
    STANDARD(19);

    private final int percentage;

    TaxCategory(int percentage) {
        this.percentage = percentage;
    }

    public int getPercentage() {
        return percentage;
    }
}
