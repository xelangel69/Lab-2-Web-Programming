package org.example;

import java.math.BigDecimal;
import java.util.*;

public class AreaTest {
    static void check(String x, String y, String r, boolean expected) {
        if (Checker.inside(new BigDecimal(x), new BigDecimal(y), new BigDecimal(r)) != expected)
            throw new AssertionError(x + "," + y + "," + r);
    }

    public static void main(String[] args) {
        check("-2", "-1", "2", true);
        check("-2.0001", "-1", "2", false);
        check("-1", "0", "2", true);
        check("0", "1", "2", true);
        check("-0.5", "0.5", "2", true);
        check("-0.5", "0.5000000000000000001", "2", false);
        check("1", "0", "2", true);
        check("0", "-1", "2", true);
        check("0.6", "-0.8", "2", true);
        check("0.6", "-0.800000000000000001", "2", false);
        check("0", "0", "1", true);
        check("1", "1", "5", false);
        check("-1", "-1.01", "2", false);
        var history = new ArrayList<String>();
        Main.process(Main.parse("action=check&x=0&y=0&r=1&r=5"), history, System.nanoTime());
        if (history.size() != 2)
            throw new AssertionError();
        for (String body : List.of("action=check&x=0&y=3&r=1", "action=check&x=6&y=0&r=1",
                 "action=check&x=0&y=NaN&r=1", "action=check&x=0&y=0&r=0",
                 "action=check&x=0&y=0&r=1&r=6", "action=check&x=0&y=0&r=1&r=1",
                 "action=check&x=0&x=1&y=0&r=1")) {
            try {
                Main.process(Main.parse(body), history, System.nanoTime());
                throw new AssertionError(body);
            } catch (IllegalArgumentException expected) {
            }
            if (history.size() != 2)
                throw new AssertionError("Partial update");
        }
        System.out.println("Area and validation tests passed");
    }
}
