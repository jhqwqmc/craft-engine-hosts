package cn.gtemc.craftengine.hosts.impl;

import cn.gtemc.craftengine.CraftengineHosts;
import cn.gtemc.craftengine.hosts.ResourcePackHosts;
import cn.gtemc.craftengine.util.GsonHelper;
import cn.gtemc.craftengine.util.HashUtils;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import net.momirealms.craftengine.core.pack.host.ResourcePackDownloadData;
import net.momirealms.craftengine.core.pack.host.ResourcePackHost;
import net.momirealms.craftengine.core.pack.host.ResourcePackHostFactory;
import net.momirealms.craftengine.core.pack.host.ResourcePackHostType;
import net.momirealms.craftengine.core.util.MiscUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class PolymathHost implements ResourcePackHost {
    public static final ResourcePackHostFactory<PolymathHost> FACTORY = new Factory();
    private final String serverUrl;
    private final String secret;
    private final HttpClient httpClient;
    private String sha1;
    private String url;
    private UUID uuid;

    public PolymathHost(String serverUrl, String secret, ProxySelector proxy) {
        this.serverUrl = serverUrl;
        this.secret = secret;
        this.httpClient = HttpClient.newBuilder().proxy(proxy).build();
        this.readCacheFromDisk();
    }

    private void readCacheFromDisk() {
        Path cachePath = CraftengineHosts.instance().dataFolderPath().resolve("cache").resolve("polymath.json");
        if (!Files.exists(cachePath) || !Files.isRegularFile(cachePath)) return;

        try (InputStream is = Files.newInputStream(cachePath)) {
            Map<String, String> cache = GsonHelper.get().fromJson(
                    new InputStreamReader(is),
                    new TypeToken<Map<String, String>>(){}.getType()
            );

            this.sha1 = cache.get("sha1");
            this.uuid = generateUUID(this.sha1);
            this.url = cache.get("url");
        } catch (Exception e) {
            CraftengineHosts.instance().getLogger().warning(
                    "[Polymath] Failed to load cache from disk: " + e.getMessage());
        }
    }

    private void saveCacheToDisk() {
        Map<String, String> cache = new HashMap<>();
        cache.put("sha1", this.sha1 != null ? this.sha1 : "");
        cache.put("url", this.url != null ? this.url : "");
        Path cachePath = CraftengineHosts.instance().dataFolderPath().resolve("cache").resolve("polymath.json");
        try {
            Files.createDirectories(cachePath.getParent());
            Files.writeString(
                    cachePath,
                    GsonHelper.get().toJson(cache),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
        } catch (IOException e) {
            CraftengineHosts.instance().getLogger().warning(
                    "[Polymath] Failed to persist cache to disk: " + e.getMessage());
        }
    }

    @Override
    public CompletableFuture<List<ResourcePackDownloadData>> requestResourcePackDownloadLink(UUID player) {
        return CompletableFuture.completedFuture(List.of(ResourcePackDownloadData.of(
                this.url, this.uuid, this.sha1
        )));
    }

    @Override
    public CompletableFuture<Void> upload(Path resourcePackPath) {
        return CompletableFuture.runAsync(() -> {
            long uploadStart = System.currentTimeMillis();
            CraftengineHosts.instance().getLogger().info("[Polymath] Uploading resource pack...");
            try {
                this.sha1 = HashUtils.calculateLocalFileSha1(resourcePackPath);
                this.uuid = generateUUID(this.sha1);
                this.saveCacheToDisk();
                String boundary = UUID.randomUUID().toString();

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(this.serverUrl))
                        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                        .POST(buildMultipartBody(resourcePackPath, boundary))
                        .build();

                HttpResponse<String> response = this.httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                JsonObject responseJson = JsonParser.parseString(response.body()).getAsJsonObject();
                this.url = responseJson.get("url").getAsString();
                this.saveCacheToDisk();
                long uploadTime = System.currentTimeMillis() - uploadStart;
                CraftengineHosts.instance().getLogger().info(String.format(
                        "[Polymath] Upload request completed in %sms", uploadTime
                ));
            } catch (IOException | JsonSyntaxException  | InterruptedException e) {
                CraftengineHosts.instance().getLogger().warning("[Polymath] Error during upload: " + e.getMessage());
                throw new RuntimeException(e);
            }
        });
    }

    private HttpRequest.BodyPublisher buildMultipartBody(Path filePath, String boundary) throws IOException {
        List<byte[]> parts = new ArrayList<>();

        String idPart = "--" + boundary + "\r\n" +
                "Content-Disposition: form-data; name=\"id\"\r\n" +
                "Content-Type: text/plain; charset=UTF-8\r\n\r\n" +
                this.secret + "\r\n";
        parts.add(idPart.getBytes(StandardCharsets.UTF_8));

        String filePart = "--" + boundary + "\r\n" +
                "Content-Disposition: form-data; name=\"pack\"; filename=\"" + filePath.getFileName() + "\"\r\n" +
                "Content-Type: application/octet-stream\r\n\r\n";
        parts.add(filePart.getBytes(StandardCharsets.UTF_8));

        parts.add(Files.readAllBytes(filePath));

        String endBoundary = "\r\n--" + boundary + "--\r\n";
        parts.add(endBoundary.getBytes(StandardCharsets.UTF_8));

        return HttpRequest.BodyPublishers.ofByteArrays(parts);
    }

    private UUID generateUUID(String sha1) {
        if (sha1 == null || sha1.isEmpty()) {
            return null;
        }
        return UUID.nameUUIDFromBytes(sha1.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public boolean canUpload() {
        return true;
    }

    @Override
    public ResourcePackHostType<PolymathHost> type() {
        return ResourcePackHosts.POLYMATH;
    }

    private static class Factory implements ResourcePackHostFactory<PolymathHost> {
        @Override
        public PolymathHost create(Map<String, Object> arguments) {
            boolean useEnv = (boolean) arguments.getOrDefault("use-environment-variables", false);
            String serverUrl = Optional.ofNullable(arguments.get("server-url")).map(String::valueOf).orElse(null);
            if (serverUrl == null || serverUrl.isEmpty()) {
                throw new IllegalArgumentException("[Polymath] Missing required 'server-url' argument for polymath host.");
            }
            String secret = useEnv ? System.getenv("CE_POLYMATH_SECRET") : Optional.ofNullable(arguments.get("secret")).map(String::valueOf).orElse(null);
            if (secret == null || secret.isEmpty()) {
                throw new IllegalArgumentException("[Polymath] Missing required 'secret' argument for polymath host.");
            }
            ProxySelector proxy = getProxySelector(MiscUtils.castToMap(arguments.get("proxy"), true));
            return new PolymathHost(serverUrl, secret, proxy);
        }
    }
}
