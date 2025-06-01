package org.Codes;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Ein Quiz-Server, der Fragen aus einer JSON-Datei lädt und mit mehreren Clients interagiert.
 * Die Clients erhalten synchronisierte Fragen und können gegeneinander antreten.
 */
public class Server2 {
    /** Liste aller geladenen Fragen. */
    private static List<Frage> fragenListe = new ArrayList<>();

    /** Liste aller verbundenen Clients. */
    private static final List<ClientHandler> clients = Collections.synchronizedList(new ArrayList<>());

    /** Map zur Speicherung der Punkte jedes Spielers. */
    private static final Map<String, Integer> punkteMap = new ConcurrentHashMap<>();

    /** Set zur Vermeidung doppelter Fragen. */
    private static final Set<Integer> beantworteteFragen = Collections.synchronizedSet(new HashSet<>());

    /** Portnummer für die Serververbindung. */
    private static final int PORT = 1404;

    /** Maximale Anzahl an Spielern. */
    private static final int MAX_SPIELER = 2;

    /** Lock-Objekt für Thread-Synchronisation. */
    private static final Object LOCK = new Object();

    /** Pfad zur JSON-Datei mit den Fragen. */
    private static final String JSON_PFAD = "src/Ordner_Fragen/fragen.json";

    /** Status, ob auf Start gewartet wird. */
    private static boolean wartetAufStart = true;

    /** Counter für Anzahl der bereitgestellten Spieler. */
    private static int bereitgestellteSpieler = 0;

