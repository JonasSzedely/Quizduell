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
 * Ein Quiz-Server, der Fragen aus einer JSON-Datei lädt und mit mehreren Clients interagiert.(minimum 2 Client)
 * Die Clients erhalten synchronisierte Fragen und können gegeneinander antreten.
 */
public class Server {

    /** Liste der geladenen Fragen */
    private static List<Frage> fragenListe = new ArrayList<>();

    /** Liste der verbundenen Clients */
    private static final List<ClientHandler> clients = Collections.synchronizedList(new ArrayList<>());

    /** Map zur Punktezählung der Spieler */
    private static final Map<String, Integer> punkteMap = new ConcurrentHashMap<>();

    /** Setzts der bereits beantworteten Fragen (ID) */
    private static final Set<Integer> beantworteteFragen = Collections.synchronizedSet(new HashSet<>());

    /** Server-Port, 1404 laut Wikipedia nicht besetzt (Iranische Kalender) */
    private static final int PORT = 1404;

    /** Maximale Anzahl der Spieler */
    private static final int MAX_SPIELER = 2;

    /** Lock-Objekt für Synchronisation */
    private static final Object LOCK = new Object();

    /** Pfad zur JSON-Datei mit Fragen */
    private static final String JSON_PFAD = "src/Ordner_Fragen/fragen.json";

    /** Flag, ob auf den Start des Spiels gewartet wird */
    private static boolean wartetAufStart = true;

    /** Anzahl der bereitgestellten Spieler */
    private static int bereitgestellteSpieler = 0;

