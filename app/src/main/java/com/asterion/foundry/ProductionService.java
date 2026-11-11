package com.asterion.foundry;

import android.app.*;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
    static final String PART_HULL = "Calibrated part references";
    static final String REVISION = "Mesh revision";
    static final String PART_SURFACE = "Edit part surface colour";
    static final String PART_MATERIAL = "Edit individual part PBR finish";
    static final String FORM = "Saved form variant";
    static final String MOTION = "Skeletal animation";
    static final String SKIN_BLEND = "Blend skin weights";
    static final String ASSEMBLY = "Assemble two actors";

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
        boolean candidateOnly = VOLUME.equals(kind)||PART_HULL.equals(kind);
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
        if (PART_HULL.equals(kind)) signature.append('|').append(PartHull.METHOD);
        if (isRigTemplate(kind)) signature.append('|').append(SkinBlend.VERSION);
        if ("Humanoid with separate tunic".equals(kind)) signature.append('|').append(ClothDrape.VERSION);
        if ("Articulated dragon".equals(kind)) signature.append("|dragon_anatomy_v1");
        Base base = REVISION.equals(kind) || PART_SURFACE.equals(kind) || PART_MATERIAL.equals(kind) || FORM.equals(kind) || MOTION.equals(kind) || SKIN_BLEND.equals(kind)
                || ASSEMBLY.equals(kind) ? base(request) : null;
        Base partner = ASSEMBLY.equals(kind) ? base(request.getString("partner"),request.getString("partner_mesh_sha256")) : null;
        if (base != null) {
            signature.append("|derived-v2|").append(request.getString("parent_mesh_sha256"))
                    .append('|').append(base.textureSha()).append('|').append(base.materialsSha());
            switch (kind) {
                case FORM -> signature.append('|').append(request.getJSONObject("form"));
                case PART_SURFACE -> signature.append('|').append(PartSurface.VERSION)
                        .append('|').append(request.getString("part_id"))
                        .append('|').append(request.getInt("part_rgb"))
                        .append('|').append(request.getDouble("part_strength"));
                case PART_MATERIAL -> signature.append('|').append(PartMaterials.VERSION)
                        .append('|').append(request.getString("part_id"))
                        .append('|').append(request.getDouble("metallic"))
                        .append('|').append(request.getDouble("roughness"));
                case MOTION -> signature.append('|').append(SkeletalMotion.VERSION).append('|')
                        .append(request.optString("motion_script", request.optString("motion_intent")));
                case SKIN_BLEND -> signature.append('|').append(SkinBlend.VERSION);
                case ASSEMBLY -> signature.append('|').append(RigAssembly.VERSION).append('|')
                        .append(request.getString("partner_mesh_sha256")).append('|').append(partner.textureSha())
                        .append('|').append(request.opt("partner_offset"));
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
        StageManifest stageManifest = new StageManifest(directory);
        // Never silently promote modified or partial outputs to a resumed stage.
        // Old projects without ledgers still load using the existing path.
        stageManifest.verifyAll();

        stage(id, job.getInt("stage"), "Preparing geometry");
        Candidate candidate = readCandidate(checkpoint, checkpointMetrics, texture, kind);
        if (candidate == null) candidate = readCandidate(cache, cacheMetrics, cacheTexture, kind);
        boolean reused = candidate != null;
        if (!reused) candidate = generate(request, originals, directory, volumeWorkspace, id, base, partner);
        if (candidate.texture != null) {
            FilesUtil.write(texture, candidate.texture);
            candidate.metrics.put("texture_sha256", FilesUtil.hash(texture));
        }
        FilesUtil.write(checkpointMetrics, candidate.metrics.toString(2));
        candidate.mesh.save(checkpoint);
        PartMaterials finishes = base == null ? PartMaterials.empty()
                : PartMaterials.load(base.materials(), candidate.mesh);
        if (PART_MATERIAL.equals(kind)) finishes = finishes.change(candidate.mesh,
                request.getString("part_id"), request.getDouble("metallic"), request.getDouble("roughness"));
        File materialLayer = new File(directory, PartMaterials.FILE);
        if (finishes.isEmpty()) Files.deleteIfExists(materialLayer.toPath());
        else finishes.save(materialLayer);
        stageManifest.publish("geometry", Map.of("production_key", key),
                Map.of("checkpoint.mesh", checkpoint, "reference-metrics.json", checkpointMetrics),
                SystemClock.elapsedRealtime() - start);
        stage(id, 1, "Geometry checkpoint saved");
        if (!reused || !cache.isFile()) {
            if (candidate.texture != null) FilesUtil.write(cacheTexture, candidate.texture);
            FilesUtil.write(cacheMetrics, candidate.metrics.toString(2));
            candidate.mesh.save(cache);
        }
        if (request.optBoolean("pauseAfterGeometry") && job.getInt("stage") == 0) {
            throw new Paused("Acceptance checkpoint saved. Resume to finish export.");
        }

        stage(id, 2, "Auditing surface, topology and writing GLB");
        JSONObject surfaceEvidence = surfaceEvidence(candidate.mesh, candidate.texture);
        Map<String, String> surfaceInputs = new java.util.LinkedHashMap<>();
        surfaceInputs.put("geometry_sha256", FilesUtil.hash(checkpoint));
        Map<String, File> surfaceFiles = new java.util.LinkedHashMap<>();
        if (candidate.texture == null) surfaceFiles.put("reference-metrics.json", checkpointMetrics);
        else surfaceFiles.put("base-color.png", texture);
        if (!finishes.isEmpty()) {
            surfaceInputs.put("part_materials_sha256", FilesUtil.hash(materialLayer));
            surfaceFiles.put(PartMaterials.FILE, materialLayer);
        }
        stageManifest.publish("surface", surfaceInputs, surfaceFiles,
                SystemClock.elapsedRealtime() - start);
        GeometryAudit.Result audit = PART_HULL.equals(kind) ? null : GeometryAudit.inspect(candidate.mesh, this::check);
        File asset = new File(directory, "asset.glb");
        JSONObject motionEvidence = null;
        if (MOTION.equals(kind)) {
            String script = request.has("motion_script") ? request.getString("motion_script")
                    : SkeletalMotion.compose(candidate.mesh, request.getString("motion_intent"));
            SkeletalMotion clip = SkeletalMotion.parse(candidate.mesh, script);
            SkeletalMotion.Validation validation = Glb.write(candidate.mesh, asset, candidate.texture, clip, finishes);
            File editableMotion = new File(directory,"motion.foundry.txt");
            FilesUtil.write(editableMotion,script);
            motionEvidence = new JSONObject(validation.json()).put("editable_script_sha256",FilesUtil.hash(editableMotion))
                    .put("method",request.has("motion_script") ? "authored_local_joint_tracks" : "deterministic_motion_composition")
                    .put("visual_playback_validation", "not_performed");
        } else {
            Glb.write(candidate.mesh, asset, candidate.texture, null, finishes);
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
                .put("outcome", candidateOnly ? "candidate" : "complete")
                .put("backend", VOLUME.equals(kind) ? "ONNX Runtime ARM64 CPU" : "Android CPU / ART")
                .put("production_export_admitted", !candidateOnly)
                .put("vulkan_compute", "not_implemented").put("qnn", "not_implemented")
                .put("unreal_import_executed", false).put("noworld_binding", "unbound")
                .put("visual_quality", "UNVALIDATED")
                .put("reference_metrics", candidate.metrics)
                .put("surface_audit", surfaceEvidence)
                .put("stage_manifest", "stage-manifest.json")
                .put("stage_evidence", "technical_checks_only")
                .put("production_method", method(kind))
                .put("source_appearance_preserved", false)
                .put("mobile_optimization", "not_validated")
                .put("rig_validation", candidateOnly ? "unrigged" : candidate.mesh.bones.isEmpty()
                        ? "not_applicable" : candidate.mesh.blended() ? "hierarchy_and_normalized_blend_weights"
                        : "hierarchy_and_normalized_rigid_weights_only");
        if (candidate.mesh.parts().size() > 1 || PART_HULL.equals(kind)) {
            JSONArray parts = new JSONArray();
            for (int i = 0; i < candidate.mesh.parts().size(); i++) {
                Mesh.Part part = candidate.mesh.parts().get(i);
                parts.put(new JSONObject().put("id",part.id()).put("name",part.name()).put("role",part.role())
                        .put("triangles",(candidate.mesh.partEnd(i)-part.firstVertex())/3));
            }
            receipt.put("mesh_parts",parts).put("shared_skin",!candidate.mesh.bones.isEmpty());
        }
        if(!candidate.mesh.bones.isEmpty())receipt.put("rig_roots",candidate.mesh.bones.stream().filter(bone->bone.parent<0).count());
        if (candidate.texture != null) receipt.put("texture_sha256", candidate.metrics.getString("texture_sha256"));
        if (!finishes.isEmpty()) receipt.put("part_materials_sha256", FilesUtil.hash(materialLayer));
        if (motionEvidence != null) receipt.put("motion",motionEvidence);
        if (VOLUME.equals(kind) && request.optBoolean("human_reference")) {
            stage(id, 3, "Checking source anatomy evidence");
            receipt.put("source_pose_evidence",sourcePoseEvidence(originals.get("source")));
        }
        if (base != null) {
            receipt.put("parent_mesh_sha256", request.getString("parent_mesh_sha256"));
        }
        if (PART_SURFACE.equals(kind)) {
            receipt.put("part_surface_revision", new JSONObject()
                    .put("part_id", request.getString("part_id"))
                    .put("target_rgb", String.format(java.util.Locale.ROOT, "#%06X", request.getInt("part_rgb")))
                    .put("strength", request.getDouble("part_strength"))
                    .put("method", PartSurface.VERSION)
                    .put("geometry_and_skin_unchanged", true)
                    .put("source_texture_reused", base.texture() != null)
                    .put("visual_fidelity", "unverified"));
        }
        if (PART_MATERIAL.equals(kind)) {
            receipt.put("part_material_revision", new JSONObject()
                    .put("part_id", request.getString("part_id"))
                    .put("metallic", request.getDouble("metallic"))
                    .put("roughness", request.getDouble("roughness"))
                    .put("geometry_skin_and_base_texture_unchanged", true)
                    .put("preview_pbr_lighting", "not_validated")
                    .put("texture_reconstruction", "not_implemented"));
        }
        if (FORM.equals(kind)) {
            receipt.put("form",request.getJSONObject("form"))
                    .put("form_generation", "deterministic_proportions_and_material_tint")
                    .put("canonical_form_admitted", false);
        }
        receipt.put("topology",PART_HULL.equals(kind)?new JSONObject().put("status","reported_per_part"):topology(audit));
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
        Map<String, String> exportInputs = new java.util.LinkedHashMap<>(Map.of(
                "geometry_sha256", FilesUtil.hash(checkpoint),
                "surface_sha256", FilesUtil.hash(candidate.texture == null ? checkpointMetrics : texture)));
        if (!finishes.isEmpty()) exportInputs.put("part_materials_sha256", FilesUtil.hash(materialLayer));
        stageManifest.publish("export", exportInputs,
                Map.of("asset.glb", asset, "receipt.json", new File(directory, "receipt.json"),
                        "import-manifest.json", new File(directory, "import-manifest.json")),
                SystemClock.elapsedRealtime() - start);
        stageManifest.verifyAll();
        if (candidateOnly) {
            store.finishCandidate(id, kind, SystemClock.elapsedRealtime() - start, reused, receipt);
        } else {
            store.finish(id, kind, SystemClock.elapsedRealtime() - start, reused, receipt);
        }
    }

    private static JSONObject surfaceEvidence(Mesh mesh, byte[] texture) throws Exception {
        if (texture == null) return SurfaceAudit.inspect(mesh, null, 0, 0);
        BitmapFactory.Options dimensions = new BitmapFactory.Options();
        dimensions.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(texture, 0, texture.length, dimensions);
        if (dimensions.outWidth <= 0 || dimensions.outHeight <= 0) {
            throw new IOException("Saved base-colour image cannot be decoded");
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 1;
        while (dimensions.outWidth / options.inSampleSize > 512
                || dimensions.outHeight / options.inSampleSize > 512) options.inSampleSize *= 2;
        Bitmap bitmap = BitmapFactory.decodeByteArray(texture, 0, texture.length, options);
        if (bitmap == null) throw new IOException("Saved base-colour image cannot be decoded");
        try {
            int width = bitmap.getWidth(), height = bitmap.getHeight();
            int[] rgba = new int[width * height];
            bitmap.getPixels(rgba, 0, width, 0, 0, width, height);
            return SurfaceAudit.inspect(mesh, rgba, width, height);
        } finally {
            bitmap.recycle();
        }
    }

    private static JSONObject topology(GeometryAudit.Result audit) throws Exception {
        if(audit==null)return new JSONObject().put("status","skipped_over_vertex_budget");
        return new JSONObject().put("boundary_edges",audit.boundaryEdges()).put("nonmanifold_edges",audit.nonManifoldEdges())
                .put("winding_conflicts",audit.windingConflicts()).put("collapsed_triangles",audit.collapsedTriangles())
                .put("weld_tolerance_metres",audit.toleranceMetres());
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

    private record Base(File mesh, File texture, String textureSha, File materials, String materialsSha) {
        byte[] textureBytes() throws IOException {
            return texture == null ? null : Files.readAllBytes(texture.toPath());
        }
    }

    private Base base(JSONObject request) throws Exception {
        return base(request.getString("parent"),request.getString("parent_mesh_sha256"));
    }

    private Base base(String parent,String expected) throws Exception {
        if (!"complete".equals(store.job(parent).getString("state"))) {
            throw new IOException("The base must be a completed editable project");
        }
        File directory = store.project(parent);
        File mesh = new File(directory,"checkpoint.mesh");
        JSONObject receipt = new JSONObject(FilesUtil.readString(new File(directory,"receipt.json").toPath()));
        if (!expected.equals(receipt.optString("editable_mesh_sha256")) || !expected.equals(FilesUtil.hash(mesh))) {
            throw new IOException("Base geometry checksum does not match");
        }
        String textureSha = receipt.optString("texture_sha256");
        File texture = textureSha.isEmpty() ? null : new File(directory,"base-color.png");
        if (texture != null && !textureSha.equals(FilesUtil.hash(texture))) {
            throw new IOException("Base texture checksum does not match");
        }
        String materialsSha = receipt.optString("part_materials_sha256");
        File materials = materialsSha.isEmpty() ? null : new File(directory, PartMaterials.FILE);
        if (materials != null && !materialsSha.equals(FilesUtil.hash(materials))) {
            throw new IOException("Base PBR material layer checksum does not match");
        }
        return new Base(mesh, texture, textureSha, materials, materialsSha);
    }

    private static Candidate readCandidate(File mesh, File metrics, File texture, String kind) {
        if (!mesh.isFile() || (HULL.equals(kind) || PART_HULL.equals(kind) || DEPTH.equals(kind) || VOLUME.equals(kind)) && !metrics.isFile()) return null;
        try {
            JSONObject evidence = metrics.isFile()
                    ? new JSONObject(FilesUtil.readString(metrics.toPath())) : new JSONObject();
            if ((HULL.equals(kind)||PART_HULL.equals(kind)) && !evidence.has("texture_sha256")) return null;
            if (PART_HULL.equals(kind)&&!PartHull.METHOD.equals(evidence.optString("method"))) return null;
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

    private Candidate generate(JSONObject request, Map<String, File> files, File directory, String volumeWorkspace, String id, Base base, Base partner) throws Exception {
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
            metrics.put("textured_triangles", result.texturedTriangles())
                    .put("texture_size", size).put("material", "projected_reference_base_color");
            return new Candidate(result.mesh(),metrics,encodeAtlas(result.atlas(),size,size));
        }
        if (PART_HULL.equals(kind)) {
            stage(id,0,"Carving the labelled reference parts");
            PartHull.Result result=PartReferenceBundle.reconstruct(files.get("source"),scale,detail,this::check);
            JSONArray measurements=new JSONArray();
            for(PartHull.Evidence evidence:result.parts()){
                measurements.put(new JSONObject().put("id",evidence.part().id()).put("role",evidence.part().role())
                        .put("front_iou",evidence.frontIou()).put("right_iou",evidence.rightIou())
                        .put("back_iou",evidence.backIou()==null?JSONObject.NULL:evidence.backIou())
                        .put("occupied_cells",evidence.occupiedCells()).put("components_6",evidence.connectedComponents())
                        .put("topology",topology(evidence.topology())));
            }
            JSONObject metrics=new JSONObject().put("method",PartHull.METHOD).put("parts",measurements)
                    .put("evidence","supplied_part_masks_in_shared_orthographic_frames")
                    .put("canvas_height_metres",scale).put("height_cells",detail)
                    .put("atlas_width",result.width()).put("atlas_height",result.height())
                    .put("cross_part_fit","unverified").put("hidden_geometry","inferred_by_space_carving");
            return new Candidate(result.mesh(),metrics,encodeAtlas(result.atlas(),result.width(),result.height()));
        }
        if (PART_MATERIAL.equals(kind)) {
            return new Candidate(Mesh.load(base.mesh()), new JSONObject()
                    .put("method", PartMaterials.VERSION)
                    .put("target_part", request.getString("part_id"))
                    .put("geometry_edit", false).put("texture_edit", false),
                    base.textureBytes());
        }
        if (PART_SURFACE.equals(kind)) {
            Mesh recoloured = PartSurface.recolour(Mesh.load(base.mesh()),
                    request.getString("part_id"), request.getInt("part_rgb"),
                    (float) request.getDouble("part_strength"));
            return new Candidate(recoloured, new JSONObject()
                    .put("method", PartSurface.VERSION)
                    .put("target_part", request.getString("part_id"))
                    .put("source_texture_preserved", true)
                    .put("geometry_edit", false), base.textureBytes());
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
        if (ASSEMBLY.equals(kind)) {
            if (base.texture()!=null||partner.texture()!=null) throw new IOException("Two-actor assembly currently supports vertex-coloured rigs");
            Mesh first=Mesh.load(base.mesh()),second=Mesh.load(partner.mesh());
            JSONArray supplied=request.optJSONArray("partner_offset");
            float x=supplied==null?RigAssembly.defaultPartnerX(first,second):(float)supplied.getDouble(0);
            float y=supplied==null?0:(float)supplied.getDouble(1),z=supplied==null?0:(float)supplied.getDouble(2);
            Mesh combined=RigAssembly.merge(first,second,x,y,z,this::check);
            return new Candidate(combined,new JSONObject().put("actor_a_root",0).put("actor_b_root",first.bones.size())
                    .put("partner_offset_metres",new JSONArray().put(x).put(y).put(z))
                    .put("partner_mesh_sha256",request.getString("partner_mesh_sha256")));
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
        if ("Articulated dragon".equals(kind)) return new Candidate(recipe,new JSONObject()
                .put("anatomy","articulated_dragon_with_wing_digits").put("appearance_source","procedural_basis"));
        if (!isRigTemplate(kind)) return new Candidate(recipe,new JSONObject());
        Candidate bound=blendSkin(recipe,null);
        if ("Humanoid with separate tunic".equals(kind)) bound.metrics.put("garment_fit",ClothDrape.VERSION)
                .put("animated_cloth_simulation",false);
        return bound;
    }

    private static byte[] encodeAtlas(int[] pixels,int width,int height) throws IOException {
        Bitmap atlas=Bitmap.createBitmap(pixels,width,height,Bitmap.Config.ARGB_8888);
        try{
            ByteArrayOutputStream png=new ByteArrayOutputStream();
            if(!atlas.compress(Bitmap.CompressFormat.PNG,100,png))throw new IOException("Reference atlas could not be encoded");
            return png.toByteArray();
        }finally{atlas.recycle();}
    }

    private Candidate blendSkin(Mesh mesh, byte[] texture) throws Exception {
        int changed = SkinBlend.apply(mesh,this::check);
        if (changed == 0) throw new IOException("No compatible joint transition was found");
        return new Candidate(mesh,new JSONObject().put("skin_method",SkinBlend.VERSION)
                .put("blended_vertices",changed).put("deformation_visually_validated",false),texture);
    }

    private static boolean isRigTemplate(String kind) {
        return "Humanoid template".equals(kind) || "Humanoid with separate tunic".equals(kind)
                || "Quadruped template".equals(kind);
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
                JSONObject saved = new JSONObject(FilesUtil.readString(cache.toPath()));
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
            case PART_HULL -> PartHull.METHOD;
            case VOLUME -> "triposr_volume_surface_vertex_colour";
            case DEPTH -> "midas_relative_depth_relief_v1";
            case REVISION -> "non_destructive_mesh_revision";
            case PART_SURFACE -> PartSurface.VERSION;
            case PART_MATERIAL -> PartMaterials.VERSION;
            case FORM -> "saved_form_rule_v1";
            case MOTION -> SkeletalMotion.VERSION;
            case SKIN_BLEND -> SkinBlend.VERSION;
            case ASSEMBLY -> RigAssembly.VERSION;
            case "Articulated dragon" -> "procedural_dragon_anatomy_v1";
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
