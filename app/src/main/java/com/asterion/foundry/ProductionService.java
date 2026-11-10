package com.asterion.foundry;

import android.app.*;
import android.content.Intent;
import android.graphics.Bitmap;
import java.io.ByteArrayOutputStream;
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
    static final String NOWORLD_GLB = "Check NoWorld GLB handoff";
    static final String MATERIAL_VARIANT = "Original GLB material variant";
    static final String VOLUME = "Image to 3D candidate";
    static final String DEPTH = "AI depth relief";
    static final String HULL = "Multi-view reconstruction";
    static final String REVISION = "Mesh revision";
    static final String FORM = "Saved form variant";
    static final String MOTION = "Skeletal animation";
    static final String SKIN_BLEND = "Blend skin weights";

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
        if (ORIGINAL_GLB.equals(kind) || NOWORLD_GLB.equals(kind)) {
            preserveGlb(job, request, directory, originals, start);
            return;
        }
        if (MATERIAL_VARIANT.equals(kind)) {
            materialVariant(job,request,directory,originals.get("source"),start);
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
        String volumeWorkspace = null;
        if (VOLUME.equals(kind)) {
            signature.append('|').append(TripoModels.VERSION);
            volumeWorkspace = FilesUtil.hash(signature.toString());
            signature.append('|').append(TriplaneMesh.PIPELINE_VERSION);
        }
        if (DEPTH.equals(kind)) signature.append("|depth-relief-v1|").append(MidasDepth.SHA256);
        if (HULL.equals(kind)) signature.append('|').append(VisualHull.METHOD);
        if (isRigTemplate(kind)) signature.append('|').append(SkinBlend.VERSION);
        Base base = REVISION.equals(kind) || FORM.equals(kind) || MOTION.equals(kind) || SKIN_BLEND.equals(kind) ? base(request) : null;
        if (base != null) {
            signature.append("|derived-v2|").append(request.getString("parent_mesh_sha256"))
                    .append('|').append(base.textureSha());
            switch (kind) {
                case FORM -> signature.append('|').append(request.getJSONObject("form"));
                case MOTION -> signature.append('|').append(SkeletalMotion.VERSION).append('|')
                        .append(request.optString("motion_script", request.optString("motion_intent")));
                case SKIN_BLEND -> signature.append('|').append(SkinBlend.VERSION);
                default -> signature.append('|').append(request.getJSONArray("axes")).append('|').append(request.opt("color"));
            }
        }
        String key = FilesUtil.hash(signature.toString());
        FilesUtil.write(new File(directory, "project.json"), request.toString(2));
        File checkpoint = new File(directory, "checkpoint.mesh");
        File checkpointMetrics = new File(directory, "reference-metrics.json");
        File cache = new File(store.root, "cache/" + key + ".mesh");
        File cacheMetrics = new File(store.root, "cache/" + key + ".json");
        File texture = new File(directory, "base-color.png");
        File cacheTexture = new File(store.root, "cache/" + key + ".png");

        stage(id, job.getInt("stage"), "Preparing geometry");
        Candidate candidate = readCandidate(checkpoint, checkpointMetrics, texture, kind);
        if (candidate == null) candidate = readCandidate(cache, cacheMetrics, cacheTexture, kind);
        boolean reused = candidate != null;
        if (!reused) candidate = generate(request, originals, directory, volumeWorkspace, id, base);
        if (candidate.texture != null) {
            FilesUtil.write(texture, candidate.texture);
            candidate.metrics.put("texture_sha256", FilesUtil.hash(texture));
        }
        FilesUtil.write(checkpointMetrics, candidate.metrics.toString(2));
        candidate.mesh.save(checkpoint);
        stage(id, 1, "Geometry checkpoint saved");
        if (!reused || !cache.isFile()) {
            if (candidate.texture != null) FilesUtil.write(cacheTexture, candidate.texture);
            FilesUtil.write(cacheMetrics, candidate.metrics.toString(2));
            candidate.mesh.save(cache);
        }
        if (request.optBoolean("pauseAfterGeometry") && job.getInt("stage") == 0) {
            throw new Paused("Acceptance checkpoint saved. Resume to finish export.");
        }

        stage(id, 2, "Auditing topology and writing GLB");
        GeometryAudit.Result audit = GeometryAudit.inspect(candidate.mesh, this::check);
        File asset = new File(directory, "asset.glb");
        JSONObject motionEvidence = null;
        if (MOTION.equals(kind)) {
            String script = request.has("motion_script") ? request.getString("motion_script")
                    : SkeletalMotion.compose(candidate.mesh, request.getString("motion_intent"));
            SkeletalMotion clip = SkeletalMotion.parse(candidate.mesh, script);
            SkeletalMotion.Validation validation = Glb.write(candidate.mesh, asset, candidate.texture, clip);
            File editableMotion = new File(directory,"motion.foundry.txt");
            FilesUtil.write(editableMotion,script);
            motionEvidence = new JSONObject(validation.json()).put("editable_script_sha256",FilesUtil.hash(editableMotion))
                    .put("method",request.has("motion_script") ? "authored_local_joint_tracks" : "deterministic_motion_composition")
                    .put("visual_playback_validation", "not_performed");
        } else {
            Glb.write(candidate.mesh, asset, candidate.texture);
        }
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
                        ? "not_applicable" : candidate.mesh.blended() ? "hierarchy_and_normalized_blend_weights"
                        : "hierarchy_and_normalized_rigid_weights_only");
        if (candidate.texture != null) receipt.put("texture_sha256", candidate.metrics.getString("texture_sha256"));
        if (motionEvidence != null) receipt.put("motion",motionEvidence);
        if (VOLUME.equals(kind) && request.optBoolean("human_reference")) {
            stage(id, 3, "Checking source anatomy evidence");
            receipt.put("source_pose_evidence",sourcePoseEvidence(originals.get("source")));
        }
        if (base != null) {
            receipt.put("parent_mesh_sha256", request.getString("parent_mesh_sha256"));
        }
        if (FORM.equals(kind)) {
            receipt.put("form",request.getJSONObject("form"))
                    .put("form_generation", "deterministic_proportions_and_material_tint")
                    .put("canonical_form_admitted", false);
        }
        if (audit != null) {
            receipt.put("topology", new JSONObject()
                    .put("boundary_edges", audit.boundaryEdges())
                    .put("nonmanifold_edges", audit.nonManifoldEdges())
                    .put("winding_conflicts", audit.windingConflicts())
                    .put("collapsed_triangles", audit.collapsedTriangles())
                    .put("weld_tolerance_metres", audit.toleranceMetres()));
        } else {
            receipt.put("topology", new JSONObject().put("status", "skipped_over_vertex_budget"));
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

    private void preserveGlb(JSONObject job, JSONObject request, File directory, Map<String, File> originals,
                             long start) throws Exception {
        String id = job.getString("id");
        String kind = request.getString("kind");
        File source = originals.get("source");
        File reference = originals.get("reference");
        if (source == null) throw new IOException("Choose a self-contained original GLB");
        if (reference != null) {
            File matched = new ReferenceRegistry(store.root).find(reference.getName());
            if (!source.equals(matched)) throw new IOException("Reference does not match the registered original");
        }
        stage(id, job.getInt("stage"), "Inspecting original GLB container");
        JSONObject inventory = OriginalGlb.inspect(source);
        JSONObject handoff = null;
        if (NOWORLD_GLB.equals(kind)) {
            File profile = originals.get("contract");
            if (profile == null) throw new IOException("Choose the NoWorld GLB handoff profile");
            stage(id, job.getInt("stage"), "Checking the declared NoWorld source profile");
            handoff = NoWorldHandoff.validate(source,profile);
            if (!profile.getName().equals(handoff.getString("profile_sha256"))
                    || !source.getName().equals(handoff.getString("source_sha256"))) {
                throw new IOException("NoWorld inputs changed during validation");
            }
        }
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
                .put("job", id).put("recipe", kind).put("source_sha256", source.getName())
                .put("asset_sha256", source.getName()).put("outcome", "complete")
                .put("source_bytes_preserved", true).put("inventory", inventory)
                .put("reused", reused).put("duration_ms", SystemClock.elapsedRealtime() - start)
                .put("visual_quality", "UNVALIDATED").put("unreal_import_executed", false);
        if (reference != null) {
            receipt.put("reference_sha256", reference.getName())
                    .put("reference_match", "exact_reference_bytes_to_registered_original")
                    .put("association_provenance", "user_registered")
                    .put("geometry_generated", false);
        }
        if (handoff != null) receipt.put("noworld_source_profile",handoff);
        FilesUtil.write(new File(directory, "receipt.json"), receipt.toString(2));
        FilesUtil.write(new File(directory, "import-manifest.json"), new JSONObject()
                .put("schema", "asterion.foundry.intermediate.v1")
                .put("asset", "asset.glb").put("sha256", source.getName())
                .put("native_acceptance", false).put("source_bytes_preserved", true).toString(2));
        store.finish(id, kind, SystemClock.elapsedRealtime() - start, reused, receipt);
    }

    private void materialVariant(JSONObject job, JSONObject request, File directory, File source, long start)
            throws Exception {
        String id = job.getString("id");
        if (source == null) throw new IOException("Material variant requires an original self-contained GLB");
        String material = request.getString("material_name");
        int tint = request.getInt("tint");
        if (tint < 0 || tint > 0xffffff) throw new IOException("Invalid material tint");
        stage(id, job.getInt("stage"), "Revising the selected original material");
        File asset = new File(directory,"asset.glb");
        OriginalMaterialVariant.Result variant = OriginalMaterialVariant.write(source,asset,material,
                VisualHull.linear(tint >>> 16),VisualHull.linear(tint >>> 8),VisualHull.linear(tint));
        FilesUtil.write(new File(directory,"project.json"),request.toString(2));
        stage(id, 3, "Writing the material variant receipt");
        String assetSha = FilesUtil.hash(asset);
        JSONObject receipt = new JSONObject().put("schema","asterion.foundry.receipt.v1").put("job",id)
                .put("recipe",MATERIAL_VARIANT).put("outcome","complete").put("source_sha256",source.getName())
                .put("asset_sha256",assetSha).put("source_bytes_preserved",false).put("binary_payload_preserved",true)
                .put("material_name",variant.materialName()).put("material_index",variant.materialIndex())
                .put("resulting_base_color_factor",new JSONArray().put(variant.red()).put(variant.green())
                        .put(variant.blue()).put(variant.alpha()))
                .put("visual_quality","UNVALIDATED").put("unreal_import_executed",false);
        FilesUtil.write(new File(directory,"receipt.json"),receipt.toString(2));
        FilesUtil.write(new File(directory,"import-manifest.json"),new JSONObject()
                .put("schema","asterion.foundry.intermediate.v1").put("asset","asset.glb").put("sha256",assetSha)
                .put("source_sha256",source.getName()).put("native_acceptance",false).toString(2));
        store.finish(id,MATERIAL_VARIANT,SystemClock.elapsedRealtime()-start,false,receipt);
    }

    private record Candidate(Mesh mesh, JSONObject metrics, byte[] texture) {
        Candidate(Mesh mesh, JSONObject metrics) { this(mesh,metrics,null); }
    }

    private record Base(File mesh, File texture, String textureSha) {
        byte[] textureBytes() throws IOException {
            return texture == null ? null : Files.readAllBytes(texture.toPath());
        }
    }

    private Base base(JSONObject request) throws Exception {
        String parent = request.getString("parent");
        if (!"complete".equals(store.job(parent).getString("state"))) {
            throw new IOException("The base must be a completed editable project");
        }
        File directory = store.project(parent);
        File mesh = new File(directory,"checkpoint.mesh");
        String expected = request.getString("parent_mesh_sha256");
        JSONObject receipt = new JSONObject(Files.readString(new File(directory,"receipt.json").toPath()));
        if (!expected.equals(receipt.optString("editable_mesh_sha256")) || !expected.equals(FilesUtil.hash(mesh))) {
            throw new IOException("Base geometry checksum does not match");
        }
        String textureSha = receipt.optString("texture_sha256");
        File texture = textureSha.isEmpty() ? null : new File(directory,"base-color.png");
        if (texture != null && !textureSha.equals(FilesUtil.hash(texture))) {
            throw new IOException("Base texture checksum does not match");
        }
        return new Base(mesh, texture, textureSha);
    }

    private static Candidate readCandidate(File mesh, File metrics, File texture, String kind) {
        if (!mesh.isFile() || (HULL.equals(kind) || DEPTH.equals(kind) || VOLUME.equals(kind)) && !metrics.isFile()) return null;
        try {
            JSONObject evidence = metrics.isFile()
                    ? new JSONObject(Files.readString(metrics.toPath())) : new JSONObject();
            if (HULL.equals(kind) && !evidence.has("texture_sha256")) return null;
            if (VOLUME.equals(kind) && (!TripoModels.VERSION.equals(evidence.optString("model_pack"))
                    || !TriplaneMesh.PIPELINE_VERSION.equals(evidence.optString("pipeline")))) return null;
            byte[] image = null;
            if (evidence.has("texture_sha256")) {
                if (!evidence.getString("texture_sha256").equals(FilesUtil.hash(texture))) return null;
                image = Files.readAllBytes(texture.toPath());
            }
            return new Candidate(Mesh.load(mesh), evidence, image);
        } catch (Exception damaged) {
            return null;
        }
    }

    private Candidate generate(JSONObject request, Map<String, File> files, File directory, String volumeWorkspace, String id, Base base) throws Exception {
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
            File workspace = new File(directory, "volume-" + volumeWorkspace);
            Files.createDirectories(workspace.toPath());
            SourceFiles.Raster input = SourceFiles.decode(files.get("source"), 1024);
            TriplaneMesh.Result result = TriplaneMesh.reconstruct(TripoModels.directory(store.root), workspace,
                    input.pixels(), input.width(), input.height(), detail, scale, this::check,
                    message -> store.state(id, "running", 0, message));
            return new Candidate(result.mesh(), new JSONObject()
                    .put("method", "learned_triplane_density")
                    .put("model_pack", TripoModels.VERSION).put("pipeline",TriplaneMesh.PIPELINE_VERSION)
                    .put("reference_view",new JSONObject(result.referenceView().evidence()))
                    .put("grid_resolution", result.cells())
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
            int size = (int) Math.sqrt(result.atlas().length);
            Bitmap atlas = Bitmap.createBitmap(result.atlas(),size,size,Bitmap.Config.ARGB_8888);
            try {
                ByteArrayOutputStream png = new ByteArrayOutputStream();
                if (!atlas.compress(Bitmap.CompressFormat.PNG,100,png)) {
                    throw new IOException("Reference atlas could not be encoded");
                }
                metrics.put("textured_triangles", result.texturedTriangles())
                        .put("texture_size", size).put("material", "projected_reference_base_color");
                return new Candidate(result.mesh(), metrics, png.toByteArray());
            } finally {
                atlas.recycle();
            }
        }
        if (REVISION.equals(kind)) {
            JSONArray axes = request.getJSONArray("axes");
            return new Candidate(MeshRevision.apply(Mesh.load(base.mesh()), (float) axes.getDouble(0),
                    (float) axes.getDouble(1), (float) axes.getDouble(2),
                    request.has("color") ? request.getInt("color") : null), new JSONObject(), base.textureBytes());
        }
        if (FORM.equals(kind)) {
            FormLibrary.Profile form = formFrom(request.getJSONObject("form"));
            if (!form.baseScope().isEmpty() && !form.baseScope().equals(formScope(request))) {
                throw new IOException("Saved form is scoped to a different base mesh");
            }
            return new Candidate(form.rule().apply(Mesh.load(base.mesh())), new JSONObject(), base.textureBytes());
        }
        if (MOTION.equals(kind)) {
            return new Candidate(Mesh.load(base.mesh()), new JSONObject(), base.textureBytes());
        }
        if (SKIN_BLEND.equals(kind)) {
            return blendSkin(Mesh.load(base.mesh()),base.textureBytes());
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
        Mesh recipe = Recipes.create(kind, scale, detail);
        return isRigTemplate(kind) ? blendSkin(recipe,null) : new Candidate(recipe, new JSONObject());
    }

    private Candidate blendSkin(Mesh mesh, byte[] texture) throws Exception {
        int changed = SkinBlend.apply(mesh,this::check);
        if (changed == 0) throw new IOException("No compatible joint transition was found");
        return new Candidate(mesh,new JSONObject().put("skin_method",SkinBlend.VERSION)
                .put("blended_vertices",changed).put("deformation_visually_validated",false),texture);
    }

    private static boolean isRigTemplate(String kind) {
        return "Humanoid template".equals(kind) || "Quadruped template".equals(kind);
    }

    static FormLibrary.Profile formFrom(JSONObject snapshot) throws Exception {
        FormLibrary.Rule rule = new FormLibrary.Rule((float) snapshot.getDouble("x"),
                (float) snapshot.getDouble("y"),(float) snapshot.getDouble("z"), snapshot.getInt("tone"),
                (float) snapshot.getDouble("strength"),snapshot.getBoolean("protect_head"));
        return new FormLibrary.Profile(snapshot.getString("name"),snapshot.getString("scope"),
                snapshot.getInt("version"),rule);
    }

    static String formScope(JSONObject request) throws Exception {
        return FilesUtil.hash(request.getString("parent") + "|" + request.getString("parent_mesh_sha256"));
    }

    private JSONObject sourcePoseEvidence(File source) throws Exception {
        JSONObject result = new JSONObject().put("model",PoseModel.IDENTITY).put("model_sha256",PoseModel.SHA256)
                .put("coordinate_space","normalized_original_image_xy")
                .put("minimum_confidence",PoseEvidence.MIN_CONFIDENCE)
                .put("reconstructed_anatomy_verified",false);
        File cache = new File(store.root,"cache/pose-v1-" + source.getName() + "-" + PoseModel.SHA256 + ".json");
        if (cache.isFile()) {
            try {
                JSONObject saved = new JSONObject(Files.readString(cache.toPath()));
                if (source.getName().equals(saved.getString("source_sha256"))
                        && PoseModel.SHA256.equals(saved.getString("model_sha256"))) {
                    return saved.put("reused",true);
                }
            } catch (IOException | org.json.JSONException damaged) {
                android.util.Log.w("Foundry","Refreshing unreadable pose evidence",damaged);
            }
        }
        try {
            PoseModel.Estimate estimate = PoseModel.predict(this,SourceFiles.decode(source,1024));
            PoseEvidence evidence = estimate.evidence();
            boolean supportedTorso = evidence.hasTorsoSupport();
            JSONArray points = new JSONArray();
            int usable = 0;
            for (PoseEvidence.Point point : evidence.points()) {
                boolean supported = supportedTorso && point.usable();
                if (supported) usable++;
                points.put(new JSONObject().put("joint",point.joint().name())
                        .put("x",point.x()).put("y",point.y()).put("confidence",point.confidence())
                        .put("in_frame",point.inFrame()).put("observation",supported ? "supported_prediction" : "not_established"));
            }
            result.put("status",supportedTorso ? "torso_supported" : "torso_not_established")
                    .put("source_sha256",source.getName()).put("reused",false)
                    .put("supported_landmarks",usable).put("landmarks",points)
                    .put("threads",estimate.threads()).put("inference_and_setup_ms",estimate.elapsedMillis());
            FilesUtil.write(cache,result.toString(2));
        } catch (IOException | LinkageError unavailable) {
            result.put("status","model_unavailable").put("reason",unavailable.getMessage());
            android.util.Log.w("Foundry","Source pose evidence unavailable",unavailable);
        }
        return result;
    }

    private static String method(String kind) {
        return switch (kind) {
            case HULL -> VisualHull.METHOD;
            case VOLUME -> "triposr_volume_surface_vertex_colour";
            case DEPTH -> "midas_relative_depth_relief_v1";
            case REVISION -> "non_destructive_mesh_revision";
            case FORM -> "saved_form_rule_v1";
            case MOTION -> SkeletalMotion.VERSION;
            case SKIN_BLEND -> SkinBlend.VERSION;
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
