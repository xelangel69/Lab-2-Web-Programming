package org.example;

import com.fastcgi.FCGIInterface;
import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

public class Main {
    static final Map<String, List<String>> histories = new HashMap<>();

    static Map<String, List<String>> parse(String body) {
        Map<String, List<String>> data = new HashMap<>();
        for (String pair : body.split("&")) {
            String[] parts = pair.split("=", -1);
            if (parts.length != 2) {
                throw new IllegalArgumentException();
            }
            String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            if (!Set.of("x", "y", "r", "action").contains(key)) {
                throw new IllegalArgumentException();
            }
            data.computeIfAbsent(key, k -> new ArrayList<>())
                .add(URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
        }
        return data;
    }

    static String one(Map<String, List<String>> data, String key) {
        List<String> values = data.get(key);
        if (values == null || values.size() != 1) {
            throw new IllegalArgumentException();
        }
        return values.get(0);
    }

    static String process(Map<String, List<String>> data, List<String> history, long start) {
        String action = one(data, "action");
        if (action.equals("clear") || action.equals("history")) {
            if (data.size() != 1) {
                throw new IllegalArgumentException();
            }
            if (action.equals("clear"))
                history.clear();
        } else if (action.equals("check")) {
            if (data.size() != 4) {
                throw new IllegalArgumentException();
            }
            Checker checker = new Checker(data);
            checker.validate();
            String time = Instant.now().toString();
            for (String r : checker.radii()) {
                double ms = (System.nanoTime() - start) / 1e6;
                history.add("{\"x\":" + checker.x() + ",\"y\":" + checker.y().toPlainString()
                    + ",\"r\":" + r + ",\"hit\":" + checker.isHit(r) + ",\"timestamp\":\"" + time
                    + "\",\"elapsedMs\":" + ms + "}");
            }
        } else
            throw new IllegalArgumentException();
        return ("{\"results\":[" + String.join(",", history) + "],\"serverTime\":\"" + Instant.now()
            + "\",\"elapsedMs\":" + (System.nanoTime() - start) / 1e6 + "}");
    }

    public static void main(String[] args) {
        FCGIInterface fcgi = new FCGIInterface();
        while (fcgi.FCGIaccept() >= 0) {
            long start = System.nanoTime();
            String status = "200 OK", body, cookie = "";
            try {
                Properties env = FCGIInterface.request.params;
                if (!"POST".equals(env.getProperty("REQUEST_METHOD"))) {
                    status = "405 Method Not Allowed";
                    body = "{\"error\":\"Используйте POST\"}";
                } else if (!env.getProperty("CONTENT_TYPE", "")
                               .split(";")[0]
                               .trim()
                               .equalsIgnoreCase("application/x-www-form-urlencoded")) {
                    status = "415 Unsupported Media Type";
                    body = "{\"error\":\"Требуется форма URL encoded\"}";
                } else {
                    int size = Integer.parseInt(env.getProperty("CONTENT_LENGTH", "0"));
                    if (size < 1 || size > 4096) {
                        throw new IllegalArgumentException();
                    }
                    byte[] bytes = System.in.readNBytes(size);
                    if (bytes.length != size) {
                        throw new IllegalArgumentException();
                    }
                    String sid = null;
                    for (String part : env.getProperty("HTTP_COOKIE", "").split(";")) {
                        String value = part.trim();
                        if (value.matches("LAB2SID=[a-f0-9-]{36}")
                            && histories.containsKey(value.substring(8)))
                            sid = value.substring(8);
                    }
                    if (sid == null) {
                        sid = UUID.randomUUID().toString();
                        histories.put(sid, new ArrayList<>());
                        cookie =
                            "Set-Cookie: LAB2SID=" + sid + "; Path=/; HttpOnly; SameSite=Lax\r\n";
                    }
                    body = process(parse(new String(bytes, StandardCharsets.UTF_8)),
                        histories.get(sid), start);
                }
            } catch (IllegalArgumentException e) {
                status = "400 Bad Request";
                body =
                    "{\"error\":\"Недопустимые параметры: X от -3 до 5, -3 < Y < 3, R от 1 до 5\"}";
            } catch (Exception e) {
                status = "500 Internal Server Error";
                body = "{\"error\":\"Ошибка сервера\"}";
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);

            String headers = "HTTP/1.1 " + status
                + "\r\nContent-Type: application/json; charset=UTF-8\r\nContent-Length: "
                + bytes.length + "\r\nCache-Control: no-store\r\nAllow: POST\r\n" + cookie + "\r\n";
            System.out.writeBytes(headers.getBytes(StandardCharsets.UTF_8));
            System.out.writeBytes(bytes);
            System.out.flush();
        }
    }
}
