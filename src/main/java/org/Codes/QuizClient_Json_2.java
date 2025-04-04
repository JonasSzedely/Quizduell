// client:
package org.example;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;

public class QuizClient2 {
    private JFrame frame;
    private JLabel questionLabel;
    private JButton[] answerButtons;
    private JButton startButton;
    private JLabel statusLabel;
    private JLabel pointsLabel;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private int points = 0;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new QuizClient2());
    }

    public QuizClient2() {
        initializeUI();
        connectToServer();
    }

    private void initializeUI() {
        frame = new JFrame("QuizDuell");
        frame.setSize(800, 600);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout(10, 10));

        questionLabel = new JLabel("Warte auf Spielstart...", SwingConstants.CENTER);
        questionLabel.setFont(new Font("Arial", Font.BOLD, 24));
        frame.add(questionLabel, BorderLayout.NORTH);

        pointsLabel = new JLabel("Punkte: 0", SwingConstants.CENTER);
        pointsLabel.setFont(new Font("Arial", Font.BOLD, 16));
        frame.add(pointsLabel, BorderLayout.SOUTH);

        JPanel answerPanel = new JPanel(new GridLayout(3, 1, 10, 10));
        answerButtons = new JButton[3];
        for (int i = 0; i < 3; i++) {
            answerButtons[i] = new JButton();
            answerButtons[i].setFont(new Font("Arial", Font.PLAIN, 18));
            final char answer = (char) ('A' + i);
            answerButtons[i].addActionListener(e -> submitAnswer(answer));
            answerButtons[i].setEnabled(false);
            answerPanel.add(answerButtons[i]);
        }

        JPanel controlPanel = new JPanel(new BorderLayout(10, 10));
        statusLabel = new JLabel("Verbinde mit Server...", SwingConstants.CENTER);
        startButton = new JButton("Spiel starten");
        startButton.addActionListener(e -> startGame());
        startButton.setEnabled(false);

        controlPanel.add(statusLabel, BorderLayout.NORTH);
        controlPanel.add(startButton, BorderLayout.CENTER);
        controlPanel.add(answerPanel, BorderLayout.SOUTH);

        frame.add(controlPanel, BorderLayout.CENTER);
        frame.setVisible(true);
    }

    private void connectToServer() {
        try {
            socket = new Socket("localhost", 1404);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            new Thread(this::listenToServer).start();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(frame, "Verbindungsfehler: " + e.getMessage());
            System.exit(1);
        }
    }

    private void listenToServer() {
        try {
            String message;
            while ((message = in.readLine()) != null) {
                processServerMessage(message);
            }
        } catch (IOException e) {
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(frame, "Verbindung zum Server verloren");
                frame.dispose();
            });
        }
    }

    private void processServerMessage(String message) {
        String[] parts = message.split("\\|");
        switch (parts[0]) {
            case "STATUS":
                updateStatus(parts[1]);
                break;
            case "COUNTDOWN_START":
                startCountdown();
                break;
            case "QUESTION":
                showQuestion(parts);
                break;
            case "RESULTS":
                showResults(parts);
                break;
            case "WINNER":
            case "LOST":
                gameOver(parts);
                break;
            case "POINTS":
                updatePoints(parts[1]);
                break;
            case "GAME_OVER":
                gameOverNoWinner(parts);
                break;
            case "CAN_START":
                setStartButtonEnabled(Boolean.parseBoolean(parts[1]));
                break;
            case "NEXT_QUESTION_IN":
                startNextQuestionCountdown(Integer.parseInt(parts[1]));
                break;
        }
    }

    private void updateStatus(String status) {
        SwingUtilities.invokeLater(() -> {
            if (status.startsWith("WAITING_FOR_PLAYERS")) {
                statusLabel.setText(status.split("\\|")[1]);
            } else if (status.startsWith("READY_TO_START")) {
                statusLabel.setText(status.split("\\|")[1]);
            } else if (status.startsWith("GAME_IN_PROGRESS")) {
                statusLabel.setText("Spiel läuft bereits - bitte warten");
            } else if (status.startsWith("MAX_PLAYERS_REACHED")) {
                statusLabel.setText(status.split("\\|")[1]);
            }
        });
    }

    private void startCountdown() {
        SwingUtilities.invokeLater(() -> {
            startButton.setEnabled(false);
            disableAnswerButtons();

            new Thread(() -> {
                for (int i = 5; i > 0; i--) {
                    final int count = i;
                    SwingUtilities.invokeLater(() -> {
                        statusLabel.setText("Spiel startet in " + count + "...");
                    });
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Spiel läuft! Wähle deine Antwort");
                });
            }).start();
        });
    }

    private void showQuestion(String[] parts) {
        SwingUtilities.invokeLater(() -> {
            questionLabel.setText(parts[1]);
            answerButtons[0].setText("A: " + parts[2]);
            answerButtons[1].setText("B: " + parts[3]);
            answerButtons[2].setText("C: " + parts[4]);

            enableAnswerButtons();
            statusLabel.setText("Wähle deine Antwort");
        });
    }

    private void submitAnswer(char answer) {
        out.println(String.valueOf(answer));
        SwingUtilities.invokeLater(() -> {
            disableAnswerButtons();
            statusLabel.setText("Antwort gesendet! Warte auf Ergebnisse...");
        });
    }

    private void showResults(String[] parts) {
        String correctAnswer = parts[2];
        boolean correctAnswerGiven = Boolean.parseBoolean(parts[3]);

        SwingUtilities.invokeLater(() -> {
            for (int i = 0; i < 3; i++) {
                if (answerButtons[i].getText().startsWith(correctAnswer + ":")) {
                    answerButtons[i].setBackground(correctAnswerGiven ? Color.GREEN : Color.RED);
                } else {
                    answerButtons[i].setBackground(null);
                }
            }

            statusLabel.setText(correctAnswerGiven ?
                    "Richtige Antwort gegeben!" :
                    "Keine richtigen Antworten!");

            disableAnswerButtons();
        });
    }

    private void startNextQuestionCountdown(int seconds) {
        SwingUtilities.invokeLater(() -> {
            disableAnswerButtons();
            new Thread(() -> {
                for (int i = seconds; i > 0; i--) {
                    final int count = i;
                    SwingUtilities.invokeLater(() -> {
                        statusLabel.setText("Nächste Frage in " + count + " Sekunden...");
                    });
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Bereit für die nächste Frage");
                    resetAnswerButtons();
                });
            }).start();
        });
    }

    private void gameOver(String[] parts) {
        SwingUtilities.invokeLater(() -> {
            questionLabel.setText("<html><center>" + parts[1] + "</center></html>");
            statusLabel.setText("Spiel beendet");
            startButton.setText("Neues Spiel");
            startButton.setEnabled(false); // Wait for server to enable it

            disableAnswerButtons();
            resetAnswerButtons();
        });
    }

    private void gameOverNoWinner(String[] parts) {
        SwingUtilities.invokeLater(() -> {
            questionLabel.setText("<html><center>" + parts[1] + "</center></html>");
            statusLabel.setText("Alle Fragen beantwortet");
            startButton.setText("Neues Spiel");
            startButton.setEnabled(false); // Wait for server to enable it

            disableAnswerButtons();
            resetAnswerButtons();
        });
    }

    private void updatePoints(String pointsStr) {
        points = Integer.parseInt(pointsStr);
        SwingUtilities.invokeLater(() -> {
            pointsLabel.setText("Punkte: " + points);
        });
    }

    private void startGame() {
        out.println("START");
        startButton.setEnabled(false);
    }

    private void setStartButtonEnabled(boolean enabled) {
        SwingUtilities.invokeLater(() -> {
            startButton.setEnabled(enabled);
        });
    }

    private void enableAnswerButtons() {
        for (JButton btn : answerButtons) {
            btn.setEnabled(true);
            btn.setBackground(null);
        }
    }

    private void disableAnswerButtons() {
        for (JButton btn : answerButtons) {
            btn.setEnabled(false);
        }
    }

    private void resetAnswerButtons() {
        for (JButton btn : answerButtons) {
            btn.setBackground(null);
        }
    }
}