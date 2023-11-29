import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class Main {
    HttpClient client;

    String url;

    Queue<RequestData> asyncQueue = new ConcurrentLinkedQueue<>();

    public static class RequestData {
        LocalTime time;
        int resultCode;

        public RequestData(LocalTime time, int resultCode) {
            this.time = time;
            this.resultCode = resultCode;
        }
    }
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
        Map<Integer, List<RequestData>> RPM = asyncQueue.stream().collect(Collectors.groupingBy(x -> x.time.get(ChronoField.MINUTE_OF_DAY)));

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
        try {
            BufferedWriter csvTimestamps = new BufferedWriter(new FileWriter("./timetamps.csv"));
            for (var reqResult: asyncQueue) {
                csvTimestamps.write(reqResult.time.format(formatter) + "," + reqResult.resultCode + "\n");
            };
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        try {
            BufferedWriter rpmWriter = new BufferedWriter(new FileWriter("./rpm.csv"));
            for (var entry : RPM.entrySet()) {
                rpmWriter.write(entry.getKey() + "," + entry.getValue().size());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void sendRequest() {
        HttpRequest req = HttpRequest.newBuilder().GET().uri(URI.create(this.url)).build();
        try {
            int status = client.send(req, HttpResponse.BodyHandlers.ofString()).statusCode();
            asyncQueue.add(new RequestData(LocalTime.now(), status));
        } catch (IOException e) {
            System.out.println("Received an error :" + e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
