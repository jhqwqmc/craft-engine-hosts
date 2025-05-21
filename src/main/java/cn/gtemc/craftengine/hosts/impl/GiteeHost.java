package cn.gtemc.craftengine.hosts.impl;

import cn.gtemc.craftengine.CraftengineHosts;
import cn.gtemc.craftengine.hosts.ResourcePackHosts;
import cn.gtemc.craftengine.util.GsonHelper;
import cn.gtemc.craftengine.util.HashUtils;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import net.momirealms.craftengine.core.pack.host.ResourcePackDownloadData;
import net.momirealms.craftengine.core.pack.host.ResourcePackHost;
import net.momirealms.craftengine.core.pack.host.ResourcePackHostFactory;
import net.momirealms.craftengine.core.util.Key;
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

public class GiteeHost implements ResourcePackHost {
    public static final Factory FACTORY = new Factory();
    private static final String GITEE_API = "https://gitee.com/api/v5";
    private final String owner;
    private final String repo;
    private final String token;
    private final String uploadPath;
    private final HttpClient httpClient;
    private String cachedSha1;
    private String downloadUrl;

    public GiteeHost(String owner, String repo, String token, String uploadPath, ProxySelector proxy) {
        this.owner = owner;
        this.repo = repo;
        this.token = token;
        this.uploadPath = uploadPath;
        this.httpClient = HttpClient.newBuilder().proxy(proxy).build();
        this.readCacheFromDisk();
    }

    @Override
    public CompletableFuture<List<ResourcePackDownloadData>> requestResourcePackDownloadLink(UUID player) {
        return CompletableFuture.completedFuture(List.of(ResourcePackDownloadData.of(
                this.downloadUrl, UUID.nameUUIDFromBytes(this.cachedSha1.getBytes(StandardCharsets.UTF_8)), this.cachedSha1
        )));
    }

    @Override
    public CompletableFuture<Void> upload(Path resourcePackPath) {
        return CompletableFuture.runAsync(() -> {
            try {
                long uploadStart = System.currentTimeMillis();
                CraftengineHosts.instance().getLogger().info("[Gitee] Uploading resource pack...");
                this.cachedSha1 = HashUtils.calculateLocalFileSha1(resourcePackPath);
                this.saveCacheToDisk();

                String sha = null;
                String checkUrl = String.format("%s/repos/%s/%s/contents/%s",
                        GITEE_API, owner, repo, uploadPath);

                HttpRequest checkRequest = HttpRequest.newBuilder()
                        .uri(URI.create(checkUrl))
                        .header("Authorization", "token " + token)
                        .GET()
                        .build();

                HttpResponse<String> checkResponse = httpClient.send(checkRequest, HttpResponse.BodyHandlers.ofString());

                if (checkResponse.statusCode() == 200 || checkResponse.statusCode() == 201) {
                    JsonObject existingFile = JsonParser.parseString(checkResponse.body()).getAsJsonObject();
                    sha = existingFile.get("sha").getAsString();
                }

                byte[] fileContent = Files.readAllBytes(resourcePackPath);
                String contentBase64 = Base64.getEncoder().encodeToString(fileContent);

                String uploadUrl = String.format("%s/repos/%s/%s/contents/%s",
                        GITEE_API, owner, repo, uploadPath);

                JsonObject uploadBody = new JsonObject();
                uploadBody.addProperty("message", "Upload resource pack");
                uploadBody.addProperty("content", contentBase64);
                if (sha != null) {
                    uploadBody.addProperty("sha", sha);
                }

                HttpRequest uploadRequest = HttpRequest.newBuilder()
                        .uri(URI.create(uploadUrl))
                        .header("Authorization", "token " + token)
                        .header("Content-Type", "application/json")
                        .PUT(HttpRequest.BodyPublishers.ofString(uploadBody.toString()))
                        .build();

                HttpResponse<String> uploadResponse = httpClient.send(uploadRequest, HttpResponse.BodyHandlers.ofString());

                if (uploadResponse.statusCode() == 200 || uploadResponse.statusCode() == 201) {
                    JsonObject responseJson = JsonParser.parseString(uploadResponse.body()).getAsJsonObject();
                    this.downloadUrl = responseJson.getAsJsonObject("content").get("download_url").getAsString();
                    saveCacheToDisk();

                    long uploadTime = System.currentTimeMillis() - uploadStart;
                    CraftengineHosts.instance().getLogger().info(String.format(
                            "[Gitee] Upload request completed in %sms", uploadTime
                    ));
                } else {
                    CraftengineHosts.instance().getLogger().warning("[Gitee] Upload failed with status " +
                            uploadResponse.statusCode() + ": " + uploadResponse.body());
                    throw new RuntimeException("Upload failed with status " + uploadResponse.statusCode());
                }
            } catch (IOException | InterruptedException e) {
                CraftengineHosts.instance().getLogger().warning("[Gitee] Error during upload: " + e.getMessage());
                throw new RuntimeException(e);
            }
        });
    }

