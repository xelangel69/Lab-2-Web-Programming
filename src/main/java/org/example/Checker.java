package org.example;

import java.math.BigDecimal;
import java.util.*;

public class Checker {
    private final Map<String, List<String>> data;
    private int xi;
    private BigDecimal x, y;
    private List<String> radii;

    public Checker(Map<String, List<String>> data) {
        this.data = data;
    }

    public void validate() {
        String xs = Main.one(data, "x"), ys = Main.one(data, "y").replace(',', '.');
        if (!xs.matches("-?[0-9]") || !ys.matches("-?[0-9]+(\\.[0-9]+)?") || ys.length() > 64) {
            throw new IllegalArgumentException();
        }
        xi = Integer.parseInt(xs);
        x = new BigDecimal(xs);
        y = new BigDecimal(ys);
        if (xi < -3 || xi > 5 || y.abs().compareTo(new BigDecimal("3")) >= 0) {
            throw new IllegalArgumentException();
        }
        radii = data.get("r");
        if (radii == null || radii.isEmpty() || radii.size() > 5
            || new HashSet<>(radii).size() != radii.size()) {
            throw new IllegalArgumentException();
        }
        for (String r : radii) {
            if (!r.matches("[1-5]")) {
                throw new IllegalArgumentException();
            }
        }
    }

    public int x() {
        return xi;
    }

    public BigDecimal y() {
        return y;
    }

    public List<String> radii() {
        return radii;
    }

    public boolean isHit(String r) {
        return inside(x, y, new BigDecimal(r));
    }

    static final BigDecimal TWO = new BigDecimal("2"), FOUR = new BigDecimal("4");

    public static boolean inside(BigDecimal x, BigDecimal y, BigDecimal r) {
        boolean rectangle = x.compareTo(r.negate()) >= 0 && x.signum() <= 0 && y.signum() <= 0
            && y.multiply(TWO).compareTo(r.negate()) >= 0;
        boolean triangle =
            x.signum() <= 0 && y.signum() >= 0 && y.subtract(x).multiply(TWO).compareTo(r) <= 0;
        boolean circle = x.signum() >= 0 && y.signum() <= 0
            && x.multiply(x).add(y.multiply(y)).multiply(FOUR).compareTo(r.multiply(r)) <= 0;
        return rectangle || triangle || circle;
    }
}
