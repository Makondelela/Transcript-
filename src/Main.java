import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import javax.swing.JFileChooser;

public class Main {

    private static final String API_KEY = "2127a972d43846358ed7acbfc4cbed09";

    public static void main(String[] args) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .build();

        httpClient = httpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        File selectedFile = chooseFile(); // Prompt user to choose a file
        if (selectedFile != null) {
            String uploadUrl;
            try {
                uploadUrl = uploadFile(selectedFile.getPath(), httpClient);
                Transcript transcript = createTranscript(uploadUrl, httpClient);
                transcript = waitForTranscriptToProcess(transcript, httpClient);

                String[] note;
                note = transcript.getText().split(",");
                for(int i=0; i<note.length; i++) {
                	System.out.println(note[i]);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            System.out.println("No file selected. Exiting.");
        }
    }

    private static File chooseFile() {
        JFileChooser fileChooser = new JFileChooser();
        int result = fileChooser.showOpenDialog(null);

        if (result == JFileChooser.APPROVE_OPTION) {
            return fileChooser.getSelectedFile();
        } else {
            return null;
        }
    }

    private static String uploadFile(String filePath, HttpClient httpClient) throws IOException, InterruptedException {
        Path path = Paths.get(filePath);
        InputStream fileStream = new FileInputStream(path.toFile());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.assemblyai.com/v2/upload"))
                .header("Authorization", API_KEY)
                .header("Content-Type", "application/octet-stream")
                .POST(HttpRequest.BodyPublishers.ofInputStream(() -> fileStream))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            JsonObject jsonResponse = new Gson().fromJson(response.body(), JsonObject.class);
            return jsonResponse.get("upload_url").getAsString();
        } else {
            throw new RuntimeException("File upload failed with status code: " + response.statusCode());
        }
    }

    private static Transcript createTranscript(String audioUrl, HttpClient httpClient) throws IOException, InterruptedException {
        JsonObject data = new JsonObject();
        data.addProperty("audio_url", audioUrl);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.assemblyai.com/v2/transcript"))
                .header("Authorization", API_KEY)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(data.toString()))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            return new Gson().fromJson(response.body(), Transcript.class);
        } else {
            throw new RuntimeException("Transcript creation failed with status code: " + response.statusCode());
        }
    }

    private static Transcript waitForTranscriptToProcess(Transcript transcript, HttpClient httpClient) throws IOException, InterruptedException {
        String pollingEndpoint = "https://api.assemblyai.com/v2/transcript/" + transcript.getId();

        while (true) {
            HttpRequest pollingRequest = HttpRequest.newBuilder()
                    .uri(URI.create(pollingEndpoint))
                    .header("Authorization", API_KEY)
                    .GET()
                    .build();

            HttpResponse<String> pollingResponse = httpClient.send(pollingRequest, HttpResponse.BodyHandlers.ofString());
            transcript = new Gson().fromJson(pollingResponse.body(), Transcript.class);

            switch (transcript.getStatus()) {
                case "processing":
                	System.out.println("processing......");
                case "queued":
                    TimeUnit.SECONDS.sleep(3);
                    break;
                case "completed":
                	System.out.println(" ");
                    return transcript;
                case "error":
                    throw new RuntimeException("Transcription failed: " + transcript.getError());
                default:
                    throw new RuntimeException("This code shouldn't be reachable.");
            }
        }
    }

    public static class Transcript {
        private String id;
        private String status;
        private String text;
        private String error;

        public String getId() {
            return id;
        }

        public String getStatus() {
            return status;
        }

        public String getText() {
            return text;
        }

        public String getError() {
            return error;
        }
    }
}

