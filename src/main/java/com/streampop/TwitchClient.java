package com.streampop;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Reads Twitch chat anonymously (no login needed) over IRC and puts messages into INBOX. */
public final class TwitchClient {
    public enum State { OFF, CONNECTING, ON }

    public static final ConcurrentLinkedQueue<ChatMsg> INBOX = new ConcurrentLinkedQueue<>();

    private static volatile State state = State.OFF;
    private static volatile String status = "Not connected";
    private static volatile int generation = 0;
    private static volatile Socket socket;

    private TwitchClient() {}

    public static State state() { return state; }

    public static String status() { return status; }

    public static synchronized void connect(String channel) {
        disconnect();
        String ch = cleanChannel(channel);
        if (ch.isEmpty()) {
            status = "Enter a channel first";
            return;
        }
        final int myGen = ++generation;
        state = State.CONNECTING;
        status = "Connecting to #" + ch + " ...";
        Thread t = new Thread(() -> run(ch, myGen), "streampop-twitch");
        t.setDaemon(true);
        t.start();
    }

    public static synchronized void disconnect() {
        generation++;
        state = State.OFF;
        status = "Not connected";
        Socket s = socket;
        socket = null;
        if (s != null) {
            try { s.close(); } catch (IOException ignored) { }
        }
    }

    /** "https://twitch.tv/Shroud", "#shroud", "@shroud" -> "shroud" */
    public static String cleanChannel(String in) {
        if (in == null) return "";
        String s = in.trim().toLowerCase(Locale.ROOT);
        int slash = s.lastIndexOf('/');
        if (slash >= 0) s = s.substring(slash + 1);
        StringBuilder sb = new StringBuilder();
        for (char ch : s.toCharArray()) {
            if ((ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9') || ch == '_') sb.append(ch);
        }
        return sb.toString();
    }

    private static void run(String ch, int myGen) {
        while (generation == myGen) {
            try (SSLSocket s = (SSLSocket) SSLSocketFactory.getDefault().createSocket("irc.chat.twitch.tv", 6697)) {
                socket = s;
                s.setSoTimeout(6 * 60 * 1000);
                OutputStream out = s.getOutputStream();
                BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
                send(out, "CAP REQ :twitch.tv/tags twitch.tv/commands");
                send(out, "PASS SCHMOOPIIE");
                send(out, "NICK justinfan" + (10000 + new Random().nextInt(80000)));
                send(out, "JOIN #" + ch);

                String line;
                while (generation == myGen && (line = in.readLine()) != null) {
                    if (line.startsWith("PING")) {
                        send(out, "PONG :tmi.twitch.tv");
                    } else if (line.contains(" 001 ")) {
                        state = State.ON;
                        status = "Connected to #" + ch;
                    } else if (line.contains(" RECONNECT")) {
                        break;
                    } else {
                        ChatMsg m = parse(line);
                        if (m != null) {
                            INBOX.add(m);
                            while (INBOX.size() > 400) INBOX.poll();
                        }
                    }
                }
            } catch (Exception e) {
                if (generation != myGen) return;
                status = "Connection lost, retrying ...";
            }
            if (generation != myGen) return;
            state = State.CONNECTING;
            for (int i = 0; i < 50 && generation == myGen; i++) {
                try { Thread.sleep(100); } catch (InterruptedException ignored) { return; }
            }
        }
    }

    private static void send(OutputStream out, String line) throws IOException {
        out.write((line + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    /** Parses one IRC line, returns null if it is not a chat message. */
    public static ChatMsg parse(String line) {
        String tags = "";
        String rest = line;
        if (line.startsWith("@")) {
            int sp = line.indexOf(' ');
            if (sp < 0) return null;
            tags = line.substring(1, sp);
            rest = line.substring(sp + 1);
        }
        int pm = rest.indexOf(" PRIVMSG ");
        if (pm < 0) return null;
        int colon = rest.indexOf(" :", pm);
        if (colon < 0) return null;
        String text = rest.substring(colon + 2);

        Map<String, String> t = new HashMap<>();
        for (String kv : tags.split(";")) {
            int eq = kv.indexOf('=');
            if (eq > 0) t.put(kv.substring(0, eq), unescape(kv.substring(eq + 1)));
        }

        String user = t.get("display-name");
        if (user == null || user.isBlank()) {
            int ex = rest.indexOf('!');
            user = (rest.startsWith(":") && ex > 1) ? rest.substring(1, ex) : "viewer";
        }

        int color = -1;
        String c = t.get("color");
        if (c != null && c.length() == 7 && c.startsWith("#")) {
            try { color = Integer.parseInt(c.substring(1), 16); } catch (NumberFormatException ignored) { }
        }

        List<String> badges = new ArrayList<>();
        String b = t.get("badges");
        if (b != null && !b.isEmpty()) {
            for (String one : b.split(",")) {
                int sl = one.indexOf('/');
                badges.add(sl > 0 ? one.substring(0, sl) : one);
            }
        }

        boolean action = false;
        if (text.startsWith("\u0001ACTION ")) {
            action = true;
            text = text.substring(8);
            if (text.endsWith("\u0001")) text = text.substring(0, text.length() - 1);
        }
        text = clean(text);
        if (text.isEmpty()) return null;
        return new ChatMsg(user, color, badges, text, action, System.currentTimeMillis());
    }

    private static String clean(String s) {
        StringBuilder sb = new StringBuilder();
        for (char ch : s.toCharArray()) {
            if (ch >= 32 && ch != 127) sb.append(ch);
            if (sb.length() >= 300) break;
        }
        return sb.toString().trim();
    }

    private static String unescape(String s) {
        if (s.indexOf('\\') < 0) return s;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                switch (n) {
                    case 's' -> sb.append(' ');
                    case ':' -> sb.append(';');
                    case 'r' -> sb.append('\r');
                    case 'n' -> sb.append('\n');
                    default -> sb.append(n);
                }
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }
}
