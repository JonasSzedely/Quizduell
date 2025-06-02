package org.Codes.Alte_Programme;

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
public class Server2_ {
    private static List<Frage> fragenListe = new ArrayList<>();
    private static final List<ClientHandler> clients = Collections.synchronizedList(new ArrayList<>());
    private static final Map<String, Integer> punkteMap = new ConcurrentHashMap<>();
    private static final Set<Integer> beantworteteFragen = Collections.synchronizedSet(new HashSet<>());
    private static final int PORT = 1404;
    private static final int MAX_SPIELER = 2;
    private static final Object LOCK = new Object();
    private static final String JSON_PFAD = "src/Ordner_Fragen/fragen.json";
    private static boolean wartetAufStart = true;
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
                client.sendeNachricht("SPIELERNAME|" + spielerName);
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
        wartetAufStart = false;

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

    private static void sendeFrageAnAlle(Frage frage) {
        synchronized (LOCK) {
            for (ClientHandler client : clients) {
                client.sendeFrage(frage);
            }
        }
    }

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
                            // Überprüfe, ob die Antwort richtig ist
                            if (antwort != null && antwort.equalsIgnoreCase(frage.richtig)) {
                                // Vergewissere dich, dass nur der erste Spieler Punkte erhält
                                if (ersterRichtigerSpieler == null) {
                                    ersterRichtigerSpieler = client.getSpielerName();
                                    punkteMap.merge(ersterRichtigerSpieler, 1, Integer::sum);
                                    client.sendeNachricht("RICHTIG|Du hast als Erster richtig geantwortet! Punkte: " + punkteMap.get(ersterRichtigerSpieler));
                                    broadcastNachricht("PUNKT|" + ersterRichtigerSpieler + " hat als Erster richtig geantwortet und erhält 1 Punkt!");
                                } else {
                                    // Falls dieser Spieler nicht der erste ist
                                    client.sendeNachricht("RICHTIG|Du hast richtig geantwortet, aber nur der Erste hat Punkte erhalten.");
                                }
                            } else {
                                client.sendeNachricht("FALSCH|Richtig wäre " + frage.richtig + ".");
                            }
                            client.resetAntwort();  // Reset für den nächsten Durchlauf
                        }
                    }
                }

                if (alleGeantwortet || ersterRichtigerSpieler != null) break;

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
        bereitgestellteSpieler = 0;
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
                    bereitgestellteSpieler--;
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

