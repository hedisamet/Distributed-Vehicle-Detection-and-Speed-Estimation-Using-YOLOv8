package fog;
import java.io.*;
import java.net.Socket;

public class Client {

	private static final String HOST_IP = "10.26.13.6"; // Host IP addressr
    private static final int PORT = 5003; // Same port used by the host

    public static void main(String[] args) {
        clientProcess();
    }

    public static void clientProcess() {
        try (Socket socket = new Socket(HOST_IP, PORT)) {
            System.out.println("Connected to Host!");

            // Receive the video part from the host
            receiveVideoPart(socket);

            // Process the received video part
            String jsonOutput = "client_output.json";
            String videoOutput = "client_output.mp4";
            processVideoPart("received_part.mp4", jsonOutput, videoOutput);

            // Send the processed video part back to the host
            sendProcessedVideo(HOST_IP, PORT, videoOutput);

            System.out.println("Video processing done and sent back to host.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Function to receive video part from the host
    public static void receiveVideoPart(Socket socket) {
        try (InputStream is = socket.getInputStream();
             FileOutputStream fos = new FileOutputStream("received_part.mp4")) {

            byte[] buffer = new byte[4096];
            int count;
            while ((count = is.read(buffer)) > 0) {
                fos.write(buffer, 0, count);
            }
            System.out.println("Video part received from host.");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Function to process the video part
    public static void processVideoPart(String videoPart, String jsonOutput, String videoOutput) {
        try {
            ProcessBuilder pb = new ProcessBuilder("python", "C:/Users/21697/OneDrive/Bureau/fog/speed-estimation.py", videoPart, jsonOutput, videoOutput);
            pb.inheritIO();
            Process p = pb.start();
            int exitCode = p.waitFor();

            if (exitCode == 0) {
                File outputFile = new File(videoOutput);
                if (outputFile.exists()) {
                    System.out.println("Video processing completed successfully.");
                } else {
                    System.out.println("Video processing failed: Output file not found.");
                }
            } else {
                System.out.println("Video processing failed with exit code: " + exitCode);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Function to send processed video back to host
    public static void sendProcessedVideo(String hostIP, int port, String videoOutput) {
        try (Socket socket = new Socket(hostIP, port);
             FileInputStream fis = new FileInputStream(videoOutput);
             OutputStream os = socket.getOutputStream()) {

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
            System.out.println("Processed video sent back to host.");

            // Flush the output stream and close the socket
            os.flush();
            socket.shutdownOutput();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}