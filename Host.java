package fog;

package video;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class Host {
    private static final int BASE_PORT = 5002; // Base port for client connections
    private static final int NUM_CLIENTS = 2; // Number of clients

    public static void main(String[] args) {
        String videoPath = "sample_video.mp4"; // Input video
        String outputPath = "output_merged.mp4"; // Merged output video

        // Split the video into (NUM_CLIENTS + 1) parts
        List<String> videoParts = splitVideo(videoPath, NUM_CLIENTS + 1);

        // List of client sockets for initial connections
        List<Socket> clientSockets = new ArrayList<>();

        // Accept client connections
        acceptClientConnections(clientSockets);

        // Send video parts to clients
        for (int i = 0; i < NUM_CLIENTS; i++) {
            final Socket clientSocket = clientSockets.get(i);
            final String videoPart = videoParts.get(i + 1); // Start from part2 for clients
            sendVideoPartToClient(clientSocket, videoPart);
        }

        // Latch to synchronize all processing (host + clients)
        CountDownLatch latch = new CountDownLatch(NUM_CLIENTS + 1);

        // Fixed list to store processed video parts in correct order
        List<String> processedParts = new ArrayList<>(Collections.nCopies(NUM_CLIENTS + 1, null));
        processedParts.set(0, "host_output.mp4"); // First part processed locally

        // Process video locally on the host
        ExecutorService executor = Executors.newFixedThreadPool(NUM_CLIENTS + 1);
        executor.submit(() -> {
            processVideoPartLocally(videoParts.get(0), "host_output.json", "host_output.mp4");
            latch.countDown();
        });

        // Receive processed parts from clients
        for (int i = 0; i < NUM_CLIENTS; i++) {
            final int clientIndex = i + 1; // Match index with videoParts
            executor.submit(() -> {
                try (ServerSocket serverSocket = new ServerSocket(BASE_PORT + clientIndex)) {
                    System.out.println("Waiting for processed video from client " + clientIndex + "...");
                    try (Socket socket = serverSocket.accept()) {
                        String clientOutput = "client_output_" + clientIndex + ".mp4";
                        receiveProcessedVideo(socket, clientOutput);
                        processedParts.set(clientIndex, clientOutput); // Set part at correct index
                        System.out.println("Processed video received from client " + clientIndex + ".");
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                latch.countDown();
            });
        }

        try {
            // Wait for all processing to finish
            latch.await();

            // Merge all processed video parts in correct order
            mergeVideos(processedParts, outputPath);
            System.out.println("Video processing is complete, and videos have been merged.");
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            executor.shutdown();
        }
    }

    private static void acceptClientConnections(List<Socket> clientSockets) {
        for (int i = 0; i < NUM_CLIENTS; i++) {
            int port = BASE_PORT + i + 1;
            try (ServerSocket serverSocket = new ServerSocket(port)) {
                System.out.println("Waiting for client " + (i + 1) + " on port " + port);
                Socket clientSocket = serverSocket.accept();
                clientSockets.add(clientSocket);
                System.out.println("Client " + (i + 1) + " connected.");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static List<String> splitVideo(String videoPath, int numParts) {
        List<String> videoParts = new ArrayList<>();
        try {
            ProcessBuilder pb = new ProcessBuilder("python", "split_video.py", videoPath, String.valueOf(numParts));
            pb.inheritIO();
            Process process = pb.start();
            process.waitFor();

            for (int i = 1; i <= numParts; i++) {
                videoParts.add("part" + i + ".mp4");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return videoParts;
    }

    private static void sendVideoPartToClient(Socket clientSocket, String videoPart) {
        try (FileInputStream fis = new FileInputStream(videoPart);
             OutputStream os = clientSocket.getOutputStream()) {

            byte[] buffer = new byte[4096];
            int count;
            while ((count = fis.read(buffer)) > 0) {
                os.write(buffer, 0, count);
            }
            os.flush();
            System.out.println("Video part sent to client: " + videoPart);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void processVideoPartLocally(String videoPart, String jsonOutput, String videoOutput) {
        try {
            ProcessBuilder pb = new ProcessBuilder("python", "speed-estimation.py", videoPart, jsonOutput, videoOutput);
            pb.inheritIO();
            Process p = pb.start();
            p.waitFor();
            System.out.println("Host processed part: " + videoOutput);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void receiveProcessedVideo(Socket socket, String outputPath) {
        try (InputStream is = socket.getInputStream();
             FileOutputStream fos = new FileOutputStream(outputPath)) {

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }
            System.out.println("Processed video received: " + outputPath);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void mergeVideos(List<String> videoFiles, String outputPath) {
        try {
            ProcessBuilder pb = new ProcessBuilder("python", "merge_videos.py", String.join(",", videoFiles), outputPath);
            pb.inheritIO();
            Process p = pb.start();
            p.waitFor();
            System.out.println("Videos merged into: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
