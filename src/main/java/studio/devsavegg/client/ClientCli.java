package studio.devsavegg.client;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ClientCli {

    private static String WS_URL = "ws://localhost:8080/chat";


    private static final String RESET = "\u001B[0m";
    private static final String BOLD  = "\u001B[1m";

    private static final String RED   = "\u001B[31m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELL  = "\u001B[33m";
    private static final String BLUE  = "\u001B[34m";
    private static final String MAG   = "\u001B[35m";
    private static final String CYAN  = "\u001B[36m";
    private static final String GRAY  = "\u001B[90m";

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final int WIDTH = 90;

    private static final Set<String> knownUsers = new LinkedHashSet<>();
    private static final Set<String> knownRooms = new LinkedHashSet<>();

    public static void main(String[] args) throws Exception {
        if (args.length > 0) WS_URL = args[0];

        clearScreen();
        printBootScreen();

        HttpClient client = HttpClient.newHttpClient();
        CountDownLatch closedLatch = new CountDownLatch(1);

        WebSocket.Listener listener = new WebSocket.Listener() {
            private final StringBuilder partial = new StringBuilder();

            @Override
            public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
                partial.append(data);
                if (last) {
                    String msg = partial.toString();
                    partial.setLength(0);
                    renderIncoming(msg);
                }
                ws.request(1);
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public void onOpen(WebSocket ws) {
                printStatus("LINK UP", "CONNECTED TO " + WS_URL);
                printHelpHint();
                ws.request(1);
            }

            @Override
            public CompletionStage<?> onClose(WebSocket ws, int code, String reason) {
                printStatus("LINK DOWN", "DISCONNECTED (code=" + code + ", reason=" + reason + ")");
                closedLatch.countDown();
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public void onError(WebSocket ws, Throwable error) {
                printError("CONNECTION ERROR: " + error.getMessage());
                closedLatch.countDown();
            }
        };

        WebSocket ws = client.newWebSocketBuilder()
                .buildAsync(URI.create(WS_URL), listener)
                .join();

        // loop รับ input

        Thread inputThread = new Thread(() -> {
            String name = "null";
            Scanner sc = new Scanner(System.in);
            while (true) {
                if (!sc.hasNextLine()) break;
                String line = sc.nextLine().trim();
                if (line.isEmpty()) continue;

                // แยก command / args
                String[] parts = line.split("\\s+", 2);

                String rawCmd = parts[0];
                String argsPart = (parts.length > 1) ? parts[1] : "";
                // ตัด '/' ออกถ้ามี
                if (rawCmd.startsWith("/")) {
                    rawCmd = rawCmd.substring(1);
                }
                String lowerBase = rawCmd.toLowerCase(Locale.ROOT);

                // ---------- local commands: quit / clear / list ----------
                if (lowerBase.equals("quit")) {
                    try { ws.sendClose(WebSocket.NORMAL_CLOSURE, "bye").join(); } catch (Exception ignored) {}
                    break;
                }
                if (lowerBase.equals("set_name")) {
                    name = argsPart;
                }
                if (lowerBase.equals("clear")) {
                    clearScreen();
                    printBootScreen();
                    printStatus("LINK UP", "CONNECTED TO " + WS_URL);
                    printHelpHint();
                    continue;
                }

                if (lowerBase.equals("list")) {
                    // list, /list, list users, /list rooms ทำงาน local
                    String mode = argsPart == null ? "" : argsPart.trim().toLowerCase(Locale.ROOT);
                    handleLocalList(mode);
                    continue;
                }

                // ---------- คำสั่งที่ส่งไป server ----------
                // ส่งในรูปแบบ: create_room A, join_room A, set_name Alice, ...
                String canonical = rawCmd + (argsPart.isEmpty() ? "" : " " + argsPart);
                if (lowerBase.equals("say")) {
                    canonical = rawCmd + " "+name+" : "+argsPart;
                }
                try {
                    ws.sendText(canonical, true).join();
                    printOutgoing(rawCmd + (argsPart.isEmpty() ? "" : " " + argsPart));
                } catch (CompletionException | IllegalStateException e) {
                    printError("SEND FAILED: " + e.getMessage());
                }
            }
        }, "stdin-thread");
        inputThread.setDaemon(true);
        inputThread.start();

        // heartbeat
        ScheduledExecutorService ses = Executors.newSingleThreadScheduledExecutor();
        ses.scheduleAtFixedRate(() -> {
            try { ws.sendPing(ByteBuffer.wrap(new byte[]{1})).join(); }
            catch (CompletionException | IllegalStateException ignored) {}
        }, 20, 20, TimeUnit.SECONDS);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { ws.sendClose(WebSocket.NORMAL_CLOSURE, "shutdown").join(); } catch (Exception ignored) {}
            ses.shutdownNow();
        }));

        closedLatch.await();
        ses.shutdownNow();
        printStatus("SESSION END", "JUICY TERMINAL SHUTDOWN COMPLETE");
    }


    private static void printBootScreen() {
        String bar = repeat("═", WIDTH);
        System.out.println(GREEN + BOLD + "╔" + bar + "╗" + RESET);
        System.out.println(GREEN + BOLD + "║   JUICY TERMINAL  v1.0.0                NODE: JUICY-01" + " ".repeat(10) + "║" + RESET);
        System.out.println(GREEN + BOLD + "╠" + bar + "╣" + RESET);
        System.out.println(GREEN + "║ " + RESET + CYAN + "PROFILE " + RESET + ": CHAT-CLIENT   " +
                CYAN + "MODE " + RESET + ": INTERACTIVE   " +
                CYAN + "JAVA " + RESET + ": " + System.getProperty("java.version"));
        System.out.println(GREEN + "║ " + RESET + CYAN + "TARGET  " + RESET + ": " + WS_URL);
        System.out.println(GREEN + "╠" + bar + "╣" + RESET);
        System.out.println(GREEN + "║ " + RESET + "SERVER CMDS : create_room, join_room, leave_room, say, dm, add_friend, ..." + RESET);
        System.out.println(GREEN + "║ " + RESET + "            : set_name, user_info, room_info  (พิมพ์ได้ทั้งมี/ไม่มี '/')" + RESET);
        System.out.println(GREEN + "║ " + RESET + "LOCAL ONLY  : list, clear, quit               (พิมพ์ได้ทั้งมี/ไม่มี '/')" + RESET);
        System.out.println(GREEN + "╚" + bar + "╝" + RESET);
        System.out.println();
    }

    private static void printStatus(String tag, String message) {
        String time = now();
        String line = GREEN + "[" + time + "][STATUS][" + tag + "] " + RESET + message;
        System.out.println(line);
        System.out.println(GRAY + repeat("─", WIDTH) + RESET);
    }

    private static void printHelpHint() {
        System.out.println(GRAY +
                "HINT: create_room A | /join_room A | say hello | /dm user-xxxx hi | set_name Alice | list" +
                RESET);
        System.out.println(GRAY + repeat("─", WIDTH) + RESET);
    }

    // แสดงข้อความจาก server + เก็บ state สำหรับ list
    private static void renderIncoming(String raw) {
        String lower = raw.toLowerCase(Locale.ROOT);
        String time = now();

        if (lower.contains("\"type\":\"chat\"")) {
            String from = extract(raw, "from");
            String room = extract(raw, "roomId");
            String msg  = extract(raw, "message");
            if (!from.equals("?")) knownUsers.add(from);
            if (!room.equals("?")) knownRooms.add(room);

            System.out.println(CYAN + "[" + time + "][CHAT]"
                    + (room.equals("?") ? "" : "[ROOM " + room + "]")
                    + "[" + from + "] " + RESET + msg);

        } else if (lower.contains("\"type\":\"dm\"")) {
            String from = extract(raw, "from");
            String to   = extract(raw, "to");
            String msg  = extract(raw, "message");
            if (!from.equals("?")) knownUsers.add(from);
            if (!to.equals("?"))   knownUsers.add(to);

            System.out.println(MAG + "[" + time + "][DM][" + from + "→" + to + "] " + RESET + msg);

        } else if (lower.contains("\"type\":\"system\"")) {
            String sub = extract(raw, "subType");
            String msg = extract(raw, "message");
            System.out.println(GREEN + "[" + time + "][SYSTEM]"
                    + (sub.equals("?") ? "" : "[" + sub + "]") + " " + RESET + msg);

        } else if (lower.contains("\"type\":\"error\"")) {
            String code = extract(raw, "errorCode");
            String cmd  = extract(raw, "command");
            String msg  = extract(raw, "message");
            System.out.println(RED + "[" + time + "][ERROR]"
                    + (code.equals("?") ? "" : "[CODE " + code + "]")
                    + (cmd.equals("?") ? "" : "[CMD " + cmd + "]")
                    + " " + RESET + msg);

        } else {
            System.out.println(GRAY + "[" + time + "][RAW] " + raw + RESET);
        }
    }

    private static void printOutgoing(String cmd) {
        String time = now();
        System.out.println(YELL + "[" + time + "][TX] " + RESET + cmd);
    }

    private static void printError(String text) {
        String time = now();
        System.out.println(RED + "[" + time + "][ERROR] " + RESET + text);
    }

    // ===================== list แบบ local =====================

    private static void handleLocalList(String modeRaw) {
        String time = now();
        String mode = (modeRaw == null || modeRaw.isBlank()) ? "all" : modeRaw;

        System.out.println(GRAY + "[" + time + "][LOCAL][LIST] mode=" + mode + RESET);

        boolean showUsers = mode.equals("all") || mode.startsWith("user");
        boolean showRooms = mode.equals("all") || mode.startsWith("room");

        if (showUsers) {
            System.out.println(CYAN + "  USERS seen this session (" + knownUsers.size() + "):" + RESET);
            if (knownUsers.isEmpty()) {
                System.out.println(GRAY + "    (none yet — chat a bit first)" + RESET);
            } else {
                for (String u : knownUsers) {
                    System.out.println("    • " + u);
                }
            }
        }

        if (showRooms) {
            System.out.println(CYAN + "  ROOMS seen this session (" + knownRooms.size() + "):" + RESET);
            if (knownRooms.isEmpty()) {
                System.out.println(GRAY + "    (none yet — try create_room / join_room)" + RESET);
            } else {
                for (String r : knownRooms) {
                    System.out.println("    • " + r);
                }
            }
        }

        System.out.println(GRAY + repeat("─", WIDTH) + RESET);
    }

    // ===================== JSON helper (no deps) =====================

    private static String extract(String json, String field) {
        String key = "\"" + field + "\"";
        int i = json.indexOf(key);
        if (i < 0) return "?";
        int c = json.indexOf(':', i);
        if (c < 0) return "?";
        int start = c + 1;
        int endComma = json.indexOf(',', start);
        int endBrace = json.indexOf('}', start);
        int end = (endComma == -1) ? endBrace
                : (endBrace == -1 ? endComma : Math.min(endComma, endBrace));
        if (end < 0) end = json.length();
        String v = json.substring(start, end).trim();
        v = v.replaceAll("^\\s*\"|\"\\s*$", "");
        v = v.replaceAll("[{}]", "");
        return v.isBlank() ? "?" : v;
    }

    // ===================== misc utils =====================

    private static String now() {
        return LocalTime.now().format(TIME_FMT);
    }

    private static String repeat(String s, int n) {
        return s.repeat(Math.max(0, n));
    }

    private static void clearScreen() {
        System.out.print("\033[H\033[2J");
        System.out.flush();
    }
}