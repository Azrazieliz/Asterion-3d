package com.asterion.foundry;
import android.app.*;import android.content.*;import android.content.pm.*;import android.graphics.Color;import android.graphics.drawable.GradientDrawable;import android.net.Uri;import android.os.*;import android.view.*;import android.widget.*;import org.json.*;import java.io.*;import java.util.*;import java.util.zip.*;
public final class MainActivity extends Activity {
 final int BG=0xff10141e,PANEL=0xff1b2432,INK=0xffeff5f7,MUTED=0xffa7b6c8,ACCENT=0xff83e5c6;
 Store store;LinearLayout body;TextView status;Spinner kind;EditText prompt,scale;String source="",page="Create",exportId, nextRecipe, filter=""; final Map<String,String> references = new LinkedHashMap<>();MeshView preview;Handler handler=new Handler();boolean visible;
 static final String KNOWN = "Find registered original";
 static final String HUMAN_REFERENCE = "Character reference candidate";
 String bindingReference, handoffProfile;
 Button motionPlaybackButton;
 final String[] kinds={"Route from description","Sword","Pavilion","Tree","Terrain","Image silhouette","Humanoid template","Humanoid with separate tunic","Quadruped template","Articulated dragon","OBJ geometry",ProductionService.ORIGINAL_GLB,ProductionService.NOWORLD_GLB,ProductionService.HULL,ProductionService.DEPTH,ProductionService.VOLUME,HUMAN_REFERENCE,KNOWN};
 public void onCreate(Bundle b){super.onCreate(b);store=new Store(this);if(b!=null){source=b.getString("source","");exportId=b.getString("exportId");bindingReference=b.getString("bindingReference");handoffProfile=b.getString("handoffProfile");for(String role:new String[]{"front","right","back"})if(b.containsKey(role))references.put(role,b.getString(role));}if(!ProductionService.active)store.recover();getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);show("Create");if(Build.VERSION.SDK_INT>=33&&checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},8);}
 int dp(int n){return (int)(getResources().getDisplayMetrics().density*n);}
 TextView text(String value,int size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setPadding(0,dp(6),0,dp(6));return t;}
 void label(String v){body.addView(text(v,14,MUTED));}
 Button button(String name,Runnable action){Button b=new Button(this);b.setText(name);b.setTextColor(INK);b.setAllCaps(false);GradientDrawable d=new GradientDrawable();d.setColor(PANEL);d.setCornerRadius(dp(12));b.setBackground(d);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(48));p.setMargins(0,dp(5),0,dp(5));body.addView(b,p);b.setOnClickListener(v->{try{action.run();}catch(Exception e){error(e);}});return b;}
 void show(String dest){if(preview!=null){preview.onPause();preview=null;}motionPlaybackButton=null;page=dest;LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setBackgroundColor(BG);root.setPadding(dp(18),dp(12),dp(18),dp(12));TextView title=text("ASTERION  /  FOUNDRY",14,ACCENT);root.addView(title);HorizontalScrollView nav=new HorizontalScrollView(this);LinearLayout row=new LinearLayout(this);for(String p:new String[]{"Create","Projects","Studio","Library","Queue","Koikatsu","Performance"}){TextView tab=text(p,15,p.equals(dest)?ACCENT:MUTED);tab.setPadding(dp(10),dp(8),dp(10),dp(8));tab.setOnClickListener(v->show(p));row.addView(tab);}nav.addView(row);root.addView(nav);ScrollView scroll=new ScrollView(this);body=new LinearLayout(this);body.setOrientation(1);scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);body.addView(text(dest,30,INK));try{switch(dest){case "Create":create();break;case "Projects":projects(false);break;case "Studio":studio();break;case "Library":library();break;case "Queue":projects(true);break;case "Koikatsu":kk();break;case "Performance":performance();break;}}catch(Exception e){error(e);}}
 void create() {
  label("Offline production");
  prompt = new EditText(this);
  prompt.setTextColor(INK);
  prompt.setHintTextColor(MUTED);
  prompt.setHint("Project name or recipe description");
  body.addView(prompt);
  kind = new Spinner(this);
  kind.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, kinds));
  if (nextRecipe != null) {
   kind.setSelection(Arrays.asList(kinds).indexOf(nextRecipe));
   nextRecipe = null;
  }
  body.addView(kind);
  scale = new EditText(this);
  scale.setText("1.0");
  scale.setTextColor(INK);
  scale.setInputType(8194);
  body.addView(scale);
  label("Metres for reconstruction or image size. Multiplier for procedural recipes.");
  LinearLayout inputs = new LinearLayout(this);
  inputs.setOrientation(1);
  body.addView(inputs);
  kind.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
   public void onItemSelected(AdapterView<?> parent, View view, int position, long rowId) {
    inputs.removeAllViews();
    LinearLayout previous = body;
    body = inputs;
    try {
     String selected = kinds[position];
     if (ProductionService.HULL.equals(selected)) {
      label("Use upright front and right orthographic views of the same subject. An aligned back view is optional.");
      for (int i = 0; i < 3; i++) {
       String role = new String[]{"front", "right", "back"}[i];
       int request = 11 + i;
       Button control = button(viewLabel(role), () -> pick(request, "image/*"));
       control.setTag(role);
      }
     } else if (needsOriginal(selected)) {
      if (KNOWN.equals(selected)) label("Reuse the original GLB registered to these exact image bytes.");
      if (ProductionService.VOLUME.equals(selected) || HUMAN_REFERENCE.equals(selected)) {
       label("Local volumetric reconstruction candidate. Occluded geometry is inferred. Fidelity and rigging are unverified.");
       if (HUMAN_REFERENCE.equals(selected)) label("Check which human joints the reference supports. Low-confidence joints stay unobserved.");
       if (!TripoModels.ready(store.root)) button("Install offline 3D model pack", () -> pick(20, "*/*"));
      }
      if (ProductionService.DEPTH.equals(selected)) {
       label("Bundled MiDaS estimates the visible surface. Output has a flat backing and relative depth.");
      }
      Button control = button(originalLabel(), () -> pick(1,
              ("OBJ geometry".equals(selected) || originalGlbMode(selected)) ? "*/*" : "image/*"));
      control.setTag("source");
      if (ProductionService.NOWORLD_GLB.equals(selected)) {
       label("Match a self-contained GLB against a declared NoWorld source profile. Unreal import runs separately.");
       button(handoffLabel(), () -> pick(23,"*/*")).setTag("contract");
      }
     } else if (selected.endsWith("template")) {
      label("Generates a rig template. Use multi-view reconstruction for reference-shaped geometry.");
     }
    } finally {
     body = previous;
    }
   }
   public void onNothingSelected(AdapterView<?> parent) {}
  });
  button("Create", () -> submit(false));
  button("Create with interruption checkpoint", () -> submit(true));
 }

 private static boolean needsOriginal(String recipe) {
  return KNOWN.equals(recipe) || HUMAN_REFERENCE.equals(recipe) || "Image silhouette".equals(recipe) || "OBJ geometry".equals(recipe)
          || ProductionService.VOLUME.equals(recipe) || ProductionService.DEPTH.equals(recipe) || originalGlbMode(recipe);
 }

 private static boolean originalGlbMode(String recipe) {
  return ProductionService.ORIGINAL_GLB.equals(recipe) || ProductionService.NOWORLD_GLB.equals(recipe);
 }

 private String handoffLabel() {
  return (handoffProfile == null ? "Choose" : "Replace") + " NoWorld JSON profile";
 }

 private String viewLabel(String role) {
  return (references.containsKey(role) ? "Replace " : "Choose ") + role + " view";
 }

 private String originalLabel() {
  return source.isEmpty() ? "Choose original file" : "Replace original file";
 }

 private void pick(int code, String type) {
  Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
  intent.setType(type);
  intent.addCategory(Intent.CATEGORY_OPENABLE);
  startActivityForResult(intent, code);
 }

 void submit(boolean pause) {
  try {
   String selected = (String) kind.getSelectedItem();
   boolean humanReference = HUMAN_REFERENCE.equals(selected);
   if (humanReference) selected = ProductionService.VOLUME;
   String name = prompt.getText().toString().trim();
   String description = name.toLowerCase(Locale.ROOT);
   if ("Route from description".equals(selected)) {
    if (description.matches(".*\\btree\\b.*")) selected = "Tree";
    else if (description.matches(".*\\b(terrain|landscape)\\b.*")) selected = "Terrain";
    else if (description.matches(".*\\bpavilion\\b.*")) selected = "Pavilion";
    else if (description.matches(".*\\b(sword|blade)\\b.*")) selected = "Sword";
    else throw new IllegalArgumentException("Choose a production method or describe a supported recipe");

   }
   if (needsOriginal(selected) && source.isEmpty()) {
    throw new IllegalArgumentException("Choose an original file first");
   }
   if (KNOWN.equals(selected) || ProductionService.VOLUME.equals(selected)) {
    File original = new ReferenceRegistry(store.root).find(new File(source).getName());
    if (original != null) {
     enqueue(new JSONObject().put("kind",ProductionService.ORIGINAL_GLB).put("description",name)
             .put("source",original.getAbsolutePath()).put("reference",source)
             .put("scale",1).put("detail",12).put("pauseAfterGeometry",pause));
     return;
    }
    if (KNOWN.equals(selected)) throw new IllegalArgumentException("No original is registered to this exact image");
    if (!TripoModels.ready(store.root)) {
     throw new IllegalArgumentException("Install the offline 3D model pack before reconstruction");
    }
   }
   if (ProductionService.HULL.equals(selected)
           && (!references.containsKey("front") || !references.containsKey("right"))) {
    throw new IllegalArgumentException("Choose both the front and right reference views");
   }
   float value = Float.parseFloat(scale.getText().toString());
   if (!Float.isFinite(value) || value < .01 || value > 100) {
    throw new IllegalArgumentException("Use a size between 0.01 and 100");
   }
   JSONObject request = new JSONObject().put("kind", selected).put("description", name)
           .put("scale", value).put("detail", ProductionService.VOLUME.equals(selected) ? 112 : ProductionService.HULL.equals(selected) ? 40 : 12)
           .put("source", needsOriginal(selected) ? source : "").put("pauseAfterGeometry", pause);
   if (humanReference) request.put("human_reference",true);
   if (ProductionService.HULL.equals(selected)) {
    for (Map.Entry<String,String> reference : references.entrySet()) {
     request.put(reference.getKey(), reference.getValue());
    }
   }
   if (ProductionService.NOWORLD_GLB.equals(selected)) {
    if (handoffProfile == null) throw new IllegalArgumentException("Choose the NoWorld JSON profile");
    request.put("contract",handoffProfile);
   }
   enqueue(request);
  } catch (Exception failure) {
   error(failure);
  }
 }

 private void enqueue(JSONObject request) throws Exception {
  store.enqueue(request);
  startQueue();
  show("Queue");
 }

 private void studio() {
  label("Character and creature");
  studioRecipe(HUMAN_REFERENCE);
  studioRecipe(ProductionService.HULL);
  studioRecipe("Humanoid template");
  studioRecipe("Humanoid with separate tunic");
  studioRecipe("Quadruped template");
  studioRecipe("Articulated dragon");
  label("Objects and environments");
  for (String recipe : new String[]{ProductionService.DEPTH, ProductionService.ORIGINAL_GLB, "OBJ geometry", "Sword", "Pavilion", "Tree", "Terrain"}) {
   studioRecipe(recipe);
  }
 }

 private void studioRecipe(String recipe) {
  button(recipe, () -> {
   nextRecipe = recipe;
   show("Create");
  });
 }

 private void library() throws Exception {
  button("Register reference and original GLB", () -> pick(21,"image/*"));
  button("Save shared form rules", () -> formEditor("", null));
  for (FormLibrary.Profile profile : new FormLibrary(store.root).available("")) {
   button(profile.name() + " · rules v" + profile.revision(), () -> manageForm(profile));
  }
  EditText search = new EditText(this);
  search.setHint("Search completed assets");
  search.setSingleLine(true);
  search.setText(filter);
  body.addView(search);
  button("Search library", () -> {
   filter = search.getText().toString().trim();
   show("Library");
  });
  String query = filter.toLowerCase(Locale.ROOT);
  int matches = 0;
  for (JSONObject row : store.jobs()) {
   if (!"complete".equals(row.getString("state"))) continue;
   JSONObject request = new JSONObject(row.getString("request"));
   String name = request.optString("description");
   String recipe = request.getString("kind");
   if (!(name + " " + recipe).toLowerCase(Locale.ROOT).contains(query)) continue;
   String id = row.getString("id");
   button(name.isEmpty() ? recipe : name + " · " + recipe, () -> inspect(id));
   matches++;
  }
  if (matches == 0) label("No matching completed assets");
 }
 void startQueue(){try{startForegroundService(new Intent(this,ProductionService.class));}catch(Exception e){error(e);}}
 void projects(boolean queue)throws Exception{label(queue?"Stages reflect completed writes. Interrupted jobs keep their geometry checkpoints.":"Editable projects and export packages. Sources remain immutable.");if(queue){button("Run queued jobs",this::startQueue);button("Pause after current stage",()->{if(ProductionService.active)startService(new Intent(this,ProductionService.class).setAction("pause"));});}button("Refresh",()->show(page));ArrayList<JSONObject> jobs=store.jobs();if(jobs.isEmpty())label("No projects yet. Create an asset to begin.");for(JSONObject j:jobs){JSONObject q=new JSONObject(j.getString("request"));String id=j.getString("id"),state=j.getString("state");body.addView(text(q.getString("kind"),21,INK));label(state.toUpperCase(Locale.ROOT)+" · "+j.getString("message"));if(!q.optString("description").isEmpty())label(q.optString("description"));if(state.equals("paused")||state.equals("failed"))button("Resume "+id.substring(0,6),()->{store.state(id,"queued",j.optInt("stage"),"Resume requested");startQueue();show("Queue");});if(new File(store.project(id),"checkpoint.mesh").exists())button("Inspect asset",()->inspect(id));if(state.equals("complete")){if(originalGlbMode(q.optString("kind"))||ProductionService.MATERIAL_VARIANT.equals(q.optString("kind")))button("Inspect GLB receipt",()->inspect(id));button("Export editable package",()->{exportId=id;Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("application/zip");i.putExtra(Intent.EXTRA_TITLE,"Foundry-"+id.substring(0,8)+".zip");startActivityForResult(i,2);});button("Repeat using saved recipe",()->{try{store.enqueue(q);startQueue();show("Queue");}catch(Exception e){error(e);}});}label("────────────────────────");}}
 void inspect(String id) {
  try {
   if (preview != null) { preview.onPause(); preview = null; }
   motionPlaybackButton = null;
   File directory = store.project(id);
   if (!new File(directory, "checkpoint.mesh").isFile()) {
    body.removeAllViews();
    JSONObject input = new JSONObject(store.job(id).getString("request"));
    body.addView(text(ProductionService.MATERIAL_VARIANT.equals(input.getString("kind"))
            ? "Material variant receipt" : "Preserved source inventory",28,INK));
    label(java.nio.file.Files.readString(new File(directory,"receipt.json").toPath()));
    if (originalGlbMode(input.getString("kind"))) {
     button("Create a material variant", () -> chooseOriginalMaterial(input.optString("source")));
    }
    button("Export GLB package", () -> export(id));
    button("Back to projects", () -> show("Projects"));
    return;
   }
   Mesh mesh = Mesh.load(new File(store.project(id), "checkpoint.mesh"));
   JSONObject row = store.job(id);
   JSONObject request = new JSONObject(row.getString("request"));
   boolean complete = "complete".equals(row.getString("state"));
   JSONObject receipt = complete ? new JSONObject(java.nio.file.Files.readString(
           new File(directory,"receipt.json").toPath())) : null;
   SkeletalMotion motion = null;
   String motionScript = "";
   if (complete && ProductionService.MOTION.equals(request.getString("kind"))) {
    File script = new File(directory,"motion.foundry.txt");
    if (!receipt.getJSONObject("motion").getString("editable_script_sha256").equals(FilesUtil.hash(script))) {
     throw new IOException("Saved motion failed its checksum");
    }
    motionScript = java.nio.file.Files.readString(script.toPath());
    motion = SkeletalMotion.parse(mesh,motionScript);
   }
   body.removeAllViews();
   body.addView(text("Inspect asset",28,INK));
   preview = new MeshView(this, mesh, new File(directory,"base-color.png"), motion);
   body.addView(preview,new LinearLayout.LayoutParams(-1,dp(330)));
   label("Drag to orbit · " + mesh.vertices.size()/3 + " triangles · " + mesh.bones.size() + " bones");
   button("Front", () -> preview.view(0,0));
   button("Right", () -> preview.view(-90,0));
   button("Back", () -> preview.view(180,0));
   button("Top", () -> preview.view(0,85));
   if(mesh.parts().size()>1)button("Show or hide mesh parts",()->chooseVisibleParts(mesh));
   if (motion != null) {
    label(motion.name() + " · " + motion.durationSeconds() + " seconds");
    MeshView animated=preview;
    SeekBar timeline=new SeekBar(this);
    timeline.setMax(Math.max(1,Math.round(motion.durationSeconds()*1000)));
    timeline.setContentDescription("Animation position");
    timeline.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
     public void onProgressChanged(SeekBar bar,int millis,boolean fromUser){if(fromUser)animated.seekMotion(millis);}
     public void onStartTrackingTouch(SeekBar bar){}
     public void onStopTrackingTouch(SeekBar bar){}
    });
    animated.onMotionProgress(timeline::setProgress);
    body.addView(timeline);
    motionPlaybackButton = button("Play animation", () -> motionPlaybackButton.setText(
            preview.toggleMotion() ? "Pause animation" : "Play animation"));
   }
   if (complete) {
    button("Create editable revision", () -> revisionDialog(id, request));
    button("Export editable package", () -> export(id));
    if (!mesh.bones.isEmpty()) {
     String baseSha = receipt.getString("editable_mesh_sha256");
     String formScope = FilesUtil.hash(id + "|" + baseSha);
     button("Create a form from saved rules", () -> chooseForm(id, baseSha, formScope, request.optString("description")));
     button("Manage form rules for this base", () -> manageBaseForms(formScope));
     if (ProductionService.FORM.equals(request.getString("kind"))) {
      button("Prefer these settings on the original base", () -> preferForm(request));
     }
     button("Create animation", () -> motionDialog(id,baseSha,null));
     if (ProductionService.ASSEMBLY.equals(request.getString("kind"))) {
      int rootB=receipt.getJSONObject("reference_metrics").getInt("actor_b_root");
      button("Create coordinated animation",()->motionDialog(id,baseSha,
        "clip Coordinated scene\nactor A @0\nactor B @"+rootB+"\nstage A 0 3 idle\nstage B 2 5 idle\n"));
     } else if(mesh.bones.stream().filter(joint->joint.parent<0).count()==1) {
      button("Assemble with another actor",()->choosePartner(id,baseSha));
     }
     if (!mesh.blended() && motion == null) button("Blend skin weights", () -> {
      try {
       enqueue(new JSONObject().put("kind",ProductionService.SKIN_BLEND).put("parent",id)
               .put("parent_mesh_sha256",baseSha).put("scale",1).put("detail",12)
               .put("description",request.optString("description")));
      } catch (Exception failure) { error(failure); }
     });
     if (motion != null) {
      String editable = motionScript;
      button("Edit animation tracks", () -> motionDialog(id,baseSha,editable));
     }
    }
   }
   if (ProductionService.HULL.equals(request.getString("kind")) && request.getInt("detail") < 64) {
    button("Reconstruct at finer resolution", () -> {
     try {
      request.put("detail", 64).put("pauseAfterGeometry", false);
      enqueue(request);
     } catch (Exception failure) { error(failure); }
    });
   }
   File receiptFile = new File(directory, "receipt.json");
   if (receiptFile.isFile()) label(java.nio.file.Files.readString(receiptFile.toPath()));
   button("Back to projects", () -> show("Projects"));
  } catch (Exception failure) { error(failure); }
 }

 private void chooseVisibleParts(Mesh mesh) {
  List<Mesh.Part> parts=mesh.parts();
  String[] names=new String[parts.size()];
  boolean[] checked=new boolean[parts.size()];
  for(int i=0;i<parts.size();i++){
   names[i]=parts.get(i).name();
   checked[i]=preview.partVisible(i);
  }
  MeshView current=preview;
  new AlertDialog.Builder(this).setTitle("Mesh parts")
    .setMultiChoiceItems(names,checked,(dialog,part,visible)->current.setPartVisible(part,visible))
    .setPositiveButton("Done",null).show();
 }

 private void export(String id) {
  exportId = id;
  Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
  intent.setType("application/zip");
  intent.addCategory(Intent.CATEGORY_OPENABLE);
  intent.putExtra(Intent.EXTRA_TITLE, "Foundry-" + id.substring(0,8) + ".zip");
  startActivityForResult(intent,2);
 }

 private void chooseOriginalMaterial(String sourcePath) {
  new Thread(() -> {
   try {
    List<String> names = OriginalMaterialVariant.editableNames(new File(sourcePath));
    if (names.isEmpty()) throw new IOException("No uniquely named editable materials were found in this GLB");
    runOnUiThread(() -> new AlertDialog.Builder(this).setTitle("Choose a source material")
            .setItems(names.toArray(new String[0]),(dialog,item) -> tintDialog(sourcePath,names.get(item))).show());
   } catch (Exception failure) { runOnUiThread(() -> error(failure)); }
  }).start();
 }

 private void tintDialog(String sourcePath, String material) {
  EditText tint = new EditText(this);
  tint.setSingleLine(true);
  tint.setHint("Tint #RRGGBB");
  AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Tint " + material)
          .setMessage("Multiplies the chosen material’s colour. The original rig, morphs, textures and animations are preserved.")
          .setView(tint).setNegativeButton("Cancel",null).setPositiveButton("Create variant",null).create();
  dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
   try {
    String rgb = tint.getText().toString().trim();
    if (!rgb.matches("#[0-9a-fA-F]{6}")) throw new IllegalArgumentException("Use #RRGGBB for tint");
    enqueue(new JSONObject().put("kind",ProductionService.MATERIAL_VARIANT).put("source",sourcePath)
            .put("material_name",material).put("tint",Color.parseColor(rgb) & 0xffffff).put("description",material));
    dialog.dismiss();
   } catch (Exception failure) { error(failure); }
  }));
  dialog.show();
 }

 private void chooseForm(String id, String baseSha, String formScope, String baseName) {
  try {
   List<FormLibrary.Profile> forms = new FormLibrary(store.root).available(formScope);
   if (forms.isEmpty()) throw new IOException("Save shared form rules in Library or rules for this base first");
   String[] choices = forms.stream().map(profile -> profile.name() + " · v" + profile.revision()
           + (profile.baseScope().isEmpty() ? " · shared" : " · this base")).toArray(String[]::new);
   new AlertDialog.Builder(this).setTitle("Choose a saved form").setItems(choices, (dialog, selected) -> {
    try {
     FormLibrary.Profile profile = forms.get(selected);
     FormLibrary.Rule rule = profile.rule();
     JSONObject snapshot = new JSONObject().put("name",profile.name()).put("scope",profile.baseScope())
             .put("version",profile.revision()).put("x",rule.x()).put("y",rule.y()).put("z",rule.z())
             .put("tone",rule.tone()).put("strength",rule.strength()).put("protect_head",rule.preserveHead());
     enqueue(new JSONObject().put("kind",ProductionService.FORM).put("parent",id)
             .put("parent_mesh_sha256",baseSha).put("form",snapshot).put("scale",1).put("detail",12)
             .put("description",baseName.isBlank() ? profile.name() : baseName + " · " + profile.name()));
    } catch (Exception failure) { error(failure); }
   }).show();
  } catch (Exception failure) { error(failure); }
 }

 private void preferForm(JSONObject request) {
  try {
   FormLibrary.Profile form = ProductionService.formFrom(request.getJSONObject("form"));
   new FormLibrary(store.root).save(form.name(),ProductionService.formScope(request),form.rule());
   Toast.makeText(this,form.name() + " will use these settings on the original base",Toast.LENGTH_LONG).show();
  } catch (Exception failure) { error(failure); }
 }

 private void manageBaseForms(String scope) {
  try {
   List<FormLibrary.Profile> profiles = new FormLibrary(store.root).available(scope).stream()
           .filter(form -> scope.equals(form.baseScope())).collect(java.util.stream.Collectors.toList());
   String[] choices = new String[profiles.size()+1];
   choices[0] = "Save new base-specific rules";
   for (int i = 0; i < profiles.size(); i++) choices[i+1] = profiles.get(i).name();
   new AlertDialog.Builder(this).setTitle("Form rules for this base").setItems(choices,(dialog, item) -> {
    if (item == 0) formEditor(scope,null);
    else manageForm(profiles.get(item-1));
   }).show();
  } catch (Exception failure) { error(failure); }
 }

 private void manageForm(FormLibrary.Profile current) {
  new AlertDialog.Builder(this).setTitle(current.name()).setItems(new String[]{"Edit latest rules", "Rule history"},
          (dialog,item) -> {
   if (item == 0) {
    formEditor(current.baseScope(),current);
    return;
   }
   try {
    List<FormLibrary.Profile> history = new FormLibrary(store.root).history(current.name(),current.baseScope());
    String[] versions = history.stream().map(profile -> "Version " + profile.revision()).toArray(String[]::new);
    new AlertDialog.Builder(this).setTitle("Load rules from a version")
            .setItems(versions,(versionsDialog,index) -> formEditor(current.baseScope(),history.get(index))).show();
   } catch (Exception failure) { error(failure); }
  }).show();
 }

 private void motionDialog(String id, String baseSha, String editable) {
  boolean tracks = editable != null;
  EditText input = new EditText(this);
  if (tracks) {
   input.setText(editable);
   input.setMaxLines(12);
  } else {
   input.setSingleLine(true);
   input.setHint("Idle, walk in place, tail wag, dragon flight or perch");
  }
  AlertDialog dialog = new AlertDialog.Builder(this).setTitle(tracks ? "Edit animation tracks" : "Create animation")
          .setMessage(tracks ? "Edit local joint keyframes and optional contact checks."
                  : "Choose a movement that fits the rig. Flight, landing and perch require the articulated dragon rig.")
          .setView(input).setNegativeButton("Cancel",null).setPositiveButton("Create clip",null).create();
  dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
   try {
    String value = input.getText().toString().trim();
    int limit = tracks ? SkeletalMotion.MAX_SCRIPT_CHARS : 80;
    if (value.isEmpty() || value.length() > limit) throw new IllegalArgumentException("Enter a supported animation");
    enqueue(new JSONObject().put("kind",ProductionService.MOTION).put("parent",id)
            .put("parent_mesh_sha256",baseSha).put(tracks ? "motion_script" : "motion_intent",value)
            .put("description",tracks ? "Edited animation" : value).put("scale",1).put("detail",12));
    dialog.dismiss();
   } catch (Exception failure) { error(failure); }
  }));
  dialog.show();
 }

 private void choosePartner(String id,String baseSha){
  try{
   List<String> ids=new ArrayList<>(),hashes=new ArrayList<>(),names=new ArrayList<>();
   for(JSONObject job:store.jobs()){
    String candidate=job.getString("id");
    if(candidate.equals(id)||!job.getString("state").equals("complete"))continue;
    JSONObject request=new JSONObject(job.getString("request"));
    if(ProductionService.ASSEMBLY.equals(request.getString("kind")))continue;
    File receiptFile=new File(store.project(candidate),"receipt.json");
    if(!receiptFile.isFile())continue;
    JSONObject proof=new JSONObject(java.nio.file.Files.readString(receiptFile.toPath()));
    if(proof.optInt("bones")<1||proof.optInt("rig_roots",1)!=1||proof.has("texture_sha256"))continue;
    String checksum=proof.optString("editable_mesh_sha256");
    if(checksum.isEmpty())continue;
    ids.add(candidate);hashes.add(checksum);
    names.add(request.optString("description").isBlank()?request.getString("kind"):request.getString("description"));
   }
   if(ids.isEmpty())throw new IOException("Create another editable rig before assembling actors");
   new AlertDialog.Builder(this).setTitle("Choose the second actor").setItems(names.toArray(new String[0]),(dialog,item)->{
    try{
     enqueue(new JSONObject().put("kind",ProductionService.ASSEMBLY).put("parent",id)
       .put("parent_mesh_sha256",baseSha).put("partner",ids.get(item)).put("partner_mesh_sha256",hashes.get(item))
       .put("description","Two-actor scene with "+names.get(item)).put("scale",1).put("detail",12));
    }catch(Exception failure){error(failure);}
   }).show();
  }catch(Exception failure){error(failure);}
 }

 private void formEditor(String scope, FormLibrary.Profile existing) {
  FormLibrary.Rule rule = existing == null ? new FormLibrary.Rule(1,1,1,0xffffff,0,true) : existing.rule();
  LinearLayout fields = new LinearLayout(this);
  fields.setOrientation(1);
  fields.setPadding(dp(20),0,dp(20),0);
  TextView context = text(scope.isEmpty() ? "Reusable on compatible rigged meshes" : "Overrides the shared rule for this exact base mesh",14,INK);
  fields.addView(context);
  EditText name = formField(fields,"Form name",existing == null ? "" : existing.name());
  if (existing != null) name.setEnabled(false);
  EditText width = formField(fields,"Width multiplier",Float.toString(rule.x()));
  EditText height = formField(fields,"Height multiplier",Float.toString(rule.y()));
  EditText depth = formField(fields,"Depth multiplier",Float.toString(rule.z()));
  EditText tone = formField(fields,"Tint #RRGGBB",String.format(Locale.ROOT,"#%06X",rule.tone()));
  EditText strength = formField(fields,"Tint strength (0–1)",Float.toString(rule.strength()));
  CheckBox protect = new CheckBox(this);
  protect.setText("Keep colours on head and face joints");
  protect.setChecked(rule.preserveHead());
  fields.addView(protect);
  ScrollView scroll = new ScrollView(this);
  scroll.addView(fields);
  AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Save form rules").setView(scroll)
          .setNegativeButton("Cancel",null).setPositiveButton("Save version",null).create();
  dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
   try {
    String rgb = tone.getText().toString().trim();
    if (!rgb.matches("#[0-9a-fA-F]{6}")) throw new IllegalArgumentException("Use #RRGGBB for tint");
    FormLibrary.Rule updated = new FormLibrary.Rule(Float.parseFloat(width.getText().toString()),
            Float.parseFloat(height.getText().toString()),Float.parseFloat(depth.getText().toString()),
            Color.parseColor(rgb) & 0xffffff,Float.parseFloat(strength.getText().toString()),protect.isChecked());
    FormLibrary.Profile saved = new FormLibrary(store.root).save(name.getText().toString(),scope,updated);
    dialog.dismiss();
    Toast.makeText(this,saved.name() + " rules saved · v" + saved.revision(),Toast.LENGTH_LONG).show();
    if ("Library".equals(page)) show("Library");
   } catch (Exception failure) { error(failure); }
  }));
  dialog.show();
 }

 private EditText formField(LinearLayout fields, String caption, String value) {
  fields.addView(text(caption,14,INK));
  EditText entry = new EditText(this);
  entry.setSingleLine(true);
  entry.setText(value);
  fields.addView(entry);
  return entry;
 }

 private void revisionDialog(String id, JSONObject original) {
  LinearLayout fields = new LinearLayout(this);
  fields.setOrientation(1);
  fields.setPadding(dp(20),0,dp(20),0);
  EditText[] axes = new EditText[3];
  for (int i=0; i<axes.length; i++) {
   fields.addView(text(new String[]{"Width ×", "Height ×", "Depth ×"}[i],14,INK));
   axes[i] = new EditText(this);
   axes[i].setInputType(8194);
   axes[i].setText("1.0");
   fields.addView(axes[i]);
  }
  EditText color = new EditText(this);
  color.setSingleLine(true);
  color.setHint("Optional colour #RRGGBB");
  fields.addView(color);
  AlertDialog dialog = new AlertDialog.Builder(this).setTitle("New project revision")
          .setView(fields).setNegativeButton("Cancel",null).setPositiveButton("Save revision",null).create();
  dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
   try {
    JSONArray multipliers = new JSONArray();
    for (EditText axis : axes) {
     float value = Float.parseFloat(axis.getText().toString());
     if (!Float.isFinite(value) || value < .05 || value > 20) {
      throw new IllegalArgumentException("Each multiplier must be between 0.05 and 20");
     }
     multipliers.put(value);
    }
    JSONObject request = new JSONObject(original.toString()).put("kind",ProductionService.REVISION)
            .put("parent",id).put("axes",multipliers).put("pauseAfterGeometry",false)
            .put("parent_mesh_sha256",FilesUtil.hash(new File(store.project(id),"checkpoint.mesh")));
    String paint = color.getText().toString().trim();
    request.remove("color");
    if (!paint.isEmpty()) {
     if (!paint.matches("#[0-9a-fA-F]{6}")) throw new IllegalArgumentException("Use #RRGGBB for colour");
     request.put("color",Color.parseColor(paint));
    }
    enqueue(request);
    dialog.dismiss();
   } catch (Exception failure) { error(failure); }
  }));
  dialog.show();
 }
 void kk(){boolean installed;try{getPackageManager().getPackageInfo("com.asterion.kk.test",0);installed=true;}catch(Exception e){installed=false;}label(installed?"Existing Asterion KK installation detected and preserved.":"Existing KK package was not visible to this app.");label("Card preservation is available. Source-accurate mesh extraction, zipmod resolution and Android runtime IPC are NOT implemented. Cards contain parameters and dependency references; they are not self-contained geometry.");button("Preserve original character card",()->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("image/png");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,3);});label("No screenshot reconstruction is substituted for source data. No Windows plugin is executed by this app.");}
 void performance(){ActivityManager.MemoryInfo m=new ActivityManager.MemoryInfo();getSystemService(ActivityManager.class).getMemoryInfo(m);PowerManager pm=getSystemService(PowerManager.class);label(String.format(Locale.ROOT,"Available RAM: %.2f GiB\nFree app storage: %.2f GiB\nThermal status: %d\nWorker: %s",m.availMem/1073741824.0,store.root.getUsableSpace()/1073741824.0,pm.getCurrentThermalStatus(),ProductionService.active?"running":"idle"));label("Geometry: Android CPU / ART\nPreview: OpenGL ES GPU rendering\nVulkan compute: not implemented\nQNN inference: not implemented\nAI depth: bundled MiDaS small (native CPU)");label(store.memory());label("One worker · pause on severe thermal state, low RAM or low storage · immutable source cache · stage checkpoints. Android foreground-service time limits still apply. No control of other applications’ memory.");if (Build.VERSION.SDK_INT >= 30) {
   label("Recent Foundry process exits");
   List<ApplicationExitInfo> exits = getSystemService(ActivityManager.class)
           .getHistoricalProcessExitReasons(getPackageName(),0,5);
   if (exits.isEmpty()) label("Android recorded no recent exits for Foundry");
   for (ApplicationExitInfo exit : exits) {
    String reason = switch (exit.getReason()) {
     case ApplicationExitInfo.REASON_CRASH -> "Java crash";
     case ApplicationExitInfo.REASON_CRASH_NATIVE -> "Native crash";
     case ApplicationExitInfo.REASON_ANR -> "Application not responding";
     case ApplicationExitInfo.REASON_LOW_MEMORY -> "Low memory";
     case ApplicationExitInfo.REASON_USER_REQUESTED -> "Stopped by user";
     default -> "Android reason " + exit.getReason();
    };
    label(new java.util.Date(exit.getTimestamp()) + " · " + reason);
   }
  }
  button("Refresh performance",()->show("Performance"));
 }
 @Override protected void onSaveInstanceState(Bundle state) {
  super.onSaveInstanceState(state);
  state.putString("source",source);
  state.putString("exportId",exportId);
  state.putString("bindingReference",bindingReference);
  state.putString("handoffProfile",handoffProfile);
  for (Map.Entry<String,String> entry : references.entrySet()) state.putString(entry.getKey(),entry.getValue());
 }

 @Override protected void onActivityResult(int code,int result,Intent data) {
  super.onActivityResult(code,result,data);
  if (result != RESULT_OK || data == null || data.getData() == null) return;
  Uri uri = data.getData();
  String exporting = exportId;
  new Thread(() -> {
   try {
    if (code == 21 || code == 22) {
     File original;
     try (InputStream input = getContentResolver().openInputStream(uri)) {
      original = new SourceFiles(store.root).preserve(input);
     }
     if (code == 21) {
      SourceFiles.decode(original,128);
      runOnUiThread(() -> {
       bindingReference = original.getAbsolutePath();
       pick(22,"*/*");
      });
     } else {
      String referencePath = bindingReference;
      if (referencePath == null) throw new IOException("Select the reference image again");
      new ReferenceRegistry(store.root).bind(new File(referencePath),original);
      runOnUiThread(() -> {
       bindingReference = null;
       Toast.makeText(this,"Reference registered to original GLB",Toast.LENGTH_LONG).show();
      });
     }
     return;
    }
    if (code == 20) {
     try (InputStream input = getContentResolver().openInputStream(uri)) {
      TripoModels.install(store.root,input);
     }
     runOnUiThread(() -> {
      if ("Create".equals(page)) {
       nextRecipe = HUMAN_REFERENCE.equals(kind.getSelectedItem()) ? HUMAN_REFERENCE : ProductionService.VOLUME;
       show("Create");
      }
      Toast.makeText(this, "Verified 3D models installed", Toast.LENGTH_LONG).show();
     });
     return;
    }
    if (code == 2) {
     try (OutputStream output = getContentResolver().openOutputStream(uri)) {
      if (output == null) throw new IOException("Cannot open the selected destination");
      ProjectPackage.write(store, exporting, output);
     }
     runOnUiThread(() -> Toast.makeText(this,"Editable package exported",Toast.LENGTH_LONG).show());
     return;
    }
    if (code != 1 && code != 3 && code != 23 && (code < 11 || code > 13)) return;
    File original;
    try (InputStream input = getContentResolver().openInputStream(uri)) {
     original = new SourceFiles(store.root).preserve(input);
    }
    runOnUiThread(() -> {
     if (code == 23) {
      handoffProfile = original.getAbsolutePath();
      Button control = (Button) body.findViewWithTag("contract");
      if (control != null) control.setText(handoffLabel());
      return;
     }
     if (code == 3) {
      new AlertDialog.Builder(this).setTitle("Original card preserved")
              .setMessage("SHA-256: " + original.getName() + "\nGeometry extraction remains unavailable.")
              .setPositiveButton("OK",null).show();
      return;
     }
     String role = code == 1 ? "source" : new String[]{"front","right","back"}[code-11];
     if (code == 1) source = original.getAbsolutePath();
     else references.put(role,original.getAbsolutePath());
     Button control = (Button) body.findViewWithTag(role);
     if (control != null) control.setText(code == 1 ? originalLabel() : viewLabel(role));
    });
   } catch (Exception failure) {
    runOnUiThread(() -> error(failure));
   }
  }).start();
 }
 void error(Exception e){new AlertDialog.Builder(this).setTitle("Operation could not finish").setMessage(e.getMessage()==null?e.toString():e.getMessage()).setPositiveButton("OK",null).show();}
 @Override protected void onResume(){super.onResume();if(preview!=null)preview.onResume();}
 @Override protected void onPause(){if(preview!=null)preview.onPause();if(motionPlaybackButton!=null)motionPlaybackButton.setText("Play animation");super.onPause();}
}
