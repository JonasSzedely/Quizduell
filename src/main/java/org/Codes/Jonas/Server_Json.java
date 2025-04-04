// server:
package org.Codes.Jonas;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import com.google.gson.*;

public class Server_Json {
    public static List<Frage> fragenListe = new ArrayList<>();
    private static List<ClientHandler> clients = new ArrayList<>();
    private static final int PORT = 1404;
    private static final int POOL_SIZE = 10;
    private static final long COUNTDOWN_DELAY = 5000;
    private static final int WINNING_POINTS = 5;
    private static final int QUESTION_DELAY = 5000;
    private static boolean gameInProgress = false;
    private static boolean waitingForStart = true;
    private static int currentQuestionIndex = 0;
    private static boolean allAnswered = false;

    public static void main(String[] args) {
        ladeFragen("src/Ordner_Fragen/fragen.json");

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server läuft auf Port " + PORT);
            ExecutorService pool = Executors.newFixedThreadPool(POOL_SIZE);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                ClientHandler clientHandler = new ClientHandler(clientSocket);
                clients.add(clientHandler);
                pool.execute(clientHandler);
                System.out.println("Neuer Spieler verbunden. Aktive Spieler: " + clients.size());

                updateClientStatus();
            }
        } catch (IOException e) {
            System.err.println("Serverfehler: " + e.getMessage());
        }
    }

    private static void updateClientStatus() {
        String status;
        if (gameInProgress) {
            status = "GAME_IN_PROGRESS";
        } else if (clients.size() < 2) {
            status = "WAITING_FOR_PLAYERS|Es werden mindestens 2 Spieler benötigt";
        } else if (clients.size() >= 10) {
            status = "MAX_PLAYERS_REACHED|Maximal 10 Spieler erreicht";
        } else {
            status = "READY_TO_START|Warte auf Start (" + clients.size() + "/10 Spieler)";
        }

        for (ClientHandler client : clients) {
            client.sendMessage("STATUS|" + status);
            client.setCanStartGame(clients.size() >= 2 && clients.size() <= 10 && !gameInProgress && waitingForStart);
        }
    }

    private static void ladeFragen(String dateiPfad) {
        try {
            String json = new String(Files.readAllBytes(Paths.get(dateiPfad)));
            Gson gson = new Gson();
            FragenContainer container = gson.fromJson(json, FragenContainer.class);
            fragenListe = container.fragen;
            System.out.println("Geladene Fragen: " + fragenListe.size());
        } catch (IOException e) {
            System.err.println("Fehler beim Laden der Fragen: " + e.getMessage());
        }
    }

    public static synchronized void startGame() {
        if (gameInProgress || clients.size() < 2 || clients.size() > 10) {
            return;
        }

        waitingForStart = false;
        gameInProgress = true;
        currentQuestionIndex = 0;
        System.out.println("Spiel startet mit " + clients.size() + " Spielern");
        broadcastMessage("COUNTDOWN_START");

        new Thread(() -> {
            try {
                Thread.sleep(COUNTDOWN_DELAY);
                playGame();
            } catch (InterruptedException | IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private static void playGame() throws IOException {
        Collections.shuffle(fragenListe);
        boolean gameRunning = true;

        while (currentQuestionIndex < fragenListe.size() && gameRunning) {
            Frage frage = fragenListe.get(currentQuestionIndex);
            broadcastQuestion(frage);
            allAnswered = false; // Reset flag for the new question

            List<String> answers = new ArrayList<>();
            boolean correctAnswerGiven = false;
            ExecutorService answerPool = Executors.newFixedThreadPool(clients.size());
            List<Future<String>> futureAnswers = new ArrayList<>();

            for (ClientHandler client : clients) {
                futureAnswers.add(answerPool.submit(() -> client.waitForAnswer()));
            }

            answerPool.shutdown();
            try {
                answerPool.awaitTermination(60, TimeUnit.SECONDS); // Give clients some time to answer
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            for (Future<String> future : futureAnswers) {
                try {
                    String answer = future.get();
                    if (answer != null) {
                        answers.add(answer);
                        // Find the client who sent this answer to update their points
                        for (ClientHandler client : clients) {
                            if (client.getLastReceivedMessage().equals(answer)) {
                                if (answer.equalsIgnoreCase(frage.richtig)) {
                                    client.incrementPoints();
                                    correctAnswerGiven = true;
                                    if (client.getPoints() >= WINNING_POINTS) {
                                        announceWinner(client);
                                        gameRunning = false;
                                        break;
                                    }
                                }
                                break; // Move to the next answer
                            }
                        }
                    } else {
                        answers.add("TIMEOUT"); // Handle cases where client didn't answer in time
                    }
                } catch (InterruptedException | ExecutionException e) {
                    answers.add("ERROR");
                    e.printStackTrace();
                }
                if (!gameRunning) break;
            }
            allAnswered = true; // Consider all active clients have (or haven't) answered

            if (gameRunning) {
                broadcastResults(answers, frage.richtig, correctAnswerGiven);
                if (currentQuestionIndex < fragenListe.size() - 1) {
                    broadcastMessage("NEXT_QUESTION_IN|" + (COUNTDOWN_DELAY / 1000));
                    try {
                        Thread.sleep(COUNTDOWN_DELAY);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    currentQuestionIndex++;
                } else {
                    gameRunning = false;
                    broadcastMessage("GAME_OVER|Alle Fragen beantwortet");
                }
            }
        }

        if (gameRunning) {
            broadcastMessage("GAME_OVER|Kein Gewinner - alle Fragen beantwortet");
        }
        resetGame();
    }

    private static void broadcastMessage(String message) {
        for (ClientHandler client : clients) {
            client.sendMessage(message);
        }
    }

    private static void broadcastQuestion(Frage frage) {
        for (ClientHandler client : clients) {
            client.sendQuestion(frage);
        }
    }

    private static void broadcastResults(List<String> answers, String correctAnswer, boolean correctAnswerGiven) {
        String message = "RESULTS|" + String.join(",", answers) + "|" +
                correctAnswer + "|" + correctAnswerGiven;
        for (ClientHandler client : clients) {
            client.sendMessage(message);
        }
    }

    private static void announceWinner(ClientHandler winner) {
        for (ClientHandler client : clients) {
            if (client == winner) {
                client.sendMessage("WINNER|Du hast gewonnen mit " + winner.getPoints() + " Punkten!");
            } else {
                client.sendMessage("LOST|Der Gewinner hat " + winner.getPoints() + " Punkte erreicht!");
            }
        }
    }

    private static void resetGame() {
        gameInProgress = false;
        waitingForStart = true;

        for (ClientHandler client : clients) {
            client.resetPoints();
        }

        updateClientStatus();
    }

    private static class FragenContainer {
        List<Frage> fragen;
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

    private static class ClientHandler implements Runnable {
        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        private int points = 0;
        private String lastReceivedMessage = "";
        private boolean canStartGame = false;

        public ClientHandler(Socket socket) throws IOException {
            this.socket = socket;
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        }

        @Override
        public void run() {
            try {
                String input;
                while ((input = in.readLine()) != null) {
                    lastReceivedMessage = input;
                    if (input.equals("START") && canStartGame) {
                        startGame();
                    }
                }
            } catch (IOException e) {
                System.err.println("Client-Verbindung unterbrochen: " + e.getMessage());
            } finally {
                try {
                    socket.close();
                } catch (IOException e) {
                    System.err.println("Fehler beim Schließen des Sockets: " + e.getMessage());
                }
                clients.remove(this);
                System.out.println("Spieler disconnected. Verbleibende Spieler: " + clients.size());
                updateClientStatus();
            }
        }

        public void sendMessage(String message) {
            out.println(message);
        }

        public void sendQuestion(Frage frage) {
            out.println("QUESTION|" + frage.frage + "|" +
                    frage.antworten.A + "|" +
                    frage.antworten.B + "|" +
                    frage.antworten.C);
        }

        public String waitForAnswer() throws IOException {
            return in.readLine();
        }

        public synchronized void incrementPoints() {
            points++;
            sendMessage("POINTS|" + points);
        }

        public synchronized int getPoints() {
            return points;
        }

        public synchronized void resetPoints() {
            points = 0;
            sendMessage("POINTS|0");
        }

        public void setCanStartGame(boolean canStart) {
            this.canStartGame = canStart;
            sendMessage("CAN_START|" + canStart);
        }

        public String getLastReceivedMessage() {
            return lastReceivedMessage;
        }
    }
}