    private void readCacheFromDisk() {
        Path cachePath = CraftengineHosts.instance().dataFolderPath().resolve("gitee.cache");
        if (!Files.exists(cachePath)) return;

        try (InputStream is = Files.newInputStream(cachePath)) {
            Map<String, String> cache = GsonHelper.get().fromJson(
                    new InputStreamReader(is),
                    new TypeToken<Map<String, String>>(){}.getType()
            );

            this.cachedSha1 = cache.get("sha1");
            this.downloadUrl = cache.get("download_url");
        } catch (Exception e) {
            CraftengineHosts.instance().getLogger().warning(
                    "[Gitee] Failed to load cache from disk: " + e.getMessage());
        }
    }

    private void saveCacheToDisk() {
        Map<String, String> cache = new HashMap<>();
        cache.put("sha1", this.cachedSha1 != null ? this.cachedSha1 : "");
        cache.put("download_url", this.downloadUrl != null ? this.downloadUrl : "");

        Path cachePath = CraftengineHosts.instance().dataFolderPath().resolve("gitee.cache");
        try {
            Files.writeString(
                    cachePath,
                    GsonHelper.get().toJson(cache),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
        } catch (IOException e) {
            CraftengineHosts.instance().getLogger().warning(
                    "[Gitee] Failed to persist cache to disk: " + e.getMessage());
        }
    }

    @Override
    public boolean canUpload() {
        return true;
    }

    @Override
    public Key type() {
        return ResourcePackHosts.GITEE;
    }

    public static class Factory implements ResourcePackHostFactory {
        @Override
        public ResourcePackHost create(Map<String, Object> arguments) {
            boolean useEnv = (boolean) arguments.getOrDefault("use-environment-variables", false);
            String owner = arguments.get("owner").toString();
            if (owner == null || owner.isEmpty()) {
                throw new IllegalArgumentException("[Gitee] Missing required 'owner' argument for gitee host.");
            }
            String repo = arguments.get("repo").toString();
            if (repo == null || repo.isEmpty()) {
                throw new IllegalArgumentException("[Gitee] Missing required 'repo' argument for gitee host.");
            }
            String token = useEnv ? System.getenv("CE_GITEE_TOKEN") : arguments.get("token").toString();
            if (token == null || token.isEmpty()) {
                throw new IllegalArgumentException("[Gitee] Missing required 'token' argument for gitee host.");
            }
            String uploadPath = arguments.get("path").toString();
            if (uploadPath == null || uploadPath.isEmpty()) {
                throw new IllegalArgumentException("[Gitee] Missing required 'path' argument for gitee host.");
            }
            ProxySelector proxy = getProxySelector(MiscUtils.castToMap(arguments.get("proxy"), true));
            return new GiteeHost(owner, repo, token, uploadPath, proxy);
        }
    }
}