    public static void main(String[] args) {
        ladeFragenAusJson(JSON_PFAD);
        Collections.shuffle(fragenListe);

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server läuft auf Port " + PORT);
            ExecutorService pool = Executors.newFixedThreadPool(MAX_SPIELER);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                String spielerName = "Spieler " + (clients.size() + 1);
                ClientHandler client = new ClientHandler(clientSocket, spielerName);
                clients.add(client);
                client.sendeNachricht("SPIELERNAME|" + spielerName); // Sendet den Spielernamen zum Client
                pool.execute(client);
                System.out.println(spielerName + " verbunden!");
                broadcastStatus();
            }
        } catch (IOException e) {
            System.err.println("Serverfehler: " + e.getMessage());
        }
    }

    private static void ladeFragenAusJson(String dateiPfad) {
        try {
            String json = new String(Files.readAllBytes(Paths.get(dateiPfad)));
            Gson gson = new Gson();
            FragenContainer container = gson.fromJson(json, FragenContainer.class);
            if (container != null && container.fragen != null) {
                fragenListe = container.fragen;
                System.out.println(fragenListe.size() + " Fragen geladen.");
            } else {
                System.out.println("Keine Fragen gefunden in der JSON-Datei.");
            }
        } catch (IOException | JsonSyntaxException e) {
            System.err.println("Fehler beim Laden der JSON-Datei: " + e.getMessage());
        }
    }

    private static void broadcastStatus() {
        String status;
        if (clients.size() < MAX_SPIELER) {
            status = "WARTEN|Warten auf weiteren Spieler (" + clients.size() + "/" + MAX_SPIELER + ")";
        } else if (wartetAufStart) {
            status = "BEREIT|Beide Spieler verbunden. Warte auf Start...";
        } else {
            status = "SPIEL_LAEUFT";
        }
        broadcastNachricht("STATUS|" + status);
    }

    private static void starteSpiel() {
        System.out.println("Spiel startet mit " + clients.size() + " Spielern!");
        broadcastNachricht("SPIEL_STARTET");
        wartetAufStart = true;

        new Thread(() -> {
            for (Frage frage : fragenListe) {
                if (beantworteteFragen.contains(frage.id))
                    continue;

                sendeFrageAnAlle(frage);
                warteAufAntworten(frage);

                if (hatGewinner()) {
                    zeigeGewinner();
                    break;
                }
            }

            if (!hatGewinner()) {
                broadcastNachricht("SPIEL_ENDE|Kein Gewinner – alle Fragen beantwortet!");
            }
            resetSpiel();
        }).start();
    }

    private static void sendeFrageAnAlle(Frage frage) {
        synchronized (LOCK) {
            for (ClientHandler client : clients) {
                client.sendeFrage(frage);
            }
        }
    }

    private static void warteAufAntworten(Frage frage) {
        boolean[] hatGeantwortetFlag = new boolean[clients.size()];

        synchronized (LOCK) {
            while (true) {
                for (int i = 0; i < clients.size(); i++) {
                    ClientHandler client = clients.get(i);
                    if (client.hatGeantwortet() && !hatGeantwortetFlag[i]) {
                        hatGeantwortetFlag[i] = true;
                        String antwort = client.getAntwort();
                        if (antwort != null && antwort.equalsIgnoreCase(frage.richtig)) {
                            punkteMap.merge(client.getSpielerName(), 1, Integer::sum);
                            client.sendeNachricht("RICHTIG|Punkte: " + punkteMap.get(client.getSpielerName()));
                            broadcastNachricht("PUNKTE|" + client.getSpielerName() + ":" + punkteMap.get(client.getSpielerName()));
                        } else {
                            client.sendeNachricht("FALSCH|Richtig wäre " + frage.richtig + ".");
                        }
                        client.resetAntwort();
                    }
                }
                boolean alleGeantwortet = true;
                for (boolean antwortFlag : hatGeantwortetFlag) {
                    if (!antwortFlag) {
                        alleGeantwortet = false;
                        break;
                    }
                }
                if (alleGeantwortet) break;

                try {
                    LOCK.wait(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            beantworteteFragen.add(frage.id);
            broadcastNachricht("NÄCHSTE_FRAGE");
        }
    }

    private static boolean hatGewinner() {
        return punkteMap.values().stream().anyMatch(punkte -> punkte >= 5);
    }

    private static void broadcastNachricht(String nachricht) {
        synchronized (clients) {
            for (ClientHandler client : clients) {
                client.sendeNachricht(nachricht);
            }
        }
    }

    private static void zeigeGewinner() {
        Optional<Map.Entry<String, Integer>> gewinnerOpt = punkteMap.entrySet().stream()
                .max(Map.Entry.comparingByValue());

        String gewinner = gewinnerOpt.map(Map.Entry::getKey).orElse("Unentschieden");
        int punkte = punkteMap.getOrDefault(gewinner, 0);

        broadcastNachricht("GEWINNER|" + gewinner + " hat mit " + punkte + " Punkten gewonnen!");
    }

    private static void resetSpiel() {
        punkteMap.clear();
        beantworteteFragen.clear();
        bereitgestellteSpieler = 0;  // Reset der Spieleranzahl
        for (ClientHandler client : clients) {
            client.resetAntwort();
        }
    }

    // --- Datenklassen für JSON-Parsing ---
    public static class FragenContainer {
        public List<Frage> fragen;
    }

    public static class Frage {
        public int id;
        public String frage;
        public Antworten antworten;
        public String richtig;
    }

    public static class Antworten {
        public String A;
        public String B;
        public String C;
    }

    // --- Client-Handler-Klasse ---
    public static class ClientHandler implements Runnable {
        private final Socket socket;
        private final String spielerName;
        private PrintWriter ausgang;
        private BufferedReader eingang;
        private String aktuelleAntwort;
        private boolean hatGeantwortet = false;

        public ClientHandler(Socket socket, String spielerName) throws IOException {
            this.socket = socket;
            this.spielerName = spielerName;
            this.ausgang = new PrintWriter(socket.getOutputStream(), true);
            this.eingang = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.aktuelleAntwort = null;
            punkteMap.put(spielerName, 0);
        }

        @Override
        public void run() {
            try {
                String nachricht;
                while ((nachricht = eingang.readLine()) != null) {
                    if (nachricht.equalsIgnoreCase("START")) {
                        synchronized (LOCK) {
                            bereitgestellteSpieler++;
                            if (bereitgestellteSpieler == MAX_SPIELER) {
                                wartetAufStart = false;
                                starteSpiel();
                            }
                        }
                    } else {
                        synchronized (LOCK) {
                            aktuelleAntwort = nachricht.trim().toUpperCase();
                            hatGeantwortet = true;
                            LOCK.notifyAll();
                        }
                    }
                }
            } catch (IOException e) {
                System.err.println("Fehler bei Client " + spielerName + ": " + e.getMessage());
            } finally {
                try {
                    socket.close();
                } catch (IOException e) {
                    System.err.println("Fehler beim Schließen des Sockets: " + e.getMessage());
                }
                clients.remove(this);
                synchronized (LOCK) {
                    bereitgestellteSpieler--; // Spieleranzahl dekrementieren, wenn ein Client sich trennt
                }
            }
        }

        public void sendeFrage(Frage frage) {
            ausgang.println("FRAGE|" + frage.frage);
            ausgang.println("A: " + frage.antworten.A);
            ausgang.println("B: " + frage.antworten.B);
            ausgang.println("C: " + frage.antworten.C);
            hatGeantwortet = false;
        }

        public void sendeNachricht(String nachricht) {
            ausgang.println(nachricht);
        }

        public String getSpielerName() {
            return spielerName;
        }

        public String getAntwort() {
            return aktuelleAntwort;
        }

        public boolean hatGeantwortet() {
            return hatGeantwortet;
        }

        public void resetAntwort() {
            aktuelleAntwort = null;
            hatGeantwortet = false;
        }
    }
}
