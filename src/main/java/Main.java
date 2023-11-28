import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Main {
    HttpClient client;

    String url;

    public static int[] RPS = {20, 40, 60, 100, 150, 300, 500, 1000};

    public Main(HttpClient client, String url) {
        this.client = client;
        this.url = url;
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Please provide a url as a program argument");
        }
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()) {
            new Main(client, args[0]).run();
        }
    }

    public void run() {
        try {
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                for (int r : RPS) {
                    for (int i = 0; i < 60; i++) {
                        long delay = 1000 / r;
                        for (int j = 0; j < r; j++) {
                            Thread.sleep(delay);
                            executor.submit(this::sendRequest);
                        }
                    }
                }
                executor.awaitTermination(10, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
        }
    }

    public void sendRequest() {
        HttpRequest req = HttpRequest.newBuilder().GET().uri(URI.create(this.url)).build();
        try {
            client.send(req, HttpResponse.BodyHandlers.ofString()).body();
        } catch (IOException e) {
            System.out.println("Received an error :" + e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
