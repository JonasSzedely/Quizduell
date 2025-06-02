package org.Codes.Vorlagen;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Ein Quiz-Server, der Fragen aus einer JSON-Datei lädt und mit mehreren Clients interagiert.
 * Die Clients erhalten synchronisierte Fragen und können gegeneinander antreten.
 */
public class Server {
    /** Liste aller geladenen Fragen. */
    private static List<Frage> fragenListe = new ArrayList<>();

    /** Liste aller verbundenen Clients. */
    private static final List<ClientHandler> clients = new ArrayList<>();

    /** Map zur Speicherung der Punkte jedes Spielers. */
    private static final Map<String, Integer> punkteMap = new ConcurrentHashMap<>();

    /** Set zur Vermeidung doppelter Fragen. */
    private static final Set<Integer> beantworteteFragen = ConcurrentHashMap.newKeySet();

    /** Portnummer für die Serververbindung. */
    private static final int PORT = 1404;

    /** Maximale Anzahl an Spielern. */
    private static final int MAX_SPIELER = 2;

    /** Lock-Objekt für Thread-Synchronisation. */
    private static final Object LOCK = new Object();

    /** Pfad zur JSON-Datei mit den Fragen. */
    private static final String JSON_PFAD = "src/Ordner_Fragen/fragen.json";

    /** Startet das Spiel, wenn deaktiviert*/
    private static boolean wartetAufStart = true;

    /**
     * Hauptmethode
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
                pool.execute(client);
                System.out.println(spielerName + " verbunden!");

                // Status an alle Clients senden
                broadcastStatus();
            }
        } catch (IOException e) {
            System.err.println("Serverfehler: " + e.getMessage());
        }
    }

    /**
     * Lädt Fragen aus einer JSON-Datei.
     * @param dateiPfad Pfad zur JSON-Datei wird benötigt als Eingabe.
     */
    private static void ladeFragenAusJson(String dateiPfad) {
        try {
            String json = new String(Files.readAllBytes(Paths.get(dateiPfad)));
            Gson gson = new Gson();
            FragenContainer container = gson.fromJson(json, FragenContainer.class);
            fragenListe = container.fragen;
            System.out.println(fragenListe.size() + " Fragen geladen.");
        } catch (IOException | JsonSyntaxException e) {
            System.err.println("Fehler beim Laden der JSON-Datei: " + e.getMessage());
        }
    }

    /**
     * Sendet den Status, ob das Spiel läuft an alle Clients.
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
     * Startet das Quiz-Spiel für alle verbundenen Clients.
     */
    private static void starteSpiel() {
        System.out.println("Spiel startet mit " + clients.size() + " Spielern!");
        broadcastNachricht("SPIEL_STARTET");

        new Thread(() -> {
            for (Frage frage : fragenListe) {
                if (beantworteteFragen.contains(frage.id)) continue;

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
     * @param frage Die zu sendende Frage.
     */
    private static void sendeFrageAnAlle(Frage frage) {
        synchronized (LOCK) {
            clients.forEach(client -> client.sendeFrage(frage));
        }
    }

    /**
     * Wartet, bis alle Clients geantwortet haben.
     * @param frage Die aktuelle Frage.
     */
    private static void warteAufAntworten(Frage frage) {
        synchronized (LOCK) {
            while (true) {
                boolean alleGeantwortet = clients.stream()
                        .allMatch(ClientHandler::hatGeantwortet);

                if (alleGeantwortet) break;

                try {
                    LOCK.wait(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            // Auswertung der Antworten
            clients.forEach(client -> {
                String antwort = client.getAntwort();
                if (antwort != null && antwort.equalsIgnoreCase(frage.richtig)) {
                    punkteMap.merge(client.getSpielerName(), 1, Integer::sum);
                    client.sendeNachricht("RICHTIG|Punkte: " + punkteMap.get(client.getSpielerName()));
                } else {
                    client.sendeNachricht("FALSCH|Richtig wäre " + frage.richtig + ".");
                }
                client.resetAntwort();
            });

            beantworteteFragen.add(frage.id);
            broadcastNachricht("NÄCHSTE_FRAGE");
        }
    }

    /**
     * Überprüft, ob ein Spieler die Gewinnbedingung (5 Punkte) erreicht hat.
     * @return true, falls ein Gewinner feststeht.
     */
    private static boolean hatGewinner() {
        return punkteMap.values().stream().anyMatch(punkte -> punkte >= 5);
    }

    /**
     * Sendet eine Nachricht an alle Clients.
     * @param nachricht Die zu sendende Nachricht.
     */
    private static void broadcastNachricht(String nachricht) {
        clients.forEach(client -> client.sendeNachricht(nachricht));
    }

    /**
     * Zeigt den Gewinner an und sendet die Ergebnisse an alle Clients.
     */
    private static void zeigeGewinner() {
        String gewinner = punkteMap.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("Unentschieden");

        broadcastNachricht("GEWINNER|" + gewinner + " hat mit " + punkteMap.get(gewinner) + " Punkten gewonnen!");
    }

    /**
     * Setzt das Spiel zurück für eine neue Runde.
     */
    private static void resetSpiel() {
        punkteMap.clear();
        beantworteteFragen.clear();
        clients.forEach(ClientHandler::resetAntwort);
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

        /**
         * Konstruktor für einen neuen Client-Handler.
         * @param socket Der Socket des Clients.
         * @param spielerName Der Name des Spielers.
         */
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
                while (true) {
                    String nachricht = eingang.readLine();
                    if (nachricht == null) break;

                    if (nachricht.equals("START")) {
                        synchronized (LOCK) {
                            if (clients.size() == MAX_SPIELER && wartetAufStart) {
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
            }
        }

        /**
         * Sendet eine Frage an den Client.
         * @param frage Die zu sendende Frage.
         */
        public void sendeFrage(Frage frage) {
            ausgang.println("FRAGE|" + frage.frage);  // Nur Fragetext ohne ID
            ausgang.println("A: " + frage.antworten.A);
            ausgang.println("B: " + frage.antworten.B);
            ausgang.println("C: " + frage.antworten.C);
            hatGeantwortet = false;
        }

        /**
         * Sendet eine Nachricht an den Client.
         * @param nachricht Die zu sendende Nachricht.
         */
        public void sendeNachricht(String nachricht) {
            ausgang.println(nachricht);
        }

        /**
         * Gibt den Namen des Spielers zurück.
         * @return Der Spielername.
         */
        public String getSpielerName() {
            return spielerName;
        }

        /**
         * Gibt die aktuelle Antwort des Clients zurück.
         * @return Die Antwort (A, B oder C).
         */
        public String getAntwort() {
            return aktuelleAntwort;
        }

        /**
         * Überprüft, ob der Client geantwortet hat.
         * @return true, falls eine Antwort vorliegt.
         */
        public boolean hatGeantwortet() {
            return hatGeantwortet;
        }

        /**
         * Setzt die Antwort des Clients zurück.
         */
        public void resetAntwort() {
            aktuelleAntwort = null;
        }
    }
}