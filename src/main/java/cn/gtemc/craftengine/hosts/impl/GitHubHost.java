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

public class GitHubHost implements ResourcePackHost {
    public static final Factory FACTORY = new Factory();
    private static final String GITHUB_API = "https://api.github.com";
    private final String owner;
    private final String repo;
    private final String token;
    private final String branch;
    private final String uploadPath;
    private final HttpClient httpClient;
    private String cachedSha1;
    private String downloadUrl;

    public GitHubHost(String owner, String repo, String token, String branch, String uploadPath, ProxySelector proxy) {
        this.owner = owner;
        this.repo = repo;
        this.token = token;
        this.branch = branch;
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
                CraftengineHosts.instance().getLogger().info("[GitHub] Uploading resource pack...");
                this.cachedSha1 = HashUtils.calculateLocalFileSha1(resourcePackPath);
                this.saveCacheToDisk();

                String sha = null;
                String checkUrl = String.format("%s/repos/%s/%s/contents/%s?ref=%s",
                        GITHUB_API, owner, repo, uploadPath, branch);

                HttpRequest checkRequest = HttpRequest.newBuilder()
                        .uri(URI.create(checkUrl))
                        .header("Authorization", "token " + token)
                        .header("Accept", "application/vnd.github.v3+json")
                        .GET()
                        .build();

                HttpResponse<String> checkResponse = httpClient.send(checkRequest, HttpResponse.BodyHandlers.ofString());

                if (checkResponse.statusCode() == 200) {
                    JsonObject existingFile = JsonParser.parseString(checkResponse.body()).getAsJsonObject();
                    sha = existingFile.get("sha").getAsString();
                }

                byte[] fileContent = Files.readAllBytes(resourcePackPath);
                String contentBase64 = Base64.getEncoder().encodeToString(fileContent);

                String uploadUrl = String.format("%s/repos/%s/%s/contents/%s",
                        GITHUB_API, owner, repo, uploadPath);

                JsonObject uploadBody = new JsonObject();
                uploadBody.addProperty("message", "Upload resource pack");
                uploadBody.addProperty("content", contentBase64);
                uploadBody.addProperty("branch", branch);
                if (sha != null) {
                    uploadBody.addProperty("sha", sha);
                }

                HttpRequest uploadRequest = HttpRequest.newBuilder()
                        .uri(URI.create(uploadUrl))
                        .header("Authorization", "token " + token)
                        .header("Accept", "application/vnd.github.v3+json")
                        .header("Content-Type", "application/json")
                        .PUT(HttpRequest.BodyPublishers.ofString(uploadBody.toString()))
                        .build();

                HttpResponse<String> uploadResponse = httpClient.send(uploadRequest, HttpResponse.BodyHandlers.ofString());

                if (uploadResponse.statusCode() == 200 || uploadResponse.statusCode() == 201) {
                    JsonObject responseJson = JsonParser.parseString(uploadResponse.body()).getAsJsonObject();
                    this.downloadUrl = responseJson.get("content").getAsJsonObject().get("download_url").getAsString();
                    saveCacheToDisk();

                    long uploadTime = System.currentTimeMillis() - uploadStart;
                    CraftengineHosts.instance().getLogger().info(String.format(
                            "[GitHub] Upload request completed in %sms", uploadTime
                    ));
                } else {
                    CraftengineHosts.instance().getLogger().warning("[GitHub] Upload failed with status " +
                            uploadResponse.statusCode() + ": " + uploadResponse.body());
                    throw new RuntimeException("Upload failed with status " + uploadResponse.statusCode());
                }
            } catch (IOException | InterruptedException e) {
                CraftengineHosts.instance().getLogger().warning("[GitHub] Error during upload: " + e.getMessage());
                throw new RuntimeException(e);
            }
        });
    }

    private void readCacheFromDisk() {
        Path cachePath = CraftengineHosts.instance().dataFolderPath().resolve("cache").resolve("github.json");
        if (!Files.exists(cachePath) || !Files.isRegularFile(cachePath)) return;

        try (InputStream is = Files.newInputStream(cachePath)) {
            Map<String, String> cache = GsonHelper.get().fromJson(
                    new InputStreamReader(is),
                    new TypeToken<Map<String, String>>(){}.getType()
            );

            this.cachedSha1 = cache.get("sha1");
            this.downloadUrl = cache.get("download_url");
        } catch (Exception e) {
            CraftengineHosts.instance().getLogger().warning(
                    "[GitHub] Failed to load cache from disk: " + e.getMessage());
        }
    }

    private void saveCacheToDisk() {
        Map<String, String> cache = new HashMap<>();
        cache.put("sha1", this.cachedSha1 != null ? this.cachedSha1 : "");
        cache.put("download_url", this.downloadUrl != null ? this.downloadUrl : "");
        Path cachePath = CraftengineHosts.instance().dataFolderPath().resolve("cache").resolve("github.json");
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
                    "[GitHub] Failed to persist cache to disk: " + e.getMessage());
        }
    }

    @Override
    public boolean canUpload() {
        return true;
    }

    @Override
    public Key type() {
        return ResourcePackHosts.GITHUB;
    }

    public static class Factory implements ResourcePackHostFactory {
        @Override
        public ResourcePackHost create(Map<String, Object> arguments) {
            boolean useEnv = (boolean) arguments.getOrDefault("use-environment-variables", false);
            String owner = arguments.get("owner").toString();
            if (owner == null || owner.isEmpty()) {
                throw new IllegalArgumentException("[GitHub] Missing required 'owner' argument for github host.");
            }
            String repo = arguments.get("repo").toString();
            if (repo == null || repo.isEmpty()) {
                throw new IllegalArgumentException("[GitHub] Missing required 'repo' argument for github host.");
            }
            String token = useEnv ? System.getenv("CE_GITHUB_TOKEN") : arguments.get("token").toString();
            if (token == null || token.isEmpty()) {
                throw new IllegalArgumentException("[GitHub] Missing required 'token' argument for github host.");
            }
            String branch = arguments.getOrDefault("branch", "main").toString();
            if (branch == null || branch.isEmpty()) {
                throw new IllegalArgumentException("[GitHub] Missing required 'branch' argument for github host.");
            }
            String uploadPath = arguments.get("path").toString();
            if (uploadPath == null || uploadPath.isEmpty()) {
                throw new IllegalArgumentException("[GitHub] Missing required 'path' argument for github host.");
            }
            ProxySelector proxy = getProxySelector(MiscUtils.castToMap(arguments.get("proxy"), true));
            return new GitHubHost(owner, repo, token, branch, uploadPath, proxy);
        }
    }
}