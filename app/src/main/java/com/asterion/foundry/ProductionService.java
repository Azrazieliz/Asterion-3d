package com.asterion.foundry;

import android.app.*;
import android.content.Intent;
import android.os.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONObject;

/** One foreground worker publishes atomic, independently recoverable stages. */
public final class ProductionService extends Service {
    static volatile boolean active;
    private Store store;
    private ExecutorService worker;
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean pauseRequested;
    private volatile String current;
    private int latestStart;
    static final String ORIGINAL_GLB = "Preserve original GLB";
    static final String VOLUME = "Image to 3D candidate";
    static final String DEPTH = "AI depth relief";
    static final String HULL = "Multi-view reconstruction";
    static final String REVISION = "Mesh revision";

    @Override public void onCreate() {
        super.onCreate();
        store = new Store(this);
        store.recover();
        worker = Executors.newSingleThreadExecutor();
        getSystemService(NotificationManager.class).createNotificationChannel(
                new NotificationChannel("production", "Asset production", NotificationManager.IMPORTANCE_LOW));
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        latestStart = startId;
        if (intent == null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if ("pause".equals(intent.getAction())) {
            pauseRequested = true;
            return START_NOT_STICKY;
        }
        if (active) return START_NOT_STICKY;
        active = true;
        pauseRequested = false;
        startForeground(7, new Notification.Builder(this, "production")
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setContentTitle("Asterion Foundry")
                .setContentText("Producing assets on this device")
                .setOngoing(true)
                .setContentIntent(PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class),
                        PendingIntent.FLAG_IMMUTABLE)).build());
        worker.submit(this::drain);
        return START_NOT_STICKY;
    }

    private void drain() {
        boolean held = false;
        PowerManager.WakeLock lock = getSystemService(PowerManager.class)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Foundry:production");
        try {
            lock.acquire(2 * 60 * 60 * 1000L);
            held = true;
            while (!pauseRequested) {
                JSONObject job = store.nextQueued();
                if (job == null) break;
                current = job.getString("id");
                try {
                    run(job);
                } catch (Paused pause) {
                    store.state(current, "paused", store.job(current).getInt("stage"), pause.getMessage());
                    store.record(current, new JSONObject(job.getString("request")).getString("kind"), 0, false,
                            new JSONObject().put("outcome", "paused").put("reason", pause.getMessage()));
                    pauseRequested = true;
                } catch (Exception | LinkageError failure) {
                    String message = failure.getClass().getSimpleName() + ": " + failure.getMessage();
                    store.state(current, "failed", store.job(current).getInt("stage"), message);
                    store.record(current, new JSONObject(job.getString("request")).getString("kind"), 0, false,
                            new JSONObject().put("outcome", "failed").put("reason", message));
                    android.util.Log.e("Foundry", "Asset job failed", failure);
                } finally {
                    current = null;
                }
            }
        } catch (Exception failure) {
            pauseRequested = true;
            android.util.Log.e("Foundry", "Queue failed", failure);
        } finally {
            if (held && lock.isHeld()) lock.release();
            main.post(() -> {
                if (!pauseRequested && store.hasQueued()) {
                    worker.submit(this::drain);
                    return;
                }
                active = false;
                stopForeground(STOP_FOREGROUND_REMOVE);
                stopSelf(latestStart);
            });
        }
    }

    static class Paused extends Exception {
        Paused(String reason) { super(reason); }
    }

    void check() throws Paused {
        if (pauseRequested) throw new Paused("Paused at durable checkpoint");
        if (getSystemService(PowerManager.class).getCurrentThermalStatus() >= PowerManager.THERMAL_STATUS_SEVERE) {
            throw new Paused("Paused to cool the phone");
        }
        ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
        getSystemService(ActivityManager.class).getMemoryInfo(memory);
        if (memory.lowMemory || memory.availMem < 350L * 1024 * 1024) {
            throw new Paused("Paused for low available memory");
        }
        if (store.root.getUsableSpace() < 256L * 1024 * 1024) {
            throw new Paused("Paused for low storage");
        }
    }

    private void stage(String id, int number, String message) throws Paused {
        check();
        store.state(id, "running", number, message);
    }

    private void run(JSONObject job) throws Exception {
        String id = job.getString("id");
        JSONObject request = new JSONObject(job.getString("request"));
        String kind = request.getString("kind");
        File directory = store.project(id);
        long start = SystemClock.elapsedRealtime();
        check();
        Map<String, File> originals = new SourceFiles(store.root).originals(request);
        if (ORIGINAL_GLB.equals(kind)) {
            preserveGlb(job, request, directory, originals.get("source"), start);
            return;
        }
        JSONObject sourceHashes = new JSONObject();
        StringBuilder signature = new StringBuilder(kind).append('|').append(Recipes.VERSION)
                .append('|').append(request.getDouble("scale")).append('|').append(request.getInt("detail"));
        for (Map.Entry<String, File> original : originals.entrySet()) {
            String hash = original.getValue().getName();
            signature.append('|').append(original.getKey()).append('=').append(hash);
            sourceHashes.put(original.getKey(), hash);
        }
        if (VOLUME.equals(kind)) signature.append("|").append(TripoModels.VERSION);
        if (DEPTH.equals(kind)) signature.append("|depth-relief-v1|").append(MidasDepth.SHA256);
        if (HULL.equals(kind)) signature.append('|').append(VisualHull.METHOD);
        if (REVISION.equals(kind)) {
            signature.append("|revision-v1|").append(request.getString("parent_mesh_sha256"))
                    .append('|').append(request.getJSONArray("axes")).append('|').append(request.opt("color"));
        }
        String key = FilesUtil.hash(signature.toString());
        FilesUtil.write(new File(directory, "project.json"), request.toString(2));
        File checkpoint = new File(directory, "checkpoint.mesh");
        File checkpointMetrics = new File(directory, "reference-metrics.json");
        File cache = new File(store.root, "cache/" + key + ".mesh");
        File cacheMetrics = new File(store.root, "cache/" + key + ".json");

        stage(id, job.getInt("stage"), "Preparing geometry");
        Candidate candidate = readCandidate(checkpoint, checkpointMetrics, kind);
        if (candidate == null) candidate = readCandidate(cache, cacheMetrics, kind);
        boolean reused = candidate != null;
        if (!reused) candidate = generate(request, originals, directory, key, id);
        FilesUtil.write(checkpointMetrics, candidate.metrics.toString(2));
        candidate.mesh.save(checkpoint);
        stage(id, 1, "Geometry checkpoint saved");
        if (!reused || !cache.isFile()) {
            FilesUtil.write(cacheMetrics, candidate.metrics.toString(2));
            candidate.mesh.save(cache);
        }
        if (request.optBoolean("pauseAfterGeometry") && job.getInt("stage") == 0) {
            throw new Paused("Acceptance checkpoint saved. Resume to finish export.");
        }

        stage(id, 2, "Auditing topology and writing GLB");
        GeometryAudit.Result audit = GeometryAudit.inspect(candidate.mesh, this::check);
        File asset = new File(directory, "asset.glb");
        Glb.write(candidate.mesh, asset);
        stage(id, 3, "Writing validation receipt");
        JSONObject receipt = new JSONObject(candidate.mesh.validate())
                .put("schema", "asterion.foundry.receipt.v1")
                .put("job", id).put("recipe", kind).put("recipe_version", Recipes.VERSION)
                .put("asset_sha256", FilesUtil.hash(asset))
                .put("editable_mesh_sha256", FilesUtil.hash(checkpoint))
                .put("source_sha256", sourceHashes.optString("source"))
                .put("source_roles", sourceHashes)
                .put("cache_key", key).put("reused", reused)
                .put("duration_ms", SystemClock.elapsedRealtime() - start)
                .put("thermal_status", getSystemService(PowerManager.class).getCurrentThermalStatus())
                .put("outcome", VOLUME.equals(kind) ? "candidate" : "complete")
                .put("backend", VOLUME.equals(kind) ? "ONNX Runtime ARM64 CPU" : "Android CPU / ART")
                .put("production_export_admitted", !VOLUME.equals(kind))
                .put("vulkan_compute", "not_implemented").put("qnn", "not_implemented")
                .put("unreal_import_executed", false).put("noworld_binding", "unbound")
                .put("visual_quality", "UNVALIDATED")
                .put("reference_metrics", candidate.metrics)
                .put("production_method", method(kind))
                .put("source_appearance_preserved", false)
                .put("mobile_optimization", "not_validated")
                .put("rig_validation", VOLUME.equals(kind) ? "unrigged" : candidate.mesh.bones.isEmpty()
                        ? "not_applicable" : "hierarchy_and_normalized_rigid_weights_only");
        if (REVISION.equals(kind)) {
            receipt.put("parent_mesh_sha256", request.getString("parent_mesh_sha256"));
        }
        if (audit != null) {
            receipt.put("topology", new JSONObject()
                    .put("boundary_edges", audit.boundaryEdges())
                    .put("nonmanifold_edges", audit.nonManifoldEdges())
                    .put("winding_conflicts", audit.windingConflicts())
                    .put("collapsed_triangles", audit.collapsedTriangles())
                    .put("weld_tolerance_metres", audit.toleranceMetres()));
        }
        FilesUtil.write(new File(directory, "receipt.json"), receipt.toString(2));
        FilesUtil.write(new File(directory, "import-manifest.json"), new JSONObject()
                .put("schema", "asterion.foundry.intermediate.v1")
                .put("format", "glTF2 GLB").put("units", "metres")
                .put("up_axis", "Y").put("handedness", "right")
                .put("asset", "asset.glb").put("sha256", receipt.getString("asset_sha256"))
                .put("identity_id", JSONObject.NULL).put("version_id", JSONObject.NULL)
                .put("manifestation_id", JSONObject.NULL).put("native_acceptance", false)
                .put("warnings", new JSONArray().put("NoWorld adapter has not admitted this asset"))
                .toString(2));
        if (VOLUME.equals(kind)) {
            store.finishCandidate(id, kind, SystemClock.elapsedRealtime() - start, reused, receipt);
        } else {
            store.finish(id, kind, SystemClock.elapsedRealtime() - start, reused, receipt);
        }
    }

    private void preserveGlb(JSONObject job, JSONObject request, File directory, File source, long start)
            throws Exception {
        String id = job.getString("id");
        if (source == null) throw new IOException("Choose a self-contained original GLB");
        stage(id, job.getInt("stage"), "Inspecting original GLB container");
        JSONObject inventory = OriginalGlb.inspect(source);
        FilesUtil.write(new File(directory, "project.json"), request.toString(2));
        File asset = new File(directory, "asset.glb");
        boolean reused = asset.isFile() && FilesUtil.hash(asset).equals(source.getName());
        if (!reused) {
            File temporary = new File(directory, "asset.glb.tmp");
            Files.copy(source.toPath(), temporary.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            if (!FilesUtil.hash(temporary).equals(source.getName())) {
                throw new IOException("Copied GLB failed its original checksum");
            }
            FilesUtil.move(temporary, asset);
        }
        stage(id, 1, "Original GLB checkpoint saved");
        if (request.optBoolean("pauseAfterGeometry") && job.getInt("stage") == 0) {
            throw new Paused("Original GLB checkpoint saved. Resume to publish the receipt.");
        }
        JSONObject receipt = new JSONObject().put("schema", "asterion.foundry.receipt.v1")
                .put("job", id).put("recipe", ORIGINAL_GLB).put("source_sha256", source.getName())
                .put("asset_sha256", source.getName()).put("outcome", "complete")
                .put("source_bytes_preserved", true).put("inventory", inventory)
                .put("reused", reused).put("duration_ms", SystemClock.elapsedRealtime() - start)
                .put("visual_quality", "UNVALIDATED").put("unreal_import_executed", false);
        FilesUtil.write(new File(directory, "receipt.json"), receipt.toString(2));
        FilesUtil.write(new File(directory, "import-manifest.json"), new JSONObject()
                .put("schema", "asterion.foundry.intermediate.v1")
                .put("asset", "asset.glb").put("sha256", source.getName())
                .put("native_acceptance", false).put("source_bytes_preserved", true).toString(2));
        store.finish(id, ORIGINAL_GLB, SystemClock.elapsedRealtime() - start, reused, receipt);
    }

    private record Candidate(Mesh mesh, JSONObject metrics) {}

    private static Candidate readCandidate(File mesh, File metrics, String kind) {
        if (!mesh.isFile() || (HULL.equals(kind) || DEPTH.equals(kind) || VOLUME.equals(kind)) && !metrics.isFile()) return null;
        try {
            JSONObject evidence = metrics.isFile()
                    ? new JSONObject(Files.readString(metrics.toPath())) : new JSONObject();
            return new Candidate(Mesh.load(mesh), evidence);
        } catch (Exception damaged) {
            return null;
        }
    }

    private Candidate generate(JSONObject request, Map<String, File> files, File directory, String key, String id) throws Exception {
        String kind = request.getString("kind");
        float scale = (float) request.getDouble("scale");
        int detail = request.getInt("detail");
        if (VOLUME.equals(kind)) {
            if (!TripoModels.ready(store.root)) throw new IOException("Install the pinned 3D model pack first");
            ActivityManager.MemoryInfo available = new ActivityManager.MemoryInfo();
            getSystemService(ActivityManager.class).getMemoryInfo(available);
            if (available.availMem < 3L * 1024 * 1024 * 1024) {
                throw new Paused("3D reconstruction requires at least 3 GiB available RAM");
            }
            File workspace = new File(directory, "volume-" + key);
            Files.createDirectories(workspace.toPath());
            SourceFiles.Raster input = SourceFiles.decode(files.get("source"), 1024);
            TriplaneMesh.Result result = TriplaneMesh.reconstruct(TripoModels.directory(store.root), workspace,
                    input.pixels(), input.width(), input.height(), detail, scale, this::check,
                    message -> store.state(id, "running", 0, message));
            return new Candidate(result.mesh(), new JSONObject()
                    .put("method", "learned_triplane_density")
                    .put("model_pack", TripoModels.VERSION).put("grid_resolution", result.cells())
                    .put("generation_ms", result.millis()).put("inferred_occluded_geometry", true)
                    .put("triplane_checkpoint_reused", result.reusedPlanes())
                    .put("density_checkpoint_reused", result.reusedField())
                    .put("reference_fidelity", "unverified").put("independent_character_parts", false));
        }
        if (DEPTH.equals(kind)) {
            SourceFiles.Raster input = SourceFiles.decode(files.get("source"), 384);
            new VisualHull.View(input.pixels(), input.width(), input.height());
            check();
            MidasDepth.Estimate estimate = MidasDepth.predict(this, input);
            Mesh mesh = DepthRelief.build(input.pixels(), input.width(), input.height(),
                    estimate.inverseDepth(), MidasDepth.SIZE, scale, this::check);
            return new Candidate(mesh, new JSONObject()
                    .put("method", "relative_inverse_depth")
                    .put("model", MidasDepth.MODEL).put("model_sha256", MidasDepth.SHA256)
                    .put("inference_and_setup_ms", estimate.elapsedMillis())
                    .put("threads", estimate.threads()).put("back_surface", "flat_backing")
                    .put("metric_depth", false));
        }
        if (HULL.equals(kind)) {
            VisualHull.Result result = VisualHull.reconstruct(SourceFiles.view(files.get("front")),
                    SourceFiles.view(files.get("right")),
                    files.containsKey("back") ? SourceFiles.view(files.get("back")) : null,
                    scale, detail, this::check);
            JSONObject metrics = new JSONObject().put("method", "binary_silhouette_iou")
                    .put("front", result.frontIou()).put("right", result.rightIou())
                    .put("back", result.backIou() == null ? JSONObject.NULL : result.backIou())
                    .put("height_cells", result.resolution())
                    .put("occupied_cells", result.occupied()).put("components_6", result.components());
            return new Candidate(result.mesh(), metrics);
        }
        if (REVISION.equals(kind)) {
            String parent = request.getString("parent");
            if (!"complete".equals(store.job(parent).getString("state"))) {
                throw new IOException("Revision parent is not a completed project");
            }
            File mesh = new File(store.project(parent), "checkpoint.mesh");
            if (!FilesUtil.hash(mesh).equals(request.getString("parent_mesh_sha256"))) {
                throw new IOException("Revision parent checksum changed");
            }
            JSONArray axes = request.getJSONArray("axes");
            return new Candidate(MeshRevision.apply(Mesh.load(mesh), (float) axes.getDouble(0),
                    (float) axes.getDouble(1), (float) axes.getDouble(2),
                    request.has("color") ? request.getInt("color") : null), new JSONObject());
        }
        if ("Image silhouette".equals(kind)) {
            SourceFiles.Raster image = SourceFiles.decode(files.get("source"), 96);
            return new Candidate(Recipes.silhouette(image.pixels(), image.width(), image.height(),
                    scale, scale * .08f), new JSONObject());
        }
        if ("OBJ geometry".equals(kind)) {
            File source = files.get("source");
            if (source == null) throw new IOException("OBJ original is missing");
            return new Candidate(Glb.readObj(source), new JSONObject());
        }
        return new Candidate(Recipes.create(kind, scale, detail), new JSONObject());
    }

    private static String method(String kind) {
        return switch (kind) {
            case HULL -> VisualHull.METHOD;
            case VOLUME -> "triposr_volume_surface_vertex_colour";
            case DEPTH -> "midas_relative_depth_relief_v1";
            case REVISION -> "non_destructive_mesh_revision";
            case "Image silhouette" -> "deterministic_2_5d_extrusion";
            case "OBJ geometry" -> "triangulated_OBJ_geometry_only";
            default -> "parametric_recipe";
        };
    }

    @Override public void onTimeout(int startId, int type) {
        pauseRequested = true;
        if (current != null) {
            try {
                String id = current;
                store.state(id, "paused", store.job(id).getInt("stage"), "Android service time limit");
            } catch (Exception error) {
                android.util.Log.e("Foundry", "Could not record service timeout", error);
            }
        }
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onDestroy() {
        pauseRequested = true;
        worker.shutdown();
        super.onDestroy();
    }
}