    /**
     * Main-Methode zum Starten des Servers.
     * Lädt Fragen, startet den ServerSocket und akzeptiert Client-Verbindungen.
     *
     * @param args Kommandozeilenargumente (nicht verwendet)
     */
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
                client.sendeNachricht("SPIELERNAME|" + spielerName);
                pool.execute(client);
                System.out.println(spielerName + " verbunden!");
                broadcastStatus();
            }
        } catch (IOException e) {
            System.err.println("Serverfehler: " + e.getMessage());
        }
    }

    /**
     * Lädt die Fragen aus einer JSON-Datei.
     *
     * @param dateiPfad Pfad zur JSON-Datei
     */
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

    /**
     * Sendet den aktuellen Status an alle verbundenen Clients.
     * Zeigt an, ob gewartet wird, ob das Spiel bereit ist oder läuft.
     */
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

    /**
     * Startet das Spiel, sobald alle Spieler bereit sind.
     * Sendet die Fragen an alle und steuert den Spielablauf.
     */
    private static void starteSpiel() {
        System.out.println("Spiel startet mit " + clients.size() + " Spielern!");
        broadcastNachricht("SPIEL_STARTET");
        wartetAufStart = false;

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

    /**
     * Sendet eine Frage an alle verbundenen Clients.
     *
     * @param frage Die Frage, die gesendet wird
     */
    private static void sendeFrageAnAlle(Frage frage) {
        synchronized (LOCK) {
            for (ClientHandler client : clients) {
                client.sendeFrage(frage);
            }
        }
    }

    /**
     * Wartet auf die Antworten aller Spieler zu einer Frage.
     * Verarbeitet die erste richtige Antwort für Punkte und steuert den Übergang zur nächsten Frage.
     *
     * @param frage Die aktuelle Frage
     */
    private static void warteAufAntworten(Frage frage) {
        boolean[] hatGeantwortetFlag = new boolean[clients.size()];
        String ersterRichtigerSpieler = null;

        synchronized (LOCK) {
            while (true) {
                boolean alleGeantwortet = true;
                for (int i = 0; i < clients.size(); i++) {
                    ClientHandler client = clients.get(i);
                    if (!hatGeantwortetFlag[i]) {
                        alleGeantwortet = false;
                        if (client.hatGeantwortet()) {
                            hatGeantwortetFlag[i] = true;
                            String antwort = client.getAntwort();

                            if (antwort != null && antwort.equalsIgnoreCase(frage.richtig)) {
                                if (ersterRichtigerSpieler == null) {
                                    ersterRichtigerSpieler = client.getSpielerName();
                                    punkteMap.merge(ersterRichtigerSpieler, 1, Integer::sum);
                                    client.sendeNachricht("RICHTIG|Du hast als Erster richtig geantwortet!");

                                    // Benachrichtige andere Spieler
                                    for (ClientHandler other : clients) {
                                        if (!other.getSpielerName().equals(ersterRichtigerSpieler)) {
                                            other.sendeNachricht("LANGSAM|" + ersterRichtigerSpieler + " hat die Frage Richtig beantwortet.");
                                        }
                                    }
                                }
                            } else {
                                client.sendeNachricht("FALSCH|Richtig wäre " + frage.richtig + ".");
                            }
                            client.resetAntwort();
                        }
                    }
                }

                // Breche ab, wenn jemand richtig geantwortet hat
                if (ersterRichtigerSpieler != null) {
                    break;
                }

                if (alleGeantwortet) {
                    break;
                }

                try {
                    LOCK.wait(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            beantworteteFragen.add(frage.id);

            // Warte 5 Sekunden bevor nächste Frage kommt
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            broadcastNachricht("NÄCHSTE_FRAGE");
        }
    }

    /**
     * Überprüft, ob es einen Gewinner gibt (Spieler mit mindestens 5 Punkten).
     *
     * @return true, wenn ein Gewinner vorhanden ist, sonst false
     */
    private static boolean hatGewinner() {
        return punkteMap.values().stream().anyMatch(punkte -> punkte >= 5);
    }

    /**
     * Sendet eine Nachricht an alle verbundenen Clients.
     *
     * @param nachricht Die Nachricht, die gesendet wird
     */
    private static void broadcastNachricht(String nachricht) {
        synchronized (clients) {
            for (ClientHandler client : clients) {
                client.sendeNachricht(nachricht);
            }
        }
    }

    /**
     * Zeigt den Gewinner des Spiels an, basierend auf den Punkten.
     */
    private static void zeigeGewinner() {
        Optional<Map.Entry<String, Integer>> gewinnerOpt = punkteMap.entrySet().stream()
                .max(Map.Entry.comparingByValue());
        String gewinner = gewinnerOpt.map(Map.Entry::getKey).orElse("Unentschieden");
        int punkte = punkteMap.getOrDefault(gewinner, 0);
        broadcastNachricht("GEWINNER|" + gewinner + " hat mit " + punkte + " Punkten gewonnen!");

        // Warte 5 Sekunden und sende dann NEUES_SPIEL
        new Thread(() -> {
            try {
                Thread.sleep(5000);
                broadcastNachricht("NEUES_SPIEL");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    /**
     * Setzt das Spiel zurück, um eine neue Runde zu starten.
     */
    private static void resetSpiel() {
        punkteMap.clear();
        beantworteteFragen.clear();
        bereitgestellteSpieler = 0;
        for (ClientHandler client : clients) {
            client.resetAntwort();
        }
    }

    // --- Datenklassen für JSON-Parsing ---

    /**
     * Containerklasse für die JSON-Struktur der Fragen.
     */
    public static class FragenContainer {
        public List<Frage> fragen;
    }

    /**
     * Klasse zur Darstellung einer Frage.
     */
    public static class Frage {
        public int id;
        public String frage;
        public Antworten antworten;
        public String richtig;
    }

    /**
     * Klasse zur Darstellung der Antwortmöglichkeiten.
     */
    public static class Antworten {
        public String A;
        public String B;
        public String C;
    }

    // --- Client-Handler-Klasse ---

    /**
     * Handler für einen einzelnen Client.
     */
    public static class ClientHandler implements Runnable {

        private final Socket socket;
        private final String spielerName;
        private PrintWriter ausgang;
        private BufferedReader eingang;
        private String aktuelleAntwort;
        private boolean hatGeantwortet;

        /**
         * Konstruktor für den Client-Handler.
         *
         * @param socket Die Socket-Verbindung zum Client
         * @param spielerName Der Name des Spielers
         * @throws IOException Bei IO-Fehlern
         */
        public ClientHandler(Socket socket, String spielerName) throws IOException {
            this.socket = socket;
            this.spielerName = spielerName;
            this.ausgang = new PrintWriter(socket.getOutputStream(), true);
            this.eingang = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.aktuelleAntwort = null;
            punkteMap.put(spielerName, 0);
            this.hatGeantwortet = false;
        }

        /**
         * Laufmethode des Threads, liest Nachrichten vom Client.
         */
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
                    bereitgestellteSpieler--;
                }
            }
        }

        /**
         * Sendet eine Frage an den Client.
         *
         * @param frage Die Frage, die gesendet wird
         */
        public void sendeFrage(Frage frage) {
            ausgang.println("FRAGE|" + frage.frage);
            ausgang.println("A: " + frage.antworten.A);
            ausgang.println("B: " + frage.antworten.B);
            ausgang.println("C: " + frage.antworten.C);
            hatGeantwortet = false;
        }

        /**
         * Sendet eine Nachricht an den Client.
         *
         * @param nachricht Die Nachricht
         */
        public void sendeNachricht(String nachricht) {
            ausgang.println(nachricht);
        }

        /**
         * Gibt den Spielernamen zurück.
         *
         * @return Der Spielername
         */
        public String getSpielerName() {
            return spielerName;
        }

        /**
         * Gibt die letzte Antwort des Clients zurück.
         *
         * @return Die letzte Antwort des Clients
         */
        public String getAntwort() {
            return aktuelleAntwort;
        }

        /**
         * Gibt zurück, ob der Client geantwortet hat.
         *
         * @return true, wenn geantwortet wurde, sonst false
         */
        public boolean hatGeantwortet() {
            return hatGeantwortet;
        }

        /**
         * Setzt die Antwort des Clients zurück.
         */
        public void resetAntwort() {
            aktuelleAntwort = null;
            hatGeantwortet = false;
        }
    }

}
