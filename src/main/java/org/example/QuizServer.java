package org.example;

import java.io.*;
import java.net.*;
import java.util.*;

public class QuizServer {
    private static final int PORT = 1404;
    private static List<Question> questions = new ArrayList<>();
    private static Map<Socket, String> clients = new HashMap<>();
    private static Map<String, Integer> scores = new HashMap<>();

    public static void main(String[] args) {
        loadQuestions("path/to/your/questions.txt");

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server is running...");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Client connected: " + clientSocket);

                clients.put(clientSocket, "");
                scores.put(clientSocket.toString(), 0);

                new Thread(new ClientHandler(clientSocket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void loadQuestions(String filePath) {
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            String questionText = "";
            String[] answers = new String[3];
            int index = 0;

            while ((line = br.readLine()) != null) {
                if (line.startsWith("# Frage")) {
                    if (!questionText.isEmpty()) {
                        questions.add(new Question(questionText, answers));
                        questionText = "";
                        answers = new String[3];
                        index = 0;
                    }
                } else if (line.startsWith("A") || line.startsWith("B") || line.startsWith("C")) {
                    answers[index++] = line.trim();
                } else {
                    questionText += line + " ";
                }
            }
            if (!questionText.isEmpty()) {
                questions.add(new Question(questionText, answers));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static class ClientHandler implements Runnable {
        private Socket clientSocket;

        public ClientHandler(Socket socket) {
            this.clientSocket = socket;
        }

        @Override
        public void run() {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                 PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {

                while (true) {
                    // Send questions to all clients
                    for (Question question : questions) {
                        out.println(question.getQuestion());
                        for (String answer : question.getAnswers()) {
                            out.println(answer);
                        }
                        // Wait for answers from clients
                        String answer = in.readLine();
                        if (question.isCorrect(answer)) {
                            scores.put(clientSocket.toString(), scores.get(clientSocket.toString()) + 1);
                            System.out.println("Correct answer from: " + clientSocket);
                            out.println("Correct!");
                        } else {
                            out.println("Wrong answer.");
                        }

                        // Send scores to all clients
                        for (Socket socket : clients.keySet()) {
                            PrintWriter socketOut = new PrintWriter(socket.getOutputStream(), true);
                            socketOut.println("Scores: " + scores);
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}

class Question {
    private String question;
    private String[] answers;

    public Question(String question, String[] answers) {
        this.question = question;
        this.answers = answers;
    }

    public String getQuestion() {
        return question;
    }

    public String[] getAnswers() {
        return answers;
    }

    public boolean isCorrect(String answer) {
        return answer.equals(answers[0]); // Assuming the first answer is always the correct one
    }
